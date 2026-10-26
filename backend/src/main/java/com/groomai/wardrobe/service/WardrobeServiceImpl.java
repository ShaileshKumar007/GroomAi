package com.groomai.wardrobe.service;

import com.groomai.common.exception.GroomAiException;
import com.groomai.storage.ObjectStorageService;
import com.groomai.storage.StorageKeyValidator;
import com.groomai.storage.StorageMode;
import com.groomai.user.entity.User;
import com.groomai.user.entity.UserProfile;
import com.groomai.user.exception.ConsentRequiredException;
import com.groomai.user.repository.UserProfileRepository;
import com.groomai.user.repository.UserRepository;
import com.groomai.wardrobe.dto.CreateWardrobeItemRequestDto;
import com.groomai.wardrobe.dto.UploadIntentRequestDto;
import com.groomai.wardrobe.dto.UploadIntentResponseDto;
import com.groomai.wardrobe.dto.WardrobeItemResponseDto;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import com.groomai.wardrobe.exception.DuplicateWardrobeItemException;
import com.groomai.wardrobe.exception.UnsupportedImageFormatException;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class WardrobeServiceImpl implements WardrobeService {

    private static final Logger log = LoggerFactory.getLogger(WardrobeServiceImpl.class);

    private static final Duration INTENT_TTL = Duration.ofMinutes(15);
    private static final String ACCEPTED_CONTENT_TYPE = "image/jpeg";

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final ObjectStorageService objectStorageService;
    private final WardrobeImageVerificationService wardrobeImageVerificationService;
    private final com.groomai.storage.UploadTicketService uploadTicketService;

    public WardrobeServiceImpl(
        UserProfileRepository userProfileRepository,
        UserRepository userRepository,
        WardrobeItemRepository wardrobeItemRepository,
        ObjectStorageService objectStorageService,
        WardrobeImageVerificationService wardrobeImageVerificationService,
        com.groomai.storage.UploadTicketService uploadTicketService
    ) {
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
        this.wardrobeItemRepository = wardrobeItemRepository;
        this.objectStorageService = objectStorageService;
        this.wardrobeImageVerificationService = wardrobeImageVerificationService;
        this.uploadTicketService = uploadTicketService;
    }

    @Override
    @Transactional(readOnly = true)
    public UploadIntentResponseDto createUploadIntent(UUID userId, UploadIntentRequestDto request) {
        if (!ACCEPTED_CONTENT_TYPE.equalsIgnoreCase(request.contentType())) {
            throw new UnsupportedImageFormatException(
                "Unsupported image format: " + request.contentType() + ". Only image/jpeg is accepted."
            );
        }

        UserProfile userProfile = userProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ConsentRequiredException(
                "Wardrobe storage consent is required before requesting an upload intent."
            ));

        if (!userProfile.isWardrobeStorageConsentGranted()) {
            throw new ConsentRequiredException(
                "Wardrobe storage consent is required before requesting an upload intent."
            );
        }

        String storageKey = StorageKeyValidator.generateWardrobeKey(userId);
        String uploadTicket = uploadTicketService.generateUploadTicket(userId, storageKey, INTENT_TTL);
        String baseUploadUrl = objectStorageService.generateUploadUrl(storageKey, INTENT_TTL);

        boolean isLocal = objectStorageService.getStorageMode() == StorageMode.LOCAL_STREAM;
        String uploadMode = isLocal ? "LOCAL_STREAM" : "S3_PRESIGNED";
        String uploadUrl = baseUploadUrl;
        String httpMethod = isLocal ? "POST" : "PUT";
        Map<String, String> requiredHeaders = isLocal
            ? Map.of("Content-Type", "multipart/form-data", "X-Upload-Ticket", uploadTicket)
            : Map.of("Content-Type", "image/jpeg");

        Instant expiresAt = Instant.now().plus(INTENT_TTL);

        log.info("Generated upload intent for user [{}] with key [{}] mode [{}]", userId, storageKey, uploadMode);

        return new UploadIntentResponseDto(
            uploadMode,
            uploadUrl,
            storageKey,
            expiresAt,
            httpMethod,
            requiredHeaders,
            uploadTicket
        );
    }

    @Override
    @Transactional
    public WardrobeItemResponseDto createWardrobeItem(UUID userId, CreateWardrobeItemRequestDto request) {
        UUID ownerUserId = StorageKeyValidator.extractUserId(request.storageKey());
        if (!ownerUserId.equals(userId)) {
            log.warn("Cross-tenant storage key reference attempted: authenticated [{}] vs key owner [{}]", userId, ownerUserId);
            throw new AccessDeniedException("Access to storage key denied: cross-tenant access prohibited.");
        }

        if (wardrobeItemRepository.existsByStorageKey(request.storageKey())) {
            throw new DuplicateWardrobeItemException(
                "A wardrobe item with storage key '" + request.storageKey() + "' has already been cataloged."
            );
        }

        // Verify storage object existence, magic bytes, dimensions (>= 200x200), and size (<= 10MB)
        wardrobeImageVerificationService.verify(request.storageKey());

        // Validate upload intent authorization ticket
        String ticket = (request.uploadTicket() != null && !request.uploadTicket().isBlank())
            ? request.uploadTicket().trim()
            : objectStorageService.getMetadata(request.storageKey()).uploadTicket();

        if (ticket == null || ticket.isBlank()) {
            throw new com.groomai.storage.exception.InvalidUploadTicketException(
                "Missing required upload-intent authorization ticket for storage key: " + request.storageKey()
            );
        }

        uploadTicketService.validateUploadTicket(ticket, userId, request.storageKey());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GroomAiException("User not found: " + userId, HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        WardrobeItem item = WardrobeItem.builder()
            .user(user)
            .name(request.name().trim())
            .category(request.category())
            .subcategory(request.subcategory() != null ? request.subcategory().trim() : null)
            .primaryColor(request.primaryColor())
            .secondaryColor(request.secondaryColor())
            .pattern(request.pattern() != null ? request.pattern() : Pattern.SOLID)
            .formalityLevel(request.formalityLevel() != null ? request.formalityLevel() : FormalityLevel.CASUAL)
            .season(request.season() != null ? request.season() : Season.ALL_SEASON)
            .storageKey(request.storageKey())
            .status(ItemStatus.ACTIVE)
            .build();

        WardrobeItem saved = wardrobeItemRepository.save(item);
        log.info("Cataloged wardrobe item [{}] for user [{}]", saved.getId(), userId);

        String downloadUrl = objectStorageService.generateDownloadUrl(saved.getStorageKey(), Duration.ofMinutes(15));
        return WardrobeItemResponseDto.fromEntity(saved, downloadUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public com.groomai.common.dto.PagedResponse<WardrobeItemResponseDto> listWardrobeItems(
        UUID userId,
        com.groomai.wardrobe.enums.Category category,
        ItemStatus status,
        org.springframework.data.domain.Pageable pageable
    ) {
        ItemStatus targetStatus = (status != null) ? status : ItemStatus.ACTIVE;
        org.springframework.data.domain.Page<WardrobeItem> page;

        if (category != null) {
            page = wardrobeItemRepository.findByUserIdAndCategoryAndStatus(userId, category, targetStatus, pageable);
        } else {
            page = wardrobeItemRepository.findByUserIdAndStatus(userId, targetStatus, pageable);
        }

        org.springframework.data.domain.Page<WardrobeItemResponseDto> dtoPage = page.map(item -> {
            String downloadUrl = objectStorageService.generateDownloadUrl(item.getStorageKey(), Duration.ofMinutes(15));
            return WardrobeItemResponseDto.fromEntity(item, downloadUrl);
        });

        return com.groomai.common.dto.PagedResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public WardrobeItemResponseDto getWardrobeItem(UUID userId, UUID itemId) {
        WardrobeItem item = wardrobeItemRepository.findByIdAndUserId(itemId, userId)
            .orElseThrow(() -> new GroomAiException(
                "Wardrobe item not found: " + itemId,
                HttpStatus.NOT_FOUND,
                "ITEM_NOT_FOUND"
            ));

        String downloadUrl = objectStorageService.generateDownloadUrl(item.getStorageKey(), Duration.ofMinutes(15));
        return WardrobeItemResponseDto.fromEntity(item, downloadUrl);
    }
}
