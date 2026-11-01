package com.groomai.outfit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.outfit.dto.LogOutfitWearRequestDto;
import com.groomai.outfit.dto.OutfitWearHistoryDto;
import com.groomai.outfit.dto.SavedOutfitResponseDto;
import com.groomai.outfit.entity.Outfit;
import com.groomai.outfit.entity.OutfitItem;
import com.groomai.outfit.entity.OutfitWearLog;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.ScoreStatus;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.model.WearLogSnapshot;
import com.groomai.outfit.repository.OutfitItemRepository;
import com.groomai.outfit.repository.OutfitRepository;
import com.groomai.outfit.repository.OutfitWearLogRepository;
import com.groomai.outfit.service.OutfitPersistenceService;
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
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for outfit persistence, immutable garment snapshots,
 * versioned JSONB wear logging, duplicate reconciliation, and Flyway migration execution
 * on a real PostgreSQL 16 container.
 */
@SpringBootTest
@EnabledIf("isDockerAvailable")
class OutfitPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(OutfitPostgresIntegrationTest.class);

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("groomai_outfit_test")
        .withUsername("test_user")
        .withPassword("test_password");

    static {
        if (isDockerAvailable()) {
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (isDockerAvailable() && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.url", postgres::getJdbcUrl);
            registry.add("spring.flyway.user", postgres::getUsername);
            registry.add("spring.flyway.password", postgres::getPassword);
        }
    }

    @AfterAll
    static void tearDown() {
        if (postgres.isRunning()) {
            postgres.stop();
        }
    }

    static boolean isDockerAvailable() {
        try {
            boolean available = DockerClientFactory.instance().isDockerAvailable();
            if (!available) {
                log.info("Docker is not available in the local execution environment. Test will run in CI.");
            }
            return available;
        } catch (Throwable t) {
            log.info("Docker check failed: {}. Test will run in CI environment.", t.getMessage());
            return false;
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WardrobeItemRepository wardrobeItemRepository;

    @Autowired
    private OutfitRepository outfitRepository;

    @Autowired
    private OutfitItemRepository outfitItemRepository;

    @Autowired
    private OutfitWearLogRepository outfitWearLogRepository;

    @Autowired
    private OutfitPersistenceService outfitPersistenceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private User createTestUser(String emailPrefix) {
        User user = User.builder()
            .email(emailPrefix + "_" + UUID.randomUUID() + "@example.com")
            .passwordHash("$2a$12$e80wYF8lZ70vMqmZ3O3YwOqL1E5gqN...")
            .role("ROLE_USER")
            .build();
        return userRepository.save(user);
    }

    private Outfit createOutfitForUser(User user) {
        Outfit outfit = Outfit.builder()
            .user(user)
            .name("Casual Weekend")
            .occasion(Occasion.CASUAL)
            .temperatureBracket(TemperatureBracket.MILD)
            .weatherCondition(WeatherCondition.SUNNY)
            .stylingRationale("Comfortable layered casual ensemble")
            .suitabilityScore(85)
            .scoreStatus(ScoreStatus.CALCULATED)
            .build();
        return outfitRepository.save(outfit);
    }

    @Test
    @DisplayName("Test 1 — Partial unique index and idempotency under concurrent inserts")
    void testPartialUniqueIndexAndIdempotency() throws Exception {
        User user1 = createTestUser("user1_idempotent");
        User user2 = createTestUser("user2_idempotent");

        Outfit outfit1 = createOutfitForUser(user1);
        Outfit outfit2 = createOutfitForUser(user2);

        WearLogSnapshot snapshot = new WearLogSnapshot(
            1,
            outfit1.getId(),
            outfit1.getName(),
            outfit1.getOccasion(),
            outfit1.getTemperatureBracket(),
            outfit1.getWeatherCondition(),
            outfit1.getStylingRationale(),
            outfit1.getSuitabilityScore(),
            outfit1.getScoreStatus(),
            List.of()
        );
        String snapshotJson = objectMapper.writeValueAsString(snapshot);

        LocalDate wornDate = LocalDate.now();

        // 1. Initial insert succeeds and returns 1 affected row
        UUID logId1 = UUID.randomUUID();
        Instant now = Instant.now();
        int rows1 = outfitWearLogRepository.insertWearLogIdempotent(
            logId1, user1.getId(), outfit1.getId(), wornDate, "First wear notes", now, snapshotJson
        );
        assertThat(rows1).isEqualTo(1);

        // 2. Duplicate insert for same (user_id, outfit_id, worn_date) returns 0 affected rows
        UUID logId2 = UUID.randomUUID();
        int rows2 = outfitWearLogRepository.insertWearLogIdempotent(
            logId2, user1.getId(), outfit1.getId(), wornDate, "Conflicting second notes", now, snapshotJson
        );
        assertThat(rows2).isEqualTo(0);

        // 3. Existing wear-log record is retrieved intact and original notes are preserved
        Optional<OutfitWearLog> retrieved = outfitWearLogRepository.findByUserIdAndOutfitIdAndWornDate(
            user1.getId(), outfit1.getId(), wornDate
        );
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(logId1);
        assertThat(retrieved.get().getNotes()).isEqualTo("First wear notes");

        // 4. Same outfit logged on a different date succeeds (1 row)
        LocalDate tomorrow = wornDate.plusDays(1);
        UUID logIdTomorrow = UUID.randomUUID();
        int rowsTomorrow = outfitWearLogRepository.insertWearLogIdempotent(
            logIdTomorrow, user1.getId(), outfit1.getId(), tomorrow, "Next day wear", now, snapshotJson
        );
        assertThat(rowsTomorrow).isEqualTo(1);

        // 5. Different user independently logs their outfit on the same worn_date succeeds (1 row)
        UUID logIdUser2 = UUID.randomUUID();
        int rowsUser2 = outfitWearLogRepository.insertWearLogIdempotent(
            logIdUser2, user2.getId(), outfit2.getId(), wornDate, "User 2 notes", now, snapshotJson
        );
        assertThat(rowsUser2).isEqualTo(1);

        // 6. Concurrent insertion attempts for the same (user_id, outfit_id, worn_date) result in exactly 1 stored record
        Outfit concurrentOutfit = createOutfitForUser(user1);
        LocalDate concurrentDate = wornDate.minusDays(7);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger zeroCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    int inserted = outfitWearLogRepository.insertWearLogIdempotent(
                        UUID.randomUUID(),
                        user1.getId(),
                        concurrentOutfit.getId(),
                        concurrentDate,
                        "Concurrent attempt " + index,
                        Instant.now(),
                        snapshotJson
                    );
                    if (inserted == 1) {
                        successCount.incrementAndGet();
                    } else if (inserted == 0) {
                        zeroCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Concurrent wear log insert execution failed", e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(zeroCount.get()).isEqualTo(9);

        Optional<OutfitWearLog> singleStored = outfitWearLogRepository.findByUserIdAndOutfitIdAndWornDate(
            user1.getId(), concurrentOutfit.getId(), concurrentDate
        );
        assertThat(singleStored).isPresent();
    }

    @Test
    @DisplayName("Test 2 — Wardrobe item deletion preserves immutable garment metadata on outfit items")
    void testWardrobeItemDeletionPreservesGarmentSnapshots() {
        User user = createTestUser("user_item_deletion");

        // 1. Create wardrobe items with full metadata
        WardrobeItem top = wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user)
            .name("White Oxford Button-Down")
            .category(Category.TOP)
            .subcategory("Oxford Shirt")
            .primaryColor(Color.WHITE)
            .secondaryColor(Color.BLUE)
            .pattern(Pattern.STRIPED)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .storageKey("wardrobe/" + user.getId() + "/oxford_" + UUID.randomUUID() + ".jpg")
            .status(ItemStatus.ACTIVE)
            .build());

        WardrobeItem bottom = wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user)
            .name("Navy Tailored Trousers")
            .category(Category.BOTTOM)
            .subcategory("Trousers")
            .primaryColor(Color.NAVY)
            .pattern(Pattern.SOLID)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .storageKey("wardrobe/" + user.getId() + "/trousers_" + UUID.randomUUID() + ".jpg")
            .status(ItemStatus.ACTIVE)
            .build());

        WardrobeItem footwear = wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user)
            .name("Brown Leather Loafers")
            .category(Category.FOOTWEAR)
            .subcategory("Loafers")
            .primaryColor(Color.BROWN)
            .pattern(Pattern.SOLID)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .storageKey("wardrobe/" + user.getId() + "/loafers_" + UUID.randomUUID() + ".jpg")
            .status(ItemStatus.ACTIVE)
            .build());

        // 2. Create outfit referencing wardrobe items with snapshot attributes populated
        Outfit outfit = Outfit.builder()
            .user(user)
            .name("Business Smart")
            .occasion(Occasion.BUSINESS_CASUAL)
            .temperatureBracket(TemperatureBracket.MILD)
            .weatherCondition(WeatherCondition.SUNNY)
            .stylingRationale("Classic smart casual pairing")
            .suitabilityScore(90)
            .scoreStatus(ScoreStatus.CALCULATED)
            .build();

        OutfitItem topItem = OutfitItem.builder()
            .outfit(outfit)
            .wardrobeItem(top)
            .itemRole("TOP")
            .category(top.getCategory())
            .subcategory(top.getSubcategory())
            .primaryColor(top.getPrimaryColor())
            .secondaryColor(top.getSecondaryColor())
            .pattern(top.getPattern())
            .fabric("100% Egyptian Cotton")
            .brand("Brooks Brothers")
            .originalStorageKey(top.getStorageKey())
            .build();

        OutfitItem bottomItem = OutfitItem.builder()
            .outfit(outfit)
            .wardrobeItem(bottom)
            .itemRole("BOTTOM")
            .category(bottom.getCategory())
            .subcategory(bottom.getSubcategory())
            .primaryColor(bottom.getPrimaryColor())
            .pattern(bottom.getPattern())
            .fabric("Wool Blend")
            .brand("Incotex")
            .originalStorageKey(bottom.getStorageKey())
            .build();

        OutfitItem footwearItem = OutfitItem.builder()
            .outfit(outfit)
            .wardrobeItem(footwear)
            .itemRole("FOOTWEAR")
            .category(footwear.getCategory())
            .subcategory(footwear.getSubcategory())
            .primaryColor(footwear.getPrimaryColor())
            .pattern(footwear.getPattern())
            .fabric("Calfskin")
            .brand("Alden")
            .originalStorageKey(footwear.getStorageKey())
            .build();

        outfit.addItem(topItem);
        outfit.addItem(bottomItem);
        outfit.addItem(footwearItem);

        outfit = outfitRepository.save(outfit);
        UUID savedOutfitId = outfit.getId();
        UUID topItemId = topItem.getId();

        // 3. Delete the referenced TOP wardrobe item in PostgreSQL (tests ON DELETE SET NULL)
        jdbcTemplate.update("DELETE FROM wardrobe_items WHERE id = ?", top.getId());

        // Clear persistence context to verify real database foreign key behavior
        entityManager.clear();

        // 4. Verify wardrobe item no longer exists
        assertThat(wardrobeItemRepository.findById(top.getId())).isEmpty();

        // 5. Verify outfit_items.wardrobe_item_id became NULL while all garment metadata snapshots remain intact
        OutfitItem reloadedTopItem = outfitItemRepository.findById(topItemId).orElseThrow();
        assertThat(reloadedTopItem.getWardrobeItem()).isNull();
        assertThat(reloadedTopItem.getCategory()).isEqualTo(Category.TOP);
        assertThat(reloadedTopItem.getSubcategory()).isEqualTo("Oxford Shirt");
        assertThat(reloadedTopItem.getPrimaryColor()).isEqualTo(Color.WHITE);
        assertThat(reloadedTopItem.getSecondaryColor()).isEqualTo(Color.BLUE);
        assertThat(reloadedTopItem.getPattern()).isEqualTo(Pattern.STRIPED);
        assertThat(reloadedTopItem.getFabric()).isEqualTo("100% Egyptian Cotton");
        assertThat(reloadedTopItem.getBrand()).isEqualTo("Brooks Brothers");
        assertThat(reloadedTopItem.getOriginalStorageKey()).isEqualTo(top.getStorageKey());

        // 6. Verify outfit detail query can still display complete garment details via service
        SavedOutfitResponseDto savedOutfitDto = outfitPersistenceService.getSavedOutfitById(user.getId(), savedOutfitId);
        assertThat(savedOutfitDto).isNotNull();
        assertThat(savedOutfitDto.items()).hasSize(3);

        var topDto = savedOutfitDto.items().stream()
            .filter(i -> "TOP".equals(i.role()))
            .findFirst()
            .orElseThrow();

        assertThat(topDto.id()).isNull(); // Wardrobe item was eradicated
        assertThat(topDto.name()).isEqualTo("Oxford Shirt"); // Preserved subcategory snapshot
        assertThat(topDto.category()).isEqualTo(Category.TOP);
        assertThat(topDto.primaryColor()).isEqualTo(Color.WHITE);
        assertThat(topDto.secondaryColor()).isEqualTo(Color.BLUE);
        assertThat(topDto.pattern()).isEqualTo(Pattern.STRIPED);
    }

    @Test
    @DisplayName("Test 3 — Outfit deletion preserves wear log and versioned JSONB snapshot")
    void testOutfitDeletionPreservesWearLogSnapshot() {
        User user = createTestUser("user_outfit_deletion");

        // 1. Create and save outfit
        Outfit outfit = Outfit.builder()
            .user(user)
            .name("Anniversary Dinner")
            .occasion(Occasion.DATE_NIGHT)
            .temperatureBracket(TemperatureBracket.MILD)
            .weatherCondition(WeatherCondition.SUNNY)
            .stylingRationale("Sophisticated evening combination")
            .suitabilityScore(94)
            .scoreStatus(ScoreStatus.CALCULATED)
            .build();

        OutfitItem item1 = OutfitItem.builder()
            .outfit(outfit)
            .itemRole("TOP")
            .category(Category.TOP)
            .subcategory("Silk Blend Shirt")
            .primaryColor(Color.BLACK)
            .pattern(Pattern.SOLID)
            .fabric("Silk")
            .brand("Tom Ford")
            .originalStorageKey("wardrobe/silk.jpg")
            .build();
        outfit.addItem(item1);

        outfit = outfitRepository.save(outfit);
        UUID outfitId = outfit.getId();

        // 2. Log wear event via service (builds versioned JSONB snapshot)
        LocalDate wornDate = LocalDate.now();
        var wearResponse = outfitPersistenceService.logOutfitWear(
            user.getId(),
            outfitId,
            new LogOutfitWearRequestDto(wornDate, "Special anniversary evening celebration", "UTC")
        );
        UUID wearLogId = wearResponse.logId();

        // Clear persistence context
        entityManager.clear();

        // 3. Verify wear log has populated JSONB snapshot
        OutfitWearLog wearLog = outfitWearLogRepository.findById(wearLogId).orElseThrow();
        assertThat(wearLog.getSnapshot()).isNotNull();
        assertThat(wearLog.getSnapshot().version()).isEqualTo(1);
        assertThat(wearLog.getSnapshot().outfitId()).isEqualTo(outfitId);
        assertThat(wearLog.getSnapshot().name()).isEqualTo("Anniversary Dinner");
        assertThat(wearLog.getSnapshot().occasion()).isEqualTo(Occasion.DATE_NIGHT);
        assertThat(wearLog.getSnapshot().suitabilityScore()).isEqualTo(94);
        assertThat(wearLog.getSnapshot().scoreStatus()).isEqualTo(ScoreStatus.CALCULATED);
        assertThat(wearLog.getSnapshot().items()).hasSize(1);
        assertThat(wearLog.getSnapshot().items().get(0).subcategory()).isEqualTo("Silk Blend Shirt");

        // 4. Delete the outfit record in PostgreSQL (tests ON DELETE SET NULL on outfit_wear_logs.outfit_id)
        jdbcTemplate.update("DELETE FROM outfits WHERE id = ?", outfitId);

        entityManager.clear();

        // 5. Verify outfit is gone
        assertThat(outfitRepository.findById(outfitId)).isEmpty();

        // 6. Verify wear log persists and outfit_id became NULL
        OutfitWearLog survivingWearLog = outfitWearLogRepository.findById(wearLogId).orElseThrow();
        assertThat(survivingWearLog.getOutfit()).isNull();
        assertThat(survivingWearLog.getNotes()).isEqualTo("Special anniversary evening celebration");

        // 7. Verify JSONB snapshot remains fully intact
        WearLogSnapshot survivingSnapshot = survivingWearLog.getSnapshot();
        assertThat(survivingSnapshot).isNotNull();
        assertThat(survivingSnapshot.version()).isEqualTo(1);
        assertThat(survivingSnapshot.name()).isEqualTo("Anniversary Dinner");
        assertThat(survivingSnapshot.occasion()).isEqualTo(Occasion.DATE_NIGHT);
        assertThat(survivingSnapshot.temperatureBracket()).isEqualTo(TemperatureBracket.MILD);
        assertThat(survivingSnapshot.weatherCondition()).isEqualTo(WeatherCondition.SUNNY);
        assertThat(survivingSnapshot.suitabilityScore()).isEqualTo(94);
        assertThat(survivingSnapshot.scoreStatus()).isEqualTo(ScoreStatus.CALCULATED);
        assertThat(survivingSnapshot.stylingRationale()).isEqualTo("Sophisticated evening combination");
        assertThat(survivingSnapshot.items()).hasSize(1);
        assertThat(survivingSnapshot.items().get(0).brand()).isEqualTo("Tom Ford");
        assertThat(survivingSnapshot.items().get(0).originalStorageKey()).isEqualTo("wardrobe/silk.jpg");

        // 8. Verify wear history query can still display historical outfit from its snapshot
        Page<OutfitWearHistoryDto> historyPage = outfitPersistenceService.getWearHistory(user.getId(), PageRequest.of(0, 10));
        assertThat(historyPage.getContent()).hasSize(1);

        OutfitWearHistoryDto historyRecord = historyPage.getContent().get(0);
        assertThat(historyRecord.outfitId()).isEqualTo(outfitId); // Extracted from snapshot
        assertThat(historyRecord.outfitName()).isEqualTo("Anniversary Dinner");
        assertThat(historyRecord.occasion()).isEqualTo(Occasion.DATE_NIGHT);
        assertThat(historyRecord.suitabilityScore()).isEqualTo(94);
        assertThat(historyRecord.items()).hasSize(1);
        assertThat(historyRecord.items().get(0).name()).isEqualTo("Silk Blend Shirt");
        assertThat(historyRecord.items().get(0).primaryColor()).isEqualTo(Color.BLACK);
    }

    @Test
    @DisplayName("Test 4 — JSONB serialization and deserialization across optional and empty fields")
    void testJsonbPersistenceAndDeserialization() {
        User user = createTestUser("user_jsonb_serialization");

        // Snapshot 1: Fully populated fields
        WearLogSnapshot fullSnapshot = new WearLogSnapshot(
            1,
            UUID.randomUUID(),
            "Complete Outfit",
            Occasion.FORMAL,
            TemperatureBracket.HOT,
            WeatherCondition.SUNNY,
            "Formal linen wedding ensemble",
            98,
            ScoreStatus.CALCULATED,
            List.of(
                new WearLogSnapshot.WearLogItemSnapshot(
                    UUID.randomUUID(), "TOP", Category.TOP, "Linen Shirt",
                    Color.WHITE, Color.BEIGE, Pattern.SOLID, "100% Linen", "Loro Piana", "storage/linen.jpg"
                )
            )
        );

        // Snapshot 2: Null optional fields (temperature, weather, secondaryColor, brand, score)
        WearLogSnapshot partialSnapshot = new WearLogSnapshot(
            1,
            UUID.randomUUID(),
            null, // null name
            Occasion.CASUAL,
            null, // null temperatureBracket
            null, // null weatherCondition
            "Custom curated casual outfit",
            null, // null score
            ScoreStatus.CUSTOM,
            List.of(
                new WearLogSnapshot.WearLogItemSnapshot(
                    UUID.randomUUID(), "BOTTOM", Category.BOTTOM, "Denim Jeans",
                    Color.BLUE, null, Pattern.SOLID, null, null, null
                )
            )
        );

        // Snapshot 3: Empty item collection
        WearLogSnapshot emptyItemsSnapshot = new WearLogSnapshot(
            1,
            UUID.randomUUID(),
            "Minimalist",
            Occasion.LOUNGEWEAR,
            TemperatureBracket.COLD,
            WeatherCondition.RAINY,
            "Lounge wear",
            75,
            ScoreStatus.CALCULATED,
            List.of() // empty items
        );

        OutfitWearLog log1 = outfitWearLogRepository.save(OutfitWearLog.builder()
            .user(user).wornDate(LocalDate.now()).notes("Full snapshot").snapshot(fullSnapshot).build());

        OutfitWearLog log2 = outfitWearLogRepository.save(OutfitWearLog.builder()
            .user(user).wornDate(LocalDate.now().minusDays(1)).notes("Partial snapshot").snapshot(partialSnapshot).build());

        OutfitWearLog log3 = outfitWearLogRepository.save(OutfitWearLog.builder()
            .user(user).wornDate(LocalDate.now().minusDays(2)).notes("Empty items snapshot").snapshot(emptyItemsSnapshot).build());

        entityManager.clear();

        // 1. Verify log 1 deserialization
        OutfitWearLog retrieved1 = outfitWearLogRepository.findById(log1.getId()).orElseThrow();
        WearLogSnapshot s1 = retrieved1.getSnapshot();
        assertThat(s1).isNotNull();
        assertThat(s1.version()).isEqualTo(1);
        assertThat(s1.name()).isEqualTo("Complete Outfit");
        assertThat(s1.occasion()).isEqualTo(Occasion.FORMAL);
        assertThat(s1.temperatureBracket()).isEqualTo(TemperatureBracket.HOT);
        assertThat(s1.weatherCondition()).isEqualTo(WeatherCondition.SUNNY);
        assertThat(s1.suitabilityScore()).isEqualTo(98);
        assertThat(s1.scoreStatus()).isEqualTo(ScoreStatus.CALCULATED);
        assertThat(s1.items()).hasSize(1);
        assertThat(s1.items().get(0).brand()).isEqualTo("Loro Piana");
        assertThat(s1.items().get(0).fabric()).isEqualTo("100% Linen");

        // 2. Verify log 2 deserialization with null optional fields
        OutfitWearLog retrieved2 = outfitWearLogRepository.findById(log2.getId()).orElseThrow();
        WearLogSnapshot s2 = retrieved2.getSnapshot();
        assertThat(s2).isNotNull();
        assertThat(s2.version()).isEqualTo(1);
        assertThat(s2.name()).isNull();
        assertThat(s2.temperatureBracket()).isNull();
        assertThat(s2.weatherCondition()).isNull();
        assertThat(s2.suitabilityScore()).isNull();
        assertThat(s2.scoreStatus()).isEqualTo(ScoreStatus.CUSTOM);
        assertThat(s2.items()).hasSize(1);
        assertThat(s2.items().get(0).secondaryColor()).isNull();
        assertThat(s2.items().get(0).brand()).isNull();
        assertThat(s2.items().get(0).fabric()).isNull();
        assertThat(s2.items().get(0).originalStorageKey()).isNull();

        // 3. Verify log 3 deserialization with empty items collection
        OutfitWearLog retrieved3 = outfitWearLogRepository.findById(log3.getId()).orElseThrow();
        WearLogSnapshot s3 = retrieved3.getSnapshot();
        assertThat(s3).isNotNull();
        assertThat(s3.version()).isEqualTo(1);
        assertThat(s3.items()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Test 5 — Real PostgreSQL 16 migration V1 -> V2 -> V3 execution and historical backfill")
    void testFlywayV1ToV2ToV3MigrationAndHistoricalBackfill() throws Exception {
        try (PostgreSQLContainer<?> freshPostgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("groomai_backfill_test")
                .withUsername("test_user")
                .withPassword("test_password")) {

            freshPostgres.start();

            // 1. Migrate V1 and V2 to recreate legacy Phase 2 state
            Flyway flywayV2 = Flyway.configure()
                .dataSource(freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword())
                .locations("classpath:db/migration")
                .target("2")
                .load();

            int v2Executed = flywayV2.migrate().migrationsExecuted;
            assertThat(v2Executed).isEqualTo(2);

            // 2. Seed legacy pre-V3 data into V2 tables
            UUID userId = UUID.randomUUID();
            UUID activeItemId = UUID.randomUUID();
            UUID archivedItemId = UUID.randomUUID();
            UUID legacyOutfitId = UUID.randomUUID();
            UUID outfitItemId1 = UUID.randomUUID();
            UUID outfitItemId2 = UUID.randomUUID();
            UUID legacyWearLogId = UUID.randomUUID();

            try (Connection conn = DriverManager.getConnection(freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword());
                 Statement stmt = conn.createStatement()) {

                // Insert user
                stmt.executeUpdate("INSERT INTO users (id, email, password_hash, role) VALUES ('"
                    + userId + "', 'legacy_user@example.com', 'hash', 'ROLE_USER')");

                // Insert ACTIVE wardrobe item
                stmt.executeUpdate("INSERT INTO wardrobe_items (id, user_id, name, category, subcategory, primary_color, secondary_color, pattern, storage_key, status) "
                    + "VALUES ('" + activeItemId + "', '" + userId + "', 'Active Shirt', 'TOP', 'Button-Down', 'WHITE', 'BLUE', 'STRIPED', 'key_active.jpg', 'ACTIVE')");

                // Insert ARCHIVED wardrobe item
                stmt.executeUpdate("INSERT INTO wardrobe_items (id, user_id, name, category, subcategory, primary_color, secondary_color, pattern, storage_key, status) "
                    + "VALUES ('" + archivedItemId + "', '" + userId + "', 'Archived Pants', 'BOTTOM', 'Chinos', 'NAVY', 'GREY', 'SOLID', 'key_archived.jpg', 'ARCHIVED')");

                // Insert legacy outfit with unverified score = 88 and styling_rationale (pre-V3 schema without name or score_status)
                stmt.executeUpdate("INSERT INTO outfits (id, user_id, occasion, styling_rationale, suitability_score) "
                    + "VALUES ('" + legacyOutfitId + "', '" + userId + "', 'SMART_CASUAL', 'Legacy styling rationale', 88)");

                // Insert legacy outfit_items (pre-V3 schema without snapshot columns)
                stmt.executeUpdate("INSERT INTO outfit_items (id, outfit_id, wardrobe_item_id, item_role) "
                    + "VALUES ('" + outfitItemId1 + "', '" + legacyOutfitId + "', '" + activeItemId + "', 'TOP')");
                stmt.executeUpdate("INSERT INTO outfit_items (id, outfit_id, wardrobe_item_id, item_role) "
                    + "VALUES ('" + outfitItemId2 + "', '" + legacyOutfitId + "', '" + archivedItemId + "', 'BOTTOM')");

                // Insert legacy outfit_wear_log (pre-V3 schema without snapshot column)
                stmt.executeUpdate("INSERT INTO outfit_wear_logs (id, user_id, outfit_id, worn_date, notes) "
                    + "VALUES ('" + legacyWearLogId + "', '" + userId + "', '" + legacyOutfitId + "', '2026-05-10', 'Legacy wear event note')");
            }

            // 3. Run Flyway V3 migration on top of existing Phase 2 database
            Flyway flywayV3 = Flyway.configure()
                .dataSource(freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load();

            int v3Executed = flywayV3.migrate().migrationsExecuted;
            assertThat(v3Executed).isEqualTo(1);

            // 4. Verify post-migration database state
            try (Connection conn = DriverManager.getConnection(freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword());
                 Statement stmt = conn.createStatement()) {

                // A. Verify partial unique index exists in pg_indexes
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'outfit_wear_logs' AND indexname = 'uq_outfit_wear_logs_user_outfit_date'")) {
                    assertThat(rs.next()).isTrue();
                }

                // B. Verify unverified legacy score was invalidated to UNSCORED and NULL, with no fabricated name
                try (ResultSet rs = stmt.executeQuery("SELECT name, suitability_score, score_status FROM outfits WHERE id = '" + legacyOutfitId + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("name")).isNull(); // No fabricated name
                    assertThat(rs.getObject("suitability_score")).isNull(); // Unverified score invalidated
                    assertThat(rs.getString("score_status")).isEqualTo("UNSCORED");
                }

                // C. Verify ARCHIVED wardrobe item was correctly included in garment metadata backfill
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT category, subcategory, primary_color, secondary_color, pattern, original_storage_key "
                    + "FROM outfit_items WHERE id = '" + outfitItemId2 + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("category")).isEqualTo("BOTTOM");
                    assertThat(rs.getString("subcategory")).isEqualTo("Chinos");
                    assertThat(rs.getString("primary_color")).isEqualTo("NAVY");
                    assertThat(rs.getString("secondary_color")).isEqualTo("GREY");
                    assertThat(rs.getString("pattern")).isEqualTo("SOLID");
                    assertThat(rs.getString("original_storage_key")).isEqualTo("key_archived.jpg");
                }

                // D. Verify ACTIVE wardrobe item was also correctly backfilled
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT category, subcategory, primary_color, original_storage_key "
                    + "FROM outfit_items WHERE id = '" + outfitItemId1 + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("category")).isEqualTo("TOP");
                    assertThat(rs.getString("subcategory")).isEqualTo("Button-Down");
                    assertThat(rs.getString("primary_color")).isEqualTo("WHITE");
                    assertThat(rs.getString("original_storage_key")).isEqualTo("key_active.jpg");
                }

                // E. Verify historical wear log received a populated snapshot
                try (ResultSet rs = stmt.executeQuery("SELECT snapshot FROM outfit_wear_logs WHERE id = '" + legacyWearLogId + "'")) {
                    assertThat(rs.next()).isTrue();
                    String snapshotJson = rs.getString("snapshot");
                    assertThat(snapshotJson).isNotNull();
                    assertThat(snapshotJson).contains("\"version\": 1");
                    assertThat(snapshotJson).contains("SMART_CASUAL");
                    assertThat(snapshotJson).contains("key_archived.jpg");
                    assertThat(snapshotJson).contains("key_active.jpg");
                }
            }
        }
    }
}
