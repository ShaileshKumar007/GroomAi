package com.groomai.outfit.service;

import com.groomai.common.exception.GroomAiException;
import com.groomai.outfit.dto.LogOutfitWearRequestDto;
import com.groomai.outfit.dto.LogOutfitWearResponseDto;
import com.groomai.outfit.dto.OutfitItemDto;
import com.groomai.outfit.dto.OutfitWearHistoryDto;
import com.groomai.outfit.dto.SaveOutfitRequestDto;
import com.groomai.outfit.dto.SavedOutfitResponseDto;
import com.groomai.outfit.entity.Outfit;
import com.groomai.outfit.entity.OutfitItem;
import com.groomai.outfit.entity.OutfitWearLog;
import com.groomai.outfit.repository.OutfitItemRepository;
import com.groomai.outfit.repository.OutfitRepository;
import com.groomai.outfit.repository.OutfitWearLogRepository;
import com.groomai.storage.ObjectStorageService;
import com.groomai.user.entity.User;
import com.groomai.user.repository.UserRepository;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of OutfitPersistenceService.
 * Enforces transactional safety, tenant isolation, and strict composition rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutfitPersistenceServiceImpl implements OutfitPersistenceService {

    private final OutfitRepository outfitRepository;
    private final OutfitItemRepository outfitItemRepository;
    private final OutfitWearLogRepository outfitWearLogRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;
    private final ObjectStorageService objectStorageService;

    @Override
    @Transactional
    public SavedOutfitResponseDto saveOutfit(UUID userId, SaveOutfitRequestDto request) {
        if (request.itemIds() == null || request.itemIds().size() < 3 || request.itemIds().size() > 5) {
            throw new GroomAiException(
                "Outfit must contain between 3 and 5 items",
                HttpStatus.BAD_REQUEST,
                "INVALID_ITEM_COUNT"
            );
        }

        // Prevent duplicate item references in a single outfit
        Set<UUID> uniqueIds = new HashSet<>(request.itemIds());
        if (uniqueIds.size() != request.itemIds().size()) {
            throw new GroomAiException(
                "An outfit cannot contain duplicate wardrobe items",
                HttpStatus.BAD_REQUEST,
                "DUPLICATE_ITEMS_IN_OUTFIT"
            );
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GroomAiException(
                "User not found: " + userId,
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND"
            ));

        // Fetch candidate items and enforce tenant isolation & active status
        List<WardrobeItem> items = wardrobeItemRepository.findAllById(request.itemIds());

        if (items.size() != request.itemIds().size()) {
            throw new GroomAiException(
                "One or more wardrobe items could not be found",
                HttpStatus.BAD_REQUEST,
                "INVALID_WARDROBE_ITEMS"
            );
        }

        for (WardrobeItem item : items) {
            if (item.getUser() == null || !userId.equals(item.getUser().getId())) {
                log.warn("Security violation: user [{}] attempted to save item [{}] belonging to another user",
                    userId, item.getId());
                throw new GroomAiException(
                    "You do not own all submitted wardrobe items",
                    HttpStatus.FORBIDDEN,
                    "UNAUTHORIZED_ITEM_ACCESS"
                );
            }
            if (item.getStatus() != ItemStatus.ACTIVE) {
                throw new GroomAiException(
                    "Cannot add inactive or archived item [" + item.getName() + "] to an outfit",
                    HttpStatus.BAD_REQUEST,
                    "INACTIVE_ITEM"
                );
            }
        }

        // Validate composition: exactly 1 TOP, 1 BOTTOM, 1 FOOTWEAR; at most 1 OUTERWEAR, 1 ACCESSORY
        long topCount = items.stream().filter(i -> i.getCategory() == Category.TOP).count();
        long bottomCount = items.stream().filter(i -> i.getCategory() == Category.BOTTOM).count();
        long footwearCount = items.stream().filter(i -> i.getCategory() == Category.FOOTWEAR).count();
        long outerwearCount = items.stream().filter(i -> i.getCategory() == Category.OUTERWEAR).count();
        long accessoryCount = items.stream().filter(i -> i.getCategory() == Category.ACCESSORY).count();

        if (topCount != 1 || bottomCount != 1 || footwearCount != 1 || outerwearCount > 1 || accessoryCount > 1) {
            throw new GroomAiException(
                "Outfit must contain exactly one Top, one Bottom, and one Footwear, with at most one Outerwear and one Accessory",
                HttpStatus.BAD_REQUEST,
                "INVALID_OUTFIT_COMPOSITION"
            );
        }

        int score = (request.suitabilityScore() != null)
            ? Math.clamp(request.suitabilityScore(), 0, 100)
            : 80;

        String rationale = (request.stylingRationale() != null && !request.stylingRationale().isBlank())
            ? request.stylingRationale()
            : "Saved outfit combination for " + request.occasion();

        Outfit outfit = Outfit.builder()
            .user(user)
            .occasion(request.occasion())
            .temperatureBracket(request.temperatureBracket())
            .weatherCondition(request.weatherCondition())
            .stylingRationale(rationale)
            .suitabilityScore(score)
            .build();

        for (WardrobeItem item : items) {
            OutfitItem outfitItem = OutfitItem.builder()
                .outfit(outfit)
                .wardrobeItem(item)
                .itemRole(item.getCategory().name())
                .build();
            outfit.addItem(outfitItem);
        }

        outfit = outfitRepository.save(outfit);
        log.info("Saved outfit [{}] with [{}] items for user [{}]", outfit.getId(), items.size(), userId);

        return mapToSavedOutfitDto(outfit);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SavedOutfitResponseDto> getSavedOutfits(UUID userId, Pageable pageable) {
        return outfitRepository.findByUserId(userId, pageable)
            .map(this::mapToSavedOutfitDto);
    }

    @Override
    @Transactional(readOnly = true)
    public SavedOutfitResponseDto getSavedOutfitById(UUID userId, UUID outfitId) {
        Outfit outfit = outfitRepository.findByIdAndUserId(outfitId, userId)
            .orElseThrow(() -> new GroomAiException(
                "Outfit not found: " + outfitId,
                HttpStatus.NOT_FOUND,
                "OUTFIT_NOT_FOUND"
            ));
        return mapToSavedOutfitDto(outfit);
    }

    @Override
    @Transactional
    public LogOutfitWearResponseDto logOutfitWear(UUID userId, UUID outfitId, LogOutfitWearRequestDto request) {
        Outfit outfit = outfitRepository.findByIdAndUserId(outfitId, userId)
            .orElseThrow(() -> new GroomAiException(
                "Outfit not found: " + outfitId,
                HttpStatus.NOT_FOUND,
                "OUTFIT_NOT_FOUND"
            ));

        LocalDate wornDate = (request != null && request.wornDate() != null)
            ? request.wornDate()
            : LocalDate.now();

        if (wornDate.isAfter(LocalDate.now().plusDays(1))) {
            throw new GroomAiException(
                "Cannot log outfit wear for future dates",
                HttpStatus.BAD_REQUEST,
                "INVALID_WEAR_DATE"
            );
        }

        // Idempotency: Check if the exact wear event was already recorded for this date
        if (outfitWearLogRepository.existsByUserIdAndOutfitIdAndWornDate(userId, outfitId, wornDate)) {
            log.info("Outfit [{}] was already recorded as worn on [{}] for user [{}]; returning idempotent confirmation",
                outfitId, wornDate, userId);
            return new LogOutfitWearResponseDto(
                UUID.nameUUIDFromBytes((outfitId.toString() + wornDate.toString()).getBytes()),
                outfitId,
                wornDate,
                "Outfit wear was already recorded for this date. Repetition penalty is active for recommendations."
            );
        }

        OutfitWearLog wearLog = OutfitWearLog.builder()
            .user(outfit.getUser())
            .outfit(outfit)
            .wornDate(wornDate)
            .notes(request != null ? request.notes() : null)
            .build();

        wearLog = outfitWearLogRepository.save(wearLog);
        log.info("Recorded wear log [{}] for outfit [{}] on date [{}]", wearLog.getId(), outfitId, wornDate);

        return new LogOutfitWearResponseDto(
            wearLog.getId(),
            outfitId,
            wornDate,
            "Outfit wear successfully logged. Repetition penalty applied for upcoming recommendations."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OutfitWearHistoryDto> getWearHistory(UUID userId, Pageable pageable) {
        return outfitWearLogRepository.findByUserId(userId, pageable)
            .map(this::mapToWearHistoryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> getRecentItemWearDays(UUID userId) {
        List<Object[]> rows = outfitWearLogRepository.findLatestWornDatesByUserId(userId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<UUID, Integer> wearDaysMap = new HashMap<>();
        LocalDate today = LocalDate.now();

        for (Object[] row : rows) {
            UUID itemId = (UUID) row[0];
            LocalDate latestWornDate = (LocalDate) row[1];
            if (itemId != null && latestWornDate != null) {
                int days = (int) Math.max(0, ChronoUnit.DAYS.between(latestWornDate, today));
                wearDaysMap.put(itemId, days);
            }
        }

        return wearDaysMap;
    }

    private SavedOutfitResponseDto mapToSavedOutfitDto(Outfit outfit) {
        List<OutfitItemDto> itemDtos = new ArrayList<>();
        if (outfit.getItems() != null) {
            for (OutfitItem oi : outfit.getItems()) {
                itemDtos.add(mapToOutfitItemDto(oi));
            }
        }

        return new SavedOutfitResponseDto(
            outfit.getId(),
            outfit.getOccasion(),
            outfit.getTemperatureBracket(),
            outfit.getWeatherCondition(),
            outfit.getStylingRationale(),
            outfit.getSuitabilityScore(),
            outfit.getCreatedAt(),
            itemDtos
        );
    }

    private OutfitWearHistoryDto mapToWearHistoryDto(OutfitWearLog wearLog) {
        Outfit outfit = wearLog.getOutfit();
        List<OutfitItemDto> items = new ArrayList<>();

        if (outfit != null && outfit.getItems() != null) {
            for (OutfitItem oi : outfit.getItems()) {
                items.add(mapToOutfitItemDto(oi));
            }
        }

        return new OutfitWearHistoryDto(
            wearLog.getId(),
            outfit != null ? outfit.getId() : null,
            wearLog.getWornDate(),
            wearLog.getNotes(),
            wearLog.getCreatedAt(),
            outfit != null ? outfit.getOccasion() : null,
            outfit != null ? outfit.getSuitabilityScore() : null,
            outfit != null ? outfit.getStylingRationale() : "Historical wear record",
            items
        );
    }

    private OutfitItemDto mapToOutfitItemDto(OutfitItem oi) {
        WardrobeItem wi = oi.getWardrobeItem();
        if (wi != null) {
            String downloadUrl = null;
            if (wi.getStorageKey() != null) {
                downloadUrl = objectStorageService.generateDownloadUrl(wi.getStorageKey(), Duration.ofMinutes(15));
            }
            return new OutfitItemDto(
                oi.getItemRole(),
                wi.getId(),
                wi.getName(),
                wi.getCategory(),
                wi.getPrimaryColor(),
                wi.getSecondaryColor(),
                wi.getPattern(),
                wi.getFormalityLevel(),
                wi.getSeason(),
                downloadUrl
            );
        } else {
            // Decoupled historical snapshot: wardrobe item was deleted
            return new OutfitItemDto(
                oi.getItemRole(),
                null,
                "[Removed Item]",
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
    }
}
