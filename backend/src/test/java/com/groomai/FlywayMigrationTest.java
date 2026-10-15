package com.groomai;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Flyway V1__initial_schema.sql and V2__wardrobe_schema.sql
 * execute cleanly on a fresh PostgreSQL 16 instance, and that incremental migration
 * cleanly upgrades an existing Phase 1 database to Phase 2.
 * Automatically runs when Docker is present (e.g., in GitHub Actions CI).
 */
class FlywayMigrationTest {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationTest.class);

    private static final String[] EXPECTED_V1_TABLES = {
        "users",
        "user_profiles",
        "grooming_preferences",
        "refresh_tokens",
        "password_reset_tokens",
        "photo_assets",
        "storage_deletion_outbox",
        "recommendation_jobs",
        "recommendations",
        "recommendation_options",
        "saved_recommendations",
        "grooming_plans",
        "grooming_plan_items"
    };

    private static final String[] EXPECTED_V2_TABLES = {
        "users",
        "user_profiles",
        "grooming_preferences",
        "refresh_tokens",
        "password_reset_tokens",
        "photo_assets",
        "storage_deletion_outbox",
        "recommendation_jobs",
        "recommendations",
        "recommendation_options",
        "saved_recommendations",
        "grooming_plans",
        "grooming_plan_items",
        "style_preferences",
        "wardrobe_items",
        "outfits",
        "outfit_items",
        "outfit_wear_logs"
    };

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

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("Fresh database: V1 and V2 migrations execute cleanly and create all 18 tables")
    void testFlywayMigrationOnFreshPostgres() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("groomai_test")
                .withUsername("test_user")
                .withPassword("test_password")) {

            postgres.start();

            Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

            int migrationsExecuted = flyway.migrate().migrationsExecuted;
            assertThat(migrationsExecuted).isEqualTo(2);

            List<String> tables = queryBaseTables(postgres);
            assertThat(tables).containsExactlyInAnyOrder(EXPECTED_V2_TABLES);

            // Verify user_profiles columns added in V2
            List<String> userProfileColumns = queryColumns(postgres, "user_profiles");
            assertThat(userProfileColumns).contains(
                "wardrobe_storage_consent_granted",
                "wardrobe_storage_consent_granted_at",
                "ai_styling_consent_granted",
                "ai_styling_consent_granted_at"
            );
        }
    }

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("Incremental migration: V2 successfully applies on top of existing Phase 1 database")
    void testFlywayIncrementalMigrationFromPhase1() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("groomai_test")
                .withUsername("test_user")
                .withPassword("test_password")) {

            postgres.start();

            // 1. Migrate V1 only (representing existing Phase 1 database)
            Flyway flywayV1 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("1")
                .load();

            int v1Migrations = flywayV1.migrate().migrationsExecuted;
            assertThat(v1Migrations).isEqualTo(1);

            List<String> v1Tables = queryBaseTables(postgres);
            assertThat(v1Tables).containsExactlyInAnyOrder(EXPECTED_V1_TABLES);

            // 2. Migrate V2 on top of existing Phase 1 database
            Flyway flywayV2 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("2")
                .load();

            int v2Migrations = flywayV2.migrate().migrationsExecuted;
            assertThat(v2Migrations).isEqualTo(1);

            List<String> upgradedTables = queryBaseTables(postgres);
            assertThat(upgradedTables).containsExactlyInAnyOrder(EXPECTED_V2_TABLES);

            // Verify altered user_profiles table structure
            List<String> userProfileColumns = queryColumns(postgres, "user_profiles");
            assertThat(userProfileColumns).contains(
                "wardrobe_storage_consent_granted",
                "wardrobe_storage_consent_granted_at",
                "ai_styling_consent_granted",
                "ai_styling_consent_granted_at"
            );
        }
    }

    private List<String> queryBaseTables(PostgreSQLContainer<?> postgres) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name != 'flyway_schema_history' ORDER BY table_name")) {

            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }
        }
        return tables;
    }

    private List<String> queryColumns(PostgreSQLContainer<?> postgres, String tableName) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '" + tableName + "' ORDER BY column_name")) {

            while (rs.next()) {
                columns.add(rs.getString("column_name"));
            }
        }
        return columns;
    }
}
