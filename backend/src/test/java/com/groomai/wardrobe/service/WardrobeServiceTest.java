package com.groomai.wardrobe.service;

import com.groomai.storage.ObjectStorageService;
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
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import com.groomai.wardrobe.exception.DuplicateWardrobeItemException;
import com.groomai.wardrobe.exception.UnsupportedImageFormatException;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WardrobeServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WardrobeItemRepository wardrobeItemRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private WardrobeImageVerificationService wardrobeImageVerificationService;

    @Mock
    private com.groomai.storage.UploadTicketService uploadTicketService;

    @Mock
    private com.groomai.storage.StorageDeletionOutboxService storageDeletionOutboxService;

    private WardrobeService wardrobeService;

    private UUID userId;
    private User user;
    private UserProfile userProfile;

    @BeforeEach
    void setUp() {
        wardrobeService = new WardrobeServiceImpl(
            userProfileRepository,
            userRepository,
            wardrobeItemRepository,
            objectStorageService,
            wardrobeImageVerificationService,
            uploadTicketService,
            storageDeletionOutboxService
        );

        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("user@example.com").role("ROLE_USER").build();
        userProfile = UserProfile.builder()
            .user(user)
            .wardrobeStorageConsentGranted(true)
            .build();
    }

    @Test
    @DisplayName("createUploadIntent: rejects non-JPEG image formats (PNG, HEIC)")
    void createUploadIntent_unsupportedFormat_shouldThrow() {
        UploadIntentRequestDto pngRequest = new UploadIntentRequestDto("image/png", 1024L);

        assertThatThrownBy(() -> wardrobeService.createUploadIntent(userId, pngRequest))
            .isInstanceOf(UnsupportedImageFormatException.class)
            .hasMessageContaining("Only image/jpeg is accepted");
    }

    @Test
    @DisplayName("createUploadIntent: throws ConsentRequiredException when consent is not granted")
    void createUploadIntent_withoutConsent_shouldThrow() {
        userProfile.setWardrobeStorageConsentGranted(false);
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(userProfile));

        UploadIntentRequestDto request = new UploadIntentRequestDto("image/jpeg", 2048500L);

        assertThatThrownBy(() -> wardrobeService.createUploadIntent(userId, request))
            .isInstanceOf(ConsentRequiredException.class)
            .hasMessageContaining("Wardrobe storage consent is required");
    }

    @Test
    @DisplayName("createUploadIntent: succeeds with local stream mode and required headers")
    void createUploadIntent_success() {
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(userProfile));
        when(objectStorageService.getStorageMode()).thenReturn(StorageMode.LOCAL_STREAM);
        when(objectStorageService.generateUploadUrl(any(), any())).thenAnswer(inv -> "http://localhost:8080/api/v1/storage/upload/" + inv.getArgument(0));
        when(uploadTicketService.generateUploadTicket(eq(userId), any(), any())).thenReturn("mock-upload-ticket");

        UploadIntentRequestDto request = new UploadIntentRequestDto("image/jpeg", 2048500L);
        UploadIntentResponseDto response = wardrobeService.createUploadIntent(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.uploadMode()).isEqualTo("LOCAL_STREAM");
        assertThat(response.storageKey()).startsWith("wardrobe/" + userId + "/");
        assertThat(response.httpMethod()).isEqualTo("POST");
        assertThat(response.uploadTicket()).isEqualTo("mock-upload-ticket");
        assertThat(response.uploadUrl()).doesNotContain("?ticket=");
        assertThat(response.uploadUrl()).isEqualTo("http://localhost:8080/api/v1/storage/upload/" + response.storageKey());
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "multipart/form-data");
        assertThat(response.requiredHeaders()).containsEntry("X-Upload-Ticket", "mock-upload-ticket");
    }

    @Test
    @DisplayName("createWardrobeItem: rejects cross-tenant storage key reference")
    void createWardrobeItem_crossTenantKey_shouldThrow() {
        UUID otherUserId = UUID.randomUUID();
        String crossTenantKey = "wardrobe/" + otherUserId + "/item-123.jpg";

        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            Color.WHITE,
            Pattern.SOLID,
            FormalityLevel.SMART_CASUAL,
            Season.ALL_SEASON,
            crossTenantKey
        );

        assertThatThrownBy(() -> wardrobeService.createWardrobeItem(userId, request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("cross-tenant access prohibited");
    }

    @Test
    @DisplayName("createWardrobeItem: rejects duplicate storage key")
    void createWardrobeItem_duplicateKey_shouldThrow() {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        when(wardrobeItemRepository.existsByStorageKey(key)).thenReturn(true);

        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            null,
            Color.NAVY,
            null,
            null,
            null,
            null,
            key
        );

        assertThatThrownBy(() -> wardrobeService.createWardrobeItem(userId, request))
            .isInstanceOf(DuplicateWardrobeItemException.class)
            .hasMessageContaining("already been cataloged");
    }

    @Test
    @DisplayName("createWardrobeItem: successfully catalogs item with defaults and returns downloadUrl")
    void createWardrobeItem_success() {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        when(wardrobeItemRepository.existsByStorageKey(key)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(objectStorageService.getMetadata(key)).thenReturn(
            new com.groomai.storage.StorageObjectMetadata(key, 1024L, "image/jpeg", java.time.Instant.now(), "valid-ticket")
        );

        UUID savedItemId = UUID.randomUUID();
        when(wardrobeItemRepository.save(any(WardrobeItem.class))).thenAnswer(inv -> {
            WardrobeItem item = inv.getArgument(0);
            item.setId(savedItemId);
            return item;
        });
        when(objectStorageService.generateDownloadUrl(eq(key), any(Duration.class)))
            .thenReturn("http://localhost:8080/api/v1/storage/files/" + key);

        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Classic White Tee",
            Category.TOP,
            "T_SHIRT",
            Color.WHITE,
            null,
            null, // should default to SOLID
            null, // should default to CASUAL
            null, // should default to ALL_SEASON
            key
        );

        WardrobeItemResponseDto response = wardrobeService.createWardrobeItem(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(savedItemId);
        assertThat(response.name()).isEqualTo("Classic White Tee");
        assertThat(response.category()).isEqualTo(Category.TOP);
        assertThat(response.primaryColor()).isEqualTo(Color.WHITE);
        assertThat(response.pattern()).isEqualTo(Pattern.SOLID);
        assertThat(response.formalityLevel()).isEqualTo(FormalityLevel.CASUAL);
        assertThat(response.season()).isEqualTo(Season.ALL_SEASON);
        assertThat(response.downloadUrl()).isEqualTo("http://localhost:8080/api/v1/storage/files/" + key);
        assertThat(response.status()).isEqualTo(ItemStatus.ACTIVE);

        verify(wardrobeImageVerificationService).verify(key);
        verify(uploadTicketService).validateUploadTicket("valid-ticket", userId, key);

        ArgumentCaptor<WardrobeItem> captor = ArgumentCaptor.forClass(WardrobeItem.class);
        verify(wardrobeItemRepository).save(captor.capture());
        WardrobeItem savedEntity = captor.getValue();
        assertThat(savedEntity.getName()).isEqualTo("Classic White Tee");
        assertThat(savedEntity.getPattern()).isEqualTo(Pattern.SOLID);
        assertThat(savedEntity.getFormalityLevel()).isEqualTo(FormalityLevel.CASUAL);
        assertThat(savedEntity.getSeason()).isEqualTo(Season.ALL_SEASON);
    }

    @Test
    @DisplayName("createWardrobeItem: rejects storage key with invalid format or non-UUID user segment")
    void createWardrobeItem_invalidKeyStructure_shouldThrow() {
        String invalidKey = "wardrobe/non-uuid-segment/item-123.jpg";

        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            null,
            null,
            null,
            null,
            invalidKey
        );

        assertThatThrownBy(() -> wardrobeService.createWardrobeItem(userId, request))
            .isInstanceOf(com.groomai.storage.exception.InvalidStorageKeyException.class)
            .hasMessageContaining("not a valid UUID");
    }

    @Test
    @DisplayName("createWardrobeItem: throws InvalidUploadTicketException when ticket is missing from both request and metadata")
    void createWardrobeItem_missingTicket_shouldThrow() {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        when(wardrobeItemRepository.existsByStorageKey(key)).thenReturn(false);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new com.groomai.storage.StorageObjectMetadata(key, 1024L, "image/jpeg", java.time.Instant.now(), null)
        );

        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            null,
            null,
            null,
            null,
            key,
            null
        );

        assertThatThrownBy(() -> wardrobeService.createWardrobeItem(userId, request))
            .isInstanceOf(com.groomai.storage.exception.InvalidUploadTicketException.class)
            .hasMessageContaining("Missing required upload-intent authorization ticket");
    }

    @Test
    @DisplayName("createWardrobeItem: propagates ExpiredUploadTicketException when ticket validation fails with expired")
    void createWardrobeItem_expiredTicket_shouldThrow() {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        when(wardrobeItemRepository.existsByStorageKey(key)).thenReturn(false);

        org.mockito.Mockito.doThrow(new com.groomai.storage.exception.ExpiredUploadTicketException("Ticket expired"))
            .when(uploadTicketService).validateUploadTicket("expired-ticket", userId, key);

        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            null,
            null,
            null,
            null,
            key,
            "expired-ticket"
        );

        assertThatThrownBy(() -> wardrobeService.createWardrobeItem(userId, request))
            .isInstanceOf(com.groomai.storage.exception.ExpiredUploadTicketException.class)
            .hasMessageContaining("Ticket expired");
    }

    @Test
    @DisplayName("createWardrobeItem: propagates InvalidUploadTicketException when ticket validation fails")
    void createWardrobeItem_invalidTicket_shouldThrow() {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        when(wardrobeItemRepository.existsByStorageKey(key)).thenReturn(false);

        org.mockito.Mockito.doThrow(new com.groomai.storage.exception.InvalidUploadTicketException("Tampered ticket"))
            .when(uploadTicketService).validateUploadTicket("invalid-ticket", userId, key);

        CreateWardrobeItemRequestDto request = new CreateWardrobeItemRequestDto(
            "Oxford Shirt",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            null,
            null,
            null,
            null,
            key,
            "invalid-ticket"
        );

        assertThatThrownBy(() -> wardrobeService.createWardrobeItem(userId, request))
            .isInstanceOf(com.groomai.storage.exception.InvalidUploadTicketException.class)
            .hasMessageContaining("Tampered ticket");
    }

    @Test
    @DisplayName("listWardrobeItems: returns paged items with ephemeral download URLs")
    void listWardrobeItems_success() {
        WardrobeItem item = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(user)
            .name("Oxford Shirt")
            .category(Category.TOP)
            .primaryColor(Color.NAVY)
            .storageKey("wardrobe/" + userId + "/item-1.jpg")
            .status(ItemStatus.ACTIVE)
            .build();

        org.springframework.data.domain.Page<WardrobeItem> page =
            new org.springframework.data.domain.PageImpl<>(java.util.List.of(item));

        when(wardrobeItemRepository.findByUserIdAndStatus(eq(userId), eq(ItemStatus.ACTIVE), any()))
            .thenReturn(page);
        when(objectStorageService.generateDownloadUrl(eq(item.getStorageKey()), any(Duration.class)))
            .thenReturn("http://download.url/item-1.jpg");

        var response = wardrobeService.listWardrobeItems(
            userId, null, ItemStatus.ACTIVE, org.springframework.data.domain.PageRequest.of(0, 20)
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).name()).isEqualTo("Oxford Shirt");
        assertThat(response.content().get(0).downloadUrl()).isEqualTo("http://download.url/item-1.jpg");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("listWardrobeItems: filters by category when specified")
    void listWardrobeItems_withCategory_success() {
        WardrobeItem item = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(user)
            .name("Blue Jeans")
            .category(Category.BOTTOM)
            .primaryColor(Color.BLUE)
            .storageKey("wardrobe/" + userId + "/item-2.jpg")
            .status(ItemStatus.ACTIVE)
            .build();

        org.springframework.data.domain.Page<WardrobeItem> page =
            new org.springframework.data.domain.PageImpl<>(java.util.List.of(item));

        when(wardrobeItemRepository.findByUserIdAndCategoryAndStatus(eq(userId), eq(Category.BOTTOM), eq(ItemStatus.ACTIVE), any()))
            .thenReturn(page);
        when(objectStorageService.generateDownloadUrl(eq(item.getStorageKey()), any(Duration.class)))
            .thenReturn("http://download.url/item-2.jpg");

        var response = wardrobeService.listWardrobeItems(
            userId, Category.BOTTOM, ItemStatus.ACTIVE, org.springframework.data.domain.PageRequest.of(0, 20)
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).category()).isEqualTo(Category.BOTTOM);
    }

    @Test
    @DisplayName("getWardrobeItem: returns item with download URL for owner")
    void getWardrobeItem_success() {
        UUID itemId = UUID.randomUUID();
        WardrobeItem item = WardrobeItem.builder()
            .id(itemId)
            .user(user)
            .name("Oxford Shirt")
            .category(Category.TOP)
            .primaryColor(Color.NAVY)
            .storageKey("wardrobe/" + userId + "/item-1.jpg")
            .status(ItemStatus.ACTIVE)
            .build();

        when(wardrobeItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(objectStorageService.generateDownloadUrl(eq(item.getStorageKey()), any(Duration.class)))
            .thenReturn("http://download.url/item-1.jpg");

        WardrobeItemResponseDto response = wardrobeService.getWardrobeItem(userId, itemId);

        assertThat(response.id()).isEqualTo(itemId);
        assertThat(response.name()).isEqualTo("Oxford Shirt");
        assertThat(response.downloadUrl()).isEqualTo("http://download.url/item-1.jpg");
    }

    @Test
    @DisplayName("getWardrobeItem: throws GroomAiException with ITEM_NOT_FOUND when not found")
    void getWardrobeItem_notFound_throwsException() {
        UUID itemId = UUID.randomUUID();
        when(wardrobeItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wardrobeService.getWardrobeItem(userId, itemId))
            .isInstanceOf(com.groomai.common.exception.GroomAiException.class)
            .hasMessageContaining("Wardrobe item not found");
    }

    @Test
    @DisplayName("updateWardrobeItem: updates mutable metadata and returns updated DTO")
    void updateWardrobeItem_success() {
        UUID itemId = UUID.randomUUID();
        WardrobeItem item = WardrobeItem.builder()
            .id(itemId)
            .user(user)
            .name("Old Shirt")
            .category(Category.TOP)
            .primaryColor(Color.BLUE)
            .storageKey("wardrobe/" + userId + "/item-1.jpg")
            .status(ItemStatus.ACTIVE)
            .build();

        when(wardrobeItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));
        when(wardrobeItemRepository.save(any(WardrobeItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectStorageService.generateDownloadUrl(eq(item.getStorageKey()), any(Duration.class)))
            .thenReturn("http://download.url/item-1.jpg");

        var updateRequest = new com.groomai.wardrobe.dto.UpdateWardrobeItemRequestDto(
            "New Linen Shirt",
            Category.TOP,
            "LINEN",
            Color.WHITE,
            Color.BLUE,
            Pattern.STRIPED,
            FormalityLevel.SMART_CASUAL,
            Season.SUMMER
        );

        var response = wardrobeService.updateWardrobeItem(userId, itemId, updateRequest);

        assertThat(response.id()).isEqualTo(itemId);
        assertThat(response.name()).isEqualTo("New Linen Shirt");
        assertThat(response.primaryColor()).isEqualTo(Color.WHITE);
        assertThat(response.secondaryColor()).isEqualTo(Color.BLUE);
        assertThat(response.subcategory()).isEqualTo("LINEN");
        assertThat(response.pattern()).isEqualTo(Pattern.STRIPED);
        assertThat(response.formalityLevel()).isEqualTo(FormalityLevel.SMART_CASUAL);
        assertThat(response.season()).isEqualTo(Season.SUMMER);
        verify(wardrobeItemRepository).save(item);
    }

    @Test
    @DisplayName("updateWardrobeItem: throws GroomAiException with ITEM_NOT_FOUND when not found or wrong owner")
    void updateWardrobeItem_notFound_throwsException() {
        UUID itemId = UUID.randomUUID();
        when(wardrobeItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        var updateRequest = new com.groomai.wardrobe.dto.UpdateWardrobeItemRequestDto(
            "New Shirt",
            Category.TOP,
            null,
            Color.WHITE,
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> wardrobeService.updateWardrobeItem(userId, itemId, updateRequest))
            .isInstanceOf(com.groomai.common.exception.GroomAiException.class)
            .hasMessageContaining("Wardrobe item not found");
    }

    @Test
    @DisplayName("deleteWardrobeItem: enqueues storage key, deletes entity, and dispatches immediate eradication")
    void deleteWardrobeItem_success() {
        UUID itemId = UUID.randomUUID();
        String storageKey = "wardrobe/" + userId + "/item-1.jpg";
        WardrobeItem item = WardrobeItem.builder()
            .id(itemId)
            .user(user)
            .name("Old Shirt")
            .category(Category.TOP)
            .primaryColor(Color.BLUE)
            .storageKey(storageKey)
            .status(ItemStatus.ACTIVE)
            .build();

        when(wardrobeItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));

        wardrobeService.deleteWardrobeItem(userId, itemId);

        verify(storageDeletionOutboxService).enqueueDeletion(storageKey);
        verify(wardrobeItemRepository).delete(item);
        verify(storageDeletionOutboxService).dispatchImmediate(storageKey);
    }

    @Test
    @DisplayName("deleteWardrobeItem: throws GroomAiException with ITEM_NOT_FOUND when not found or wrong owner")
    void deleteWardrobeItem_notFound_throwsException() {
        UUID itemId = UUID.randomUUID();
        when(wardrobeItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wardrobeService.deleteWardrobeItem(userId, itemId))
            .isInstanceOf(com.groomai.common.exception.GroomAiException.class)
            .hasMessageContaining("Wardrobe item not found");
    }

    @Test
    @DisplayName("deleteWardrobeItem: cross-user deletion attempt fails with ITEM_NOT_FOUND and never triggers outbox or deletion")
    void deleteWardrobeItem_crossUserAttempt_throwsNotFoundAndNeverEnqueues() {
        UUID itemId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        when(wardrobeItemRepository.findByIdAndUserId(itemId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wardrobeService.deleteWardrobeItem(otherUserId, itemId))
            .isInstanceOf(com.groomai.common.exception.GroomAiException.class)
            .hasMessageContaining("Wardrobe item not found");

        verify(storageDeletionOutboxService, never()).enqueueDeletion(any());
        verify(wardrobeItemRepository, never()).delete(any());
        verify(storageDeletionOutboxService, never()).dispatchImmediate(any());
    }

    @Test
    @DisplayName("updateWardrobeItem: cross-user update attempt fails with ITEM_NOT_FOUND and never mutates repository")
    void updateWardrobeItem_crossUserAttempt_throwsNotFoundAndNeverSaves() {
        UUID itemId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        when(wardrobeItemRepository.findByIdAndUserId(itemId, otherUserId)).thenReturn(Optional.empty());

        com.groomai.wardrobe.dto.UpdateWardrobeItemRequestDto request = new com.groomai.wardrobe.dto.UpdateWardrobeItemRequestDto(
            "Hacked Name",
            Category.TOP,
            "Hacked",
            Color.BLACK,
            null,
            Pattern.SOLID,
            FormalityLevel.FORMAL,
            Season.WINTER
        );

        assertThatThrownBy(() -> wardrobeService.updateWardrobeItem(otherUserId, itemId, request))
            .isInstanceOf(com.groomai.common.exception.GroomAiException.class)
            .hasMessageContaining("Wardrobe item not found");

        verify(wardrobeItemRepository, never()).save(any());
    }
}
