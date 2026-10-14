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
 * Verifies that Flyway V1__initial_schema.sql executes cleanly on a fresh PostgreSQL 16 instance.
 * Automatically runs when Docker is present (e.g., in GitHub Actions CI).
 */
class FlywayMigrationTest {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationTest.class);

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
    @DisplayName("V1 migration should execute cleanly on fresh PostgreSQL 16 and create all 13 tables")
    void testFlywayMigrationOnPostgres() throws Exception {
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
            assertThat(migrationsExecuted).isEqualTo(1);

            // Verify all 13 tables exist in public schema
            List<String> tables = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name != 'flyway_schema_history' ORDER BY table_name")) {

                while (rs.next()) {
                    tables.add(rs.getString("table_name"));
                }
            }

            assertThat(tables).containsExactlyInAnyOrder(
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
            );
        }
    }
}
