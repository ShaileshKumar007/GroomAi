package com.groomai.outfit.service;

import com.groomai.common.exception.GroomAiException;
import com.groomai.outfit.dto.LogOutfitWearRequestDto;
import com.groomai.outfit.dto.LogOutfitWearResponseDto;
import com.groomai.outfit.dto.OutfitWearHistoryDto;
import com.groomai.outfit.dto.SaveOutfitRequestDto;
import com.groomai.outfit.dto.SavedOutfitResponseDto;
import com.groomai.outfit.entity.Outfit;
import com.groomai.outfit.entity.OutfitItem;
import com.groomai.outfit.entity.OutfitWearLog;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.repository.OutfitItemRepository;
import com.groomai.outfit.repository.OutfitRepository;
import com.groomai.outfit.repository.OutfitWearLogRepository;
import com.groomai.storage.ObjectStorageService;
import com.groomai.user.entity.User;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private ObjectStorageService objectStorageService;

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
            .primaryColor(Color.NAVY)
            .pattern(Pattern.SOLID)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .status(ItemStatus.ACTIVE)
            .storageKey("wardrobe/user-1/blazer.jpg")
            .build();
    }

    @Test
    @DisplayName("saveOutfit: successfully saves 3-piece core outfit and returns response with download URLs")
    void saveOutfit_success_coreItems() {
        List<UUID> itemIds = List.of(topItem.getId(), bottomItem.getId(), footwearItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.SMART_CASUAL,
            TemperatureBracket.MILD,
            WeatherCondition.SUNNY,
            "Classic Navy and Beige contrast.",
            92
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, bottomItem, footwearItem));
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
        assertThat(response.suitabilityScore()).isEqualTo(92);
        assertThat(response.stylingRationale()).isEqualTo("Classic Navy and Beige contrast.");
        assertThat(response.items()).hasSize(3);
        assertThat(response.items().get(0).downloadUrl()).isNotNull();

        verify(outfitRepository).save(any(Outfit.class));
    }

    @Test
    @DisplayName("saveOutfit: successfully saves 4-piece outfit with optional outerwear")
    void saveOutfit_success_withOuterwear() {
        List<UUID> itemIds = List.of(topItem.getId(), bottomItem.getId(), footwearItem.getId(), outerwearItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.SMART_CASUAL,
            TemperatureBracket.COOL,
            WeatherCondition.SUNNY,
            "Layered with blazer.",
            95
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, bottomItem, footwearItem, outerwearItem));
        when(outfitRepository.save(any(Outfit.class))).thenAnswer(inv -> {
            Outfit o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        SavedOutfitResponseDto response = outfitPersistenceService.saveOutfit(userId, request);
        assertThat(response.items()).hasSize(4);
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
            80
        );

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("duplicate wardrobe items")
            .matches(e -> ((GroomAiException) e).getHttpStatus() == HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("saveOutfit: rejects missing core categories (e.g. missing FOOTWEAR)")
    void saveOutfit_rejectsMissingCoreCategory() {
        WardrobeItem secondTop = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .name("White Tee")
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
            80
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, secondTop, bottomItem));

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("exactly one Top, one Bottom, and one Footwear");
    }

    @Test
    @DisplayName("saveOutfit: rejects wardrobe items belonging to another user")
    void saveOutfit_rejectsAnotherUsersItem() {
        User otherUser = User.builder().id(UUID.randomUUID()).email("other@groomai.com").build();
        WardrobeItem alienItem = WardrobeItem.builder()
            .id(UUID.randomUUID())
            .user(otherUser)
            .name("Stolen Jacket")
            .category(Category.OUTERWEAR)
            .status(ItemStatus.ACTIVE)
            .build();

        List<UUID> itemIds = List.of(topItem.getId(), bottomItem.getId(), footwearItem.getId(), alienItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.CASUAL,
            null,
            null,
            null,
            80
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, bottomItem, footwearItem, alienItem));

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("do not own all submitted wardrobe items")
            .matches(e -> ((GroomAiException) e).getHttpStatus() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("saveOutfit: rejects inactive or archived items")
    void saveOutfit_rejectsArchivedItem() {
        topItem.setStatus(ItemStatus.ARCHIVED);
        List<UUID> itemIds = List.of(topItem.getId(), bottomItem.getId(), footwearItem.getId());
        SaveOutfitRequestDto request = new SaveOutfitRequestDto(
            itemIds,
            Occasion.CASUAL,
            null,
            null,
            null,
            80
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(wardrobeItemRepository.findAllById(itemIds)).thenReturn(List.of(topItem, bottomItem, footwearItem));

        assertThatThrownBy(() -> outfitPersistenceService.saveOutfit(userId, request))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("inactive or archived item");
    }

    @Test
    @DisplayName("logOutfitWear: successfully logs wear event for authenticated outfit")
    void logOutfitWear_success() {
        UUID outfitId = UUID.randomUUID();
        Outfit outfit = Outfit.builder()
            .id(outfitId)
            .user(testUser)
            .occasion(Occasion.SMART_CASUAL)
            .stylingRationale("Classic look")
            .suitabilityScore(90)
            .build();

        LocalDate today = LocalDate.now();
        LogOutfitWearRequestDto request = new LogOutfitWearRequestDto(today, "Client pitch meeting");

        when(outfitRepository.findByIdAndUserId(outfitId, userId)).thenReturn(Optional.of(outfit));
        when(outfitWearLogRepository.existsByUserIdAndOutfitIdAndWornDate(userId, outfitId, today)).thenReturn(false);
        when(outfitWearLogRepository.save(any(OutfitWearLog.class))).thenAnswer(inv -> {
            OutfitWearLog owl = inv.getArgument(0);
            owl.setId(UUID.randomUUID());
            return owl;
        });

        LogOutfitWearResponseDto response = outfitPersistenceService.logOutfitWear(userId, outfitId, request);

        assertThat(response.outfitId()).isEqualTo(outfitId);
        assertThat(response.wornDate()).isEqualTo(today);
        assertThat(response.message()).contains("successfully logged");

        verify(outfitWearLogRepository).save(any(OutfitWearLog.class));
    }

    @Test
    @DisplayName("logOutfitWear: handles accidental duplicate log on same date idempotently")
    void logOutfitWear_idempotentDuplicateHandling() {
        UUID outfitId = UUID.randomUUID();
        Outfit outfit = Outfit.builder().id(outfitId).user(testUser).build();
        LocalDate today = LocalDate.now();

        when(outfitRepository.findByIdAndUserId(outfitId, userId)).thenReturn(Optional.of(outfit));
        when(outfitWearLogRepository.existsByUserIdAndOutfitIdAndWornDate(userId, outfitId, today)).thenReturn(true);

        LogOutfitWearResponseDto response = outfitPersistenceService.logOutfitWear(
            userId, outfitId, new LogOutfitWearRequestDto(today, "Repeated tap")
        );

        assertThat(response.outfitId()).isEqualTo(outfitId);
        assertThat(response.message()).contains("already recorded");
    }

    @Test
    @DisplayName("logOutfitWear: rejects future wear dates")
    void logOutfitWear_rejectsFutureDate() {
        UUID outfitId = UUID.randomUUID();
        Outfit outfit = Outfit.builder().id(outfitId).user(testUser).build();
        LocalDate futureDate = LocalDate.now().plusDays(5);

        when(outfitRepository.findByIdAndUserId(outfitId, userId)).thenReturn(Optional.of(outfit));

        assertThatThrownBy(() -> outfitPersistenceService.logOutfitWear(
            userId, outfitId, new LogOutfitWearRequestDto(futureDate, "Future")
        ))
            .isInstanceOf(GroomAiException.class)
            .hasMessageContaining("future dates");
    }

    @Test
    @DisplayName("logOutfitWear: rejects logging wear for non-existent or other user's outfit")
    void logOutfitWear_notFoundOrOtherUser() {
        UUID outfitId = UUID.randomUUID();
        when(outfitRepository.findByIdAndUserId(outfitId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> outfitPersistenceService.logOutfitWear(userId, outfitId, null))
            .isInstanceOf(GroomAiException.class)
            .matches(e -> ((GroomAiException) e).getHttpStatus() == HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getWearHistory: retrieves historical wear logs and handles deleted wardrobe items gracefully")
    void getWearHistory_withDeletedItemGracefulFallback() {
        UUID outfitId = UUID.randomUUID();
        Outfit outfit = Outfit.builder()
            .id(outfitId)
            .user(testUser)
            .occasion(Occasion.SMART_CASUAL)
            .stylingRationale("Saved look")
            .suitabilityScore(88)
            .items(new ArrayList<>())
            .build();

        // One item still exists, one was deleted (wardrobeItem is null)
        outfit.addItem(OutfitItem.builder().outfit(outfit).wardrobeItem(topItem).itemRole("TOP").build());
        outfit.addItem(OutfitItem.builder().outfit(outfit).wardrobeItem(null).itemRole("BOTTOM").build());

        OutfitWearLog logEntry = OutfitWearLog.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .outfit(outfit)
            .wornDate(LocalDate.now().minusDays(2))
            .notes("Nice dinner")
            .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(outfitWearLogRepository.findByUserId(userId, pageable))
            .thenReturn(new PageImpl<>(List.of(logEntry), pageable, 1));

        Page<OutfitWearHistoryDto> historyPage = outfitPersistenceService.getWearHistory(userId, pageable);

        assertThat(historyPage.getContent()).hasSize(1);
        OutfitWearHistoryDto item = historyPage.getContent().get(0);
        assertThat(item.outfitId()).isEqualTo(outfitId);
        assertThat(item.items()).hasSize(2);
        assertThat(item.items().get(0).name()).isEqualTo("Navy Oxford Shirt");
        assertThat(item.items().get(1).name()).isEqualTo("[Removed Item]");
    }

    @Test
    @DisplayName("getRecentItemWearDays: calculates days since last worn correctly for scoring engine")
    void getRecentItemWearDays_calculatesDays() {
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        List<Object[]> queryResults = List.of(
            new Object[]{itemId1, today},
            new Object[]{itemId2, today.minusDays(3)}
        );

        when(outfitWearLogRepository.findLatestWornDatesByUserId(userId)).thenReturn(queryResults);

        Map<UUID, Integer> map = outfitPersistenceService.getRecentItemWearDays(userId);

        assertThat(map).hasSize(2);
        assertThat(map.get(itemId1)).isEqualTo(0);
        assertThat(map.get(itemId2)).isEqualTo(3);
    }

    @Test
    @DisplayName("getRecentItemWearDays: returns empty map when user has no wear logs (zero penalty)")
    void getRecentItemWearDays_emptyWhenNoLogs() {
        when(outfitWearLogRepository.findLatestWornDatesByUserId(userId)).thenReturn(Collections.emptyList());

        Map<UUID, Integer> map = outfitPersistenceService.getRecentItemWearDays(userId);

        assertThat(map).isEmpty();
    }
}
