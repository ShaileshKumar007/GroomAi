package com.groomai.outfit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.common.exception.GroomAiException;
import com.groomai.outfit.dto.LogOutfitWearRequestDto;
import com.groomai.outfit.dto.LogOutfitWearResponseDto;
import com.groomai.outfit.dto.OutfitWearHistoryDto;
import com.groomai.outfit.dto.SaveOutfitRequestDto;
import com.groomai.outfit.dto.SavedOutfitResponseDto;
import com.groomai.outfit.dto.ScoreBreakdownDto;
import com.groomai.outfit.entity.Outfit;
import com.groomai.outfit.entity.OutfitItem;
import com.groomai.outfit.entity.OutfitWearLog;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.ScoreStatus;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.outfit.model.ScoredOutfit;
import com.groomai.outfit.model.WearLogSnapshot;
import com.groomai.outfit.repository.OutfitItemRepository;
import com.groomai.outfit.repository.OutfitRepository;
import com.groomai.outfit.repository.OutfitWearLogRepository;
import com.groomai.outfit.scorer.OutfitScoringEngine;
import com.groomai.storage.ObjectStorageService;
import com.groomai.user.entity.User;
import com.groomai.user.repository.StylePreferencesRepository;
import com.groomai.user.repository.UserRepository;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitPersistenceServiceImplTest {

    @Mock
    private OutfitRepository outfitRepository;

    @Mock
    private OutfitItemRepository outfitItemRepository;

    @Mock
    private OutfitWearLogRepository outfitWearLogRepository;

    @Mock
    private WardrobeItemRepository wardrobeItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StylePreferencesRepository stylePreferencesRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private OutfitScoringEngine outfitScoringEngine;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutfitPersistenceServiceImpl outfitPersistenceService;

    private UUID userId;
    private User testUser;
    private WardrobeItem topItem;
    private WardrobeItem bottomItem;
    private WardrobeItem footwearItem;
    private WardrobeItem outerwearItem;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).email("stylist@groomai.com").build();

        topItem = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .name("Navy Oxford Shirt")
            .category(Category.TOP)
            .subcategory("Oxford Shirt")
            .primaryColor(Color.NAVY)
            .pattern(Pattern.SOLID)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .status(ItemStatus.ACTIVE)
            .storageKey("wardrobe/user-1/top.jpg")
            .build();

        bottomItem = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .name("Slim Beige Chinos")
            .category(Category.BOTTOM)
            .subcategory("Chinos")
            .primaryColor(Color.BEIGE)
            .pattern(Pattern.SOLID)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .status(ItemStatus.ACTIVE)
            .storageKey("wardrobe/user-1/bottom.jpg")
            .build();

        footwearItem = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .name("Brown Loafers")
            .category(Category.FOOTWEAR)
            .subcategory("Loafers")
            .primaryColor(Color.BROWN)
            .pattern(Pattern.SOLID)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .status(ItemStatus.ACTIVE)
            .storageKey("wardrobe/user-1/shoes.jpg")
            .build();

        outerwearItem = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .name("Navy Blazer")
            .category(Category.OUTERWEAR)
            .subcategory("Blazer")
            .primaryColor(Color.NAVY)
            .pattern(Pattern.SOLID)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .status(ItemStatus.ACTIVE)
            .storageKey("wardrobe/user-1/blazer.jpg")
            .build();
    }

    @Test
    @DisplayName("saveOutfit: authoritative backend scoring calculates score and saves snapshots")
    void saveOutfit_success_authoritativeScoring() {
        List<UUID> itemIds = List.of(topItem.getId(), bottomItem.getId(), footwearItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.SMART_CASUAL,
            TemperatureBracket.MILD,
            WeatherCondition.SUNNY,
            "User provided client rationale",
            99 // Client-provided untrusted score
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, bottomItem, footwearItem));
        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());

        CandidateOutfit candidate = new CandidateOutfit(topItem, bottomItem, footwearItem);
        ScoredOutfit scored = new ScoredOutfit(
            candidate,
            88, // Authoritative backend calculated score
            new ScoreBreakdownDto(25, 25, 20, 10, 8, 0),
            "Authoritative styling rationale",
            0
        );
        when(outfitScoringEngine.scoreOutfit(any(), eq(Occasion.SMART_CASUAL), eq(TemperatureBracket.MILD), eq(WeatherCondition.SUNNY), any(), any()))
            .thenReturn(Optional.of(scored));

        when(outfitRepository.save(any(Outfit.class))).thenAnswer(inv -> {
            Outfit o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(objectStorageService.generateDownloadUrl(any(), eq(Duration.ofMinutes(15))))
            .thenReturn("https://storage.groomai.com/download?token=123");

        SavedOutfitResponseDto response = outfitPersistenceService.saveOutfit(userId, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.occasion()).isEqualTo(Occasion.SMART_CASUAL);
        assertThat(response.suitabilityScore()).isEqualTo(88); // Overridden authoritatively, not 99!
        assertThat(response.scoreStatus()).isEqualTo(ScoreStatus.CALCULATED);
        assertThat(response.items()).hasSize(3);

        verify(outfitRepository).save(any(Outfit.class));
    }

    @Test
    @DisplayName("saveOutfit: user curated outfit violating occasion gates receives CUSTOM status and null score")
    void saveOutfit_customCurated_nullScore() {
        List<UUID> itemIds = List.of(topItem.getId(), bottomItem.getId(), footwearItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.FORMAL, // Casual pieces for formal occasion
            null,
            null,
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, bottomItem, footwearItem));
        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(outfitScoringEngine.scoreOutfit(any(), eq(Occasion.FORMAL), any(), any(), any(), any()))
            .thenReturn(Optional.empty()); // Gate rejection

        when(outfitRepository.save(any(Outfit.class))).thenAnswer(inv -> {
            Outfit o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        SavedOutfitResponseDto response = outfitPersistenceService.saveOutfit(userId, request);

        assertThat(response.suitabilityScore()).isNull();
        assertThat(response.scoreStatus()).isEqualTo(ScoreStatus.CUSTOM);
        assertThat(response.stylingRationale()).contains("Custom curated");
    }

    @Test
    @DisplayName("saveOutfit: rejects duplicate item IDs in request payload")
    void saveOutfit_rejectsDuplicateItemIds() {
        List<UUID> itemIds = List.of(topItem.getId(), topItem.getId(), footwearItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.CASUAL,
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("duplicate wardrobe items");
    }

    @Test
    @DisplayName("saveOutfit: rejects request with invalid item count (< 3 or > 5)")
    void saveOutfit_rejectsInvalidItemCount() {
        List<UUID> itemIds = List.of(topItem.getId(), bottomItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.CASUAL,
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("between 3 and 5 items");
    }

    @Test
    @DisplayName("saveOutfit: rejects items belonging to another user (security tenant isolation)")
    void saveOutfit_rejectsUnauthorizedItems() {
        User otherUser = User.builder().id(UUID.randomUUID()).email("intruder@groomai.com").build();
        WardrobeItem foreignItem = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(otherUser)
            .category(Category.FOOTWEAR)
            .status(ItemStatus.ACTIVE)
            .build();

        List<UUID> itemIds = List.of(topItem.getId(), bottomItem.getId(), foreignItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.CASUAL,
            null,
            null,
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, bottomItem, foreignItem));

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("You do not own all submitted wardrobe items")
            .matches(ex -> ((GroomAiException) ex).getHttpStatus() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("saveOutfit: rejects inactive or archived items")
    void saveOutfit_rejectsInactiveItem() {
        WardrobeItem archivedBottom = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .name("Old Chinos")
            .category(Category.BOTTOM)
            .status(ItemStatus.ARCHIVED)
            .build();

        List<UUID> itemIds = List.of(topItem.getId(), archivedBottom.getId(), footwearItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.CASUAL,
            null,
            null,
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, archivedBottom, footwearItem));

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("Cannot add inactive or archived item");
    }

    @Test
    @DisplayName("saveOutfit: rejects invalid composition (e.g., missing footwear, 2 tops)")
    void saveOutfit_rejectsInvalidComposition() {
        WardrobeItem secondTop = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .category(Category.TOP)
            .status(ItemStatus.ACTIVE)
            .build();

        List<UUID> itemIds = List.of(topItem.getId(), secondTop.getId(), bottomItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.CASUAL,
            null,
            null,
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, secondTop, bottomItem));

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("Outfit must contain exactly one Top, one Bottom, and one Footwear");
    }

    @Test
    @DisplayName("logOutfitWear: atomic insertWearLogIdempotent records wear event successfully")
    void logOutfitWear_success() {
        UUID outfitId = UUID.randomUUID();
        Outfit outfit = Outfit.builder()
            .id(outfitId)
            .user(testUser)
            .occasion(Occasion.SMART_CASUAL)
            .stylingRationale("Classic look")
            .suitabilityScore(90)
            .scoreStatus(ScoreStatus.CALCULATED)
            .build();

        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LogOutfitWearRequestDto request = new LogOutfitWearRequestDto(today, "Client pitch meeting", "UTC");

        when(outfitRepository.findByIdAndUserId(outfitId, userId)).thenReturn(Optional.of(outfit));
        when(outfitWearLogRepository.insertWearLogIdempotent(any(), eq(userId), eq(outfitId), eq(today), eq("Client pitch meeting"), any(), anyString()))
            .thenReturn(1);

        LogOutfitWearResponseDto response = outfitPersistenceService.logOutfitWear(userId, outfitId, request);

        assertThat(response.outfitId()).isEqualTo(outfitId);
        assertThat(response.wornDate()).isEqualTo(today);
        assertThat(response.message()).contains("successfully logged");

        verify(outfitWearLogRepository).insertWearLogIdempotent(any(), eq(userId), eq(outfitId), eq(today), eq("Client pitch meeting"), any(), anyString());
    }

    @Test
    @DisplayName("logOutfitWear: handles accidental duplicate log on same date idempotently without transaction rollback")
    void logOutfitWear_idempotentDuplicateHandling() {
        UUID outfitId = UUID.randomUUID();
        Outfit outfit = Outfit.builder().id(outfitId).user(testUser).build();
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        UUID existingLogId = UUID.randomUUID();
        OutfitWearLog existingLog = OutfitWearLog.builder().id(existingLogId).outfit(outfit).wornDate(today).build();

        when(outfitRepository.findByIdAndUserId(outfitId, userId)).thenReturn(Optional.of(outfit));
        when(outfitWearLogRepository.insertWearLogIdempotent(any(), eq(userId), eq(outfitId), eq(today), eq("Repeated tap"), any(), anyString()))
            .thenReturn(0); // Conflict!
        when(outfitWearLogRepository.findByUserIdAndOutfitIdAndWornDate(userId, outfitId, today))
            .thenReturn(Optional.of(existingLog));

        LogOutfitWearResponseDto response = outfitPersistenceService.logOutfitWear(
            userId, outfitId, new LogOutfitWearRequestDto(today, "Repeated tap", "UTC")
        );

        assertThat(response.outfitId()).isEqualTo(outfitId);
        assertThat(response.logId()).isEqualTo(existingLogId);
        assertThat(response.message()).contains("already recorded");
    }

    @Test
    @DisplayName("logOutfitWear: rejects future wear dates based on client timezone")
    void logOutfitWear_rejectsFutureDate() {
        UUID outfitId = UUID.randomUUID();
        Outfit outfit = Outfit.builder().id(outfitId).user(testUser).build();
        LocalDate futureDate = LocalDate.now().plusDays(2);

        when(outfitRepository.findByIdAndUserId(outfitId, userId)).thenReturn(Optional.of(outfit));

        assertThatThrownBy(() -> outfitPersistenceService.logOutfitWear(
            userId, outfitId, new LogOutfitWearRequestDto(futureDate, "Future", "UTC")
        ))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("future dates");
    }

    @Test
    @DisplayName("getWearHistory: accurately maps from immutable snapshot even if wardrobe items are deleted")
    void getWearHistory_fromImmutableSnapshot_deletedItem() {
        UUID outfitId = UUID.randomUUID();
        UUID deletedItemId = UUID.randomUUID();

        WearLogSnapshot.WearLogItemSnapshot itemSnapshot = new WearLogSnapshot.WearLogItemSnapshot(
            deletedItemId,
            "TOP",
            Category.TOP,
            "Oxford Shirt",
            Color.NAVY,
            null,
            Pattern.SOLID,
            "Cotton",
            "Brooks Brothers",
            "wardrobe/old-key.jpg"
        );

        WearLogSnapshot snapshot = new WearLogSnapshot(
            1,
            outfitId,
            "Navy Classic",
            Occasion.SMART_CASUAL,
            TemperatureBracket.MILD,
            WeatherCondition.SUNNY,
            "Classic navy pairing",
            90,
            ScoreStatus.CALCULATED,
            List.of(itemSnapshot)
        );

        OutfitWearLog log = OutfitWearLog.builder()
            .id(UUID.randomUUID())
            .wornDate(LocalDate.now().minusDays(3))
            .notes("Wore to anniversary")
            .snapshot(snapshot)
            .build();

        Page<OutfitWearLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1);
        when(outfitWearLogRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(page);
        when(wardrobeItemRepository.findById(deletedItemId)).thenReturn(Optional.empty()); // Item deleted!

        Page<OutfitWearHistoryDto> result = outfitPersistenceService.getWearHistory(userId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        OutfitWearHistoryDto dto = result.getContent().get(0);
        assertThat(dto.outfitName()).isEqualTo("Navy Classic");
        assertThat(dto.suitabilityScore()).isEqualTo(90);
        assertThat(dto.scoreStatus()).isEqualTo(ScoreStatus.CALCULATED);
        assertThat(dto.items()).hasSize(1);

        var firstItem = dto.items().get(0);
        assertThat(firstItem.category()).isEqualTo(Category.TOP);
        assertThat(firstItem.primaryColor()).isEqualTo(Color.NAVY);
        assertThat(firstItem.name()).isEqualTo("Oxford Shirt");
        assertThat(firstItem.downloadUrl()).isNull(); // Image is null since item was deleted, but all snapshot metadata is preserved!
    }

    @Test
    @DisplayName("getRecentItemWearDays calculates exact days between wear date and user's reference date")
    void testGetRecentItemWearDaysWithReferenceDate() {
        UUID itemId1 = UUID.randomUUID(); // worn 30 days ago
        UUID itemId2 = UUID.randomUUID(); // worn 31 days ago
        UUID itemId3 = UUID.randomUUID(); // worn 0 days ago (today)

        LocalDate userToday = LocalDate.of(2026, 11, 3);
        LocalDate worn30DaysAgo = userToday.minusDays(30);
        LocalDate worn31DaysAgo = userToday.minusDays(31);

        List<Object[]> rows = List.of(
            new Object[]{itemId1, worn30DaysAgo},
            new Object[]{itemId2, worn31DaysAgo},
            new Object[]{itemId3, userToday}
        );

        when(outfitWearLogRepository.findLatestWornDatesByUserId(userId)).thenReturn(rows);

        Map<UUID, Integer> wearDays = outfitPersistenceService.getRecentItemWearDays(userId, userToday);

        assertThat(wearDays).containsEntry(itemId1, 30);
        assertThat(wearDays).containsEntry(itemId2, 31);
        assertThat(wearDays).containsEntry(itemId3, 0);
    }

    @Test
    @DisplayName("resolveZoneId properly resolves valid IANA zones and falls back to UTC on invalid or missing input")
    void testResolveZoneIdBehavior() {
        assertThat(OutfitPersistenceServiceImpl.resolveZoneId("America/New_York")).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(OutfitPersistenceServiceImpl.resolveZoneId("Asia/Tokyo")).isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(OutfitPersistenceServiceImpl.resolveZoneId("UTC").normalized()).isEqualTo(ZoneOffset.UTC);
        assertThat(OutfitPersistenceServiceImpl.resolveZoneId(null)).isEqualTo(ZoneOffset.UTC);
        assertThat(OutfitPersistenceServiceImpl.resolveZoneId("   ")).isEqualTo(ZoneOffset.UTC);
        assertThat(OutfitPersistenceServiceImpl.resolveZoneId("Invalid/NonExistent_Zone")).isEqualTo(ZoneOffset.UTC);
    }
}
