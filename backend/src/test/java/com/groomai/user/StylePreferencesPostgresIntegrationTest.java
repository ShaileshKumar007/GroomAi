package com.groomai.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.user.dto.PatchStylePreferencesRequestDto;
import com.groomai.user.dto.StylePreferencesResponseDto;
import com.groomai.user.dto.UpdateStylePreferencesRequestDto;
import com.groomai.user.entity.StylePreferences;
import com.groomai.user.entity.User;
import com.groomai.user.enums.PreferredFit;
import com.groomai.user.enums.PrimaryStyle;
import com.groomai.user.enums.StyleGoal;
import com.groomai.user.repository.StylePreferencesRepository;
import com.groomai.user.repository.UserRepository;
import com.groomai.user.service.StylePreferencesService;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
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
 * PostgreSQL 16 Testcontainers integration test for Style Profile Foundation (Stage 4C.3.1).
 * Verifies V1 -> V2 -> V3 -> V4 Flyway migrations, JSONB serialization, GET defaults,
 * PUT/PATCH semantics, user isolation, and first-time concurrent creation safety.
 */
@SpringBootTest
@EnabledIf("isDockerAvailable")
class StylePreferencesPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(StylePreferencesPostgresIntegrationTest.class);

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("groomai_style_test")
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
                log.info("Docker is not available; skipping PostgreSQL Testcontainers integration tests.");
            }
            return available;
        } catch (Throwable t) {
            log.info("Docker is not available ({}); skipping PostgreSQL Testcontainers integration tests.", t.getMessage());
            return false;
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StylePreferencesRepository stylePreferencesRepository;

    @Autowired
    private StylePreferencesService stylePreferencesService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private User createTestUser(String prefix) {
        User user = User.builder()
            .email(prefix + "_" + UUID.randomUUID() + "@example.com")
            .passwordHash("$2a$12$hashedPasswordPlaceholderForTesting12345")
            .role("ROLE_USER")
            .build();
        return userRepository.save(user);
    }

    @Test
    @DisplayName("Test 1 — Flyway migration sequence V1 -> V2 -> V3 -> V4 on fresh PostgreSQL container")
    void testFlywayMigrationSequenceAndDefaults() throws Exception {
        try (PostgreSQLContainer<?> freshPostgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("groomai_fresh_migration_test")
            .withUsername("fresh_user")
            .withPassword("fresh_password")) {

            freshPostgres.start();

            // 1. Run migrations up to V3
            Flyway flywayV3 = Flyway.configure()
                .dataSource(freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load();

            int v3Executed = flywayV3.migrate().migrationsExecuted;
            assertThat(v3Executed).isEqualTo(3);

            // 2. Seed pre-V4 legacy style_preferences record into V2/V3 schema
            UUID legacyUserId = UUID.randomUUID();
            UUID legacyPrefId = UUID.randomUUID();

            try (Connection conn = DriverManager.getConnection(freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword());
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate("INSERT INTO users (id, email, password_hash, role) VALUES ('"
                    + legacyUserId + "', 'legacy_profile_user@example.com', 'hash', 'ROLE_USER')");

                stmt.executeUpdate("INSERT INTO style_preferences (id, user_id, primary_style, preferred_fit, default_formality, color_preferences, avoided_colors) "
                    + "VALUES ('" + legacyPrefId + "', '" + legacyUserId + "', 'MINIMALIST', 'SLIM', 'CASUAL', '[\"BLACK\", \"WHITE\"]'::jsonb, '[\"RED\"]'::jsonb)");
            }

            // 3. Run Flyway V4 migration
            Flyway flywayV4 = Flyway.configure()
                .dataSource(freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword())
                .locations("classpath:db/migration")
                .target("4")
                .load();

            int v4Executed = flywayV4.migrate().migrationsExecuted;
            assertThat(v4Executed).isEqualTo(1);

            // 4. Verify post-migration column state and defaults
            try (Connection conn = DriverManager.getConnection(freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword());
                 Statement stmt = conn.createStatement()) {

                try (ResultSet rs = stmt.executeQuery(
                    "SELECT profile_completed, pattern_preferences, avoided_patterns, style_goals, color_preferences, avoided_colors "
                    + "FROM style_preferences WHERE id = '" + legacyPrefId + "'")) {

                    assertThat(rs.next()).isTrue();
                    // profile_completed defaults to false
                    assertThat(rs.getBoolean("profile_completed")).isFalse();
                    // existing JSONB arrays intact
                    assertThat(rs.getString("color_preferences")).contains("BLACK").contains("WHITE");
                    assertThat(rs.getString("avoided_colors")).contains("RED");
                    // new JSONB arrays default to empty array []
                    assertThat(rs.getString("pattern_preferences")).isEqualTo("[]");
                    assertThat(rs.getString("avoided_patterns")).isEqualTo("[]");
                    assertThat(rs.getString("style_goals")).isEqualTo("[]");
                }
            }
        }
    }

    @Test
    @DisplayName("Test 2 — GET behavior: unconfigured returns defaults without mutating database, configured returns stored row")
    void testGetBehavior() {
        User unconfiguredUser = createTestUser("unconf");
        User configuredUser = createTestUser("conf");

        // Unconfigured user: GET returns defaults with profileCompleted=false
        StylePreferencesResponseDto unconfiguredDto = stylePreferencesService.getStylePreferences(unconfiguredUser.getId());
        assertThat(unconfiguredDto.profileCompleted()).isFalse();
        assertThat(unconfiguredDto.primaryStyle()).isEqualTo(PrimaryStyle.CASUAL);
        assertThat(unconfiguredDto.preferredFit()).isEqualTo(PreferredFit.REGULAR);
        assertThat(unconfiguredDto.defaultFormality()).isEqualTo(FormalityLevel.CASUAL);
        assertThat(unconfiguredDto.colorPreferences()).isEmpty();

        // Verify no row was inserted into style_preferences by the GET operation
        assertThat(stylePreferencesRepository.findByUserId(unconfiguredUser.getId())).isEmpty();

        // Configured user: PUT sets profileCompleted=true
        UpdateStylePreferencesRequestDto putRequest = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.SMART_CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.NAVY, Color.WHITE),
            List.of(Color.YELLOW),
            List.of(Pattern.SOLID),
            List.of(Pattern.FLORAL),
            FormalityLevel.SMART_CASUAL,
            List.of(StyleGoal.LOOK_SHARPER)
        );
        stylePreferencesService.updateStylePreferences(configuredUser.getId(), putRequest);

        // Subsequent GET returns stored row with profileCompleted=true
        StylePreferencesResponseDto configuredDto = stylePreferencesService.getStylePreferences(configuredUser.getId());
        assertThat(configuredDto.profileCompleted()).isTrue();
        assertThat(configuredDto.primaryStyle()).isEqualTo(PrimaryStyle.SMART_CASUAL);
        assertThat(configuredDto.colorPreferences()).containsExactly(Color.NAVY, Color.WHITE);
        assertThat(configuredDto.avoidedColors()).containsExactly(Color.YELLOW);
        assertThat(configuredDto.patternPreferences()).containsExactly(Pattern.SOLID);
        assertThat(configuredDto.avoidedPatterns()).containsExactly(Pattern.FLORAL);
        assertThat(configuredDto.styleGoals()).containsExactly(StyleGoal.LOOK_SHARPER);
    }

    @Test
    @DisplayName("Test 3 — PUT and PATCH persistence, JSONB column serialization, and array clearing")
    void testPutAndPatchPersistence() {
        User user = createTestUser("put_patch");

        // 1. PUT creates new row with profileCompleted=true
        UpdateStylePreferencesRequestDto putRequest = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CLASSIC,
            PreferredFit.REGULAR,
            List.of(Color.NAVY, Color.BROWN, Color.BEIGE),
            List.of(Color.MULTI),
            List.of(Pattern.SOLID, Pattern.STRIPED),
            List.of(Pattern.GRAPHIC),
            FormalityLevel.SMART_CASUAL,
            List.of(StyleGoal.LOOK_SHARPER, StyleGoal.EXPAND_ROTATION)
        );

        StylePreferencesResponseDto putResponse = stylePreferencesService.updateStylePreferences(user.getId(), putRequest);
        assertThat(putResponse.profileCompleted()).isTrue();
        assertThat(putResponse.primaryStyle()).isEqualTo(PrimaryStyle.CLASSIC);

        // Verify JSONB columns in PostgreSQL directly
        String rawJsonb = jdbcTemplate.queryForObject(
            "SELECT pattern_preferences::text FROM style_preferences WHERE user_id = ?",
            (rs, rowNum) -> rs.getString(1),
            user.getId()
        );
        assertThat(rawJsonb).contains("SOLID").contains("STRIPED");

        // 2. PATCH updates only preferredFit, preserves profileCompleted=true
        PatchStylePreferencesRequestDto patch1 = new PatchStylePreferencesRequestDto(
            null,
            PreferredFit.SLIM,
            null,
            null,
            null,
            null,
            null,
            null
        );
        StylePreferencesResponseDto patchResponse1 = stylePreferencesService.patchStylePreferences(user.getId(), patch1);
        assertThat(patchResponse1.profileCompleted()).isTrue();
        assertThat(patchResponse1.preferredFit()).isEqualTo(PreferredFit.SLIM);
        assertThat(patchResponse1.primaryStyle()).isEqualTo(PrimaryStyle.CLASSIC);
        assertThat(patchResponse1.colorPreferences()).containsExactly(Color.NAVY, Color.BROWN, Color.BEIGE);

        // 3. PATCH with empty array clears colorPreferences
        PatchStylePreferencesRequestDto patch2 = new PatchStylePreferencesRequestDto(
            null,
            null,
            List.of(), // clear colors
            null,
            null,
            null,
            null,
            null
        );
        StylePreferencesResponseDto patchResponse2 = stylePreferencesService.patchStylePreferences(user.getId(), patch2);
        assertThat(patchResponse2.profileCompleted()).isTrue();
        assertThat(patchResponse2.colorPreferences()).isEmpty();
        assertThat(patchResponse2.avoidedColors()).containsExactly(Color.MULTI);
    }

    @Test
    @DisplayName("Test 4 — Tenancy isolation: users cannot observe or mutate each other's preferences")
    void testTenancyIsolation() {
        User userA = createTestUser("user_a");
        User userB = createTestUser("user_b");

        UpdateStylePreferencesRequestDto putA = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.MINIMALIST,
            PreferredFit.SLIM,
            List.of(Color.BLACK),
            List.of(Color.YELLOW),
            List.of(Pattern.SOLID),
            List.of(Pattern.FLORAL),
            FormalityLevel.CASUAL,
            List.of(StyleGoal.MINIMAL_EFFORT)
        );
        stylePreferencesService.updateStylePreferences(userA.getId(), putA);

        // User B's profile remains unconfigured and unaffected
        StylePreferencesResponseDto bDto = stylePreferencesService.getStylePreferences(userB.getId());
        assertThat(bDto.profileCompleted()).isFalse();
        assertThat(bDto.primaryStyle()).isEqualTo(PrimaryStyle.CASUAL);
        assertThat(bDto.colorPreferences()).isEmpty();

        // Mutating user B does not affect user A
        UpdateStylePreferencesRequestDto putB = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.STREETWEAR,
            PreferredFit.OVERSIZED,
            List.of(Color.RED),
            List.of(Color.WHITE),
            List.of(Pattern.GRAPHIC),
            List.of(Pattern.CHECKED),
            FormalityLevel.CASUAL,
            List.of(StyleGoal.TRY_NEW_COLORS)
        );
        stylePreferencesService.updateStylePreferences(userB.getId(), putB);

        StylePreferencesResponseDto aDto = stylePreferencesService.getStylePreferences(userA.getId());
        assertThat(aDto.primaryStyle()).isEqualTo(PrimaryStyle.MINIMALIST);
        assertThat(aDto.preferredFit()).isEqualTo(PreferredFit.SLIM);
        assertThat(aDto.colorPreferences()).containsExactly(Color.BLACK);
    }

    @Test
    @DisplayName("Test 5 — Concurrent first-time profile creation safety across 10 threads")
    void testConcurrentFirstTimeProfileCreation() throws Exception {
        User user = createTestUser("concurrent_profile_user");
        int concurrency = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CyclicBarrier barrier = new CyclicBarrier(concurrency);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);

                    UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
                        PrimaryStyle.CASUAL,
                        PreferredFit.REGULAR,
                        List.of(Color.NAVY),
                        List.of(Color.YELLOW),
                        List.of(Pattern.SOLID),
                        List.of(Pattern.FLORAL),
                        FormalityLevel.CASUAL,
                        List.of(StyleGoal.LOOK_SHARPER)
                    );

                    StylePreferencesResponseDto result = stylePreferencesService.updateStylePreferences(user.getId(), request);
                    if (result != null && result.profileCompleted()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Thread {} failed during concurrent profile creation", index, e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // All 10 competing threads succeed without failing
        assertThat(successCount.get()).isEqualTo(concurrency);

        // Exactly 1 row persisted in database for this user
        Integer rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM style_preferences WHERE user_id = ?",
            Integer.class,
            user.getId()
        );
        assertThat(rowCount).isEqualTo(1);

        // Final stored record is complete and valid
        Optional<StylePreferences> stored = stylePreferencesRepository.findByUserId(user.getId());
        assertThat(stored).isPresent();
        assertThat(stored.get().isProfileCompleted()).isTrue();
    }
}
