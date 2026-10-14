package com.groomai.auth;

import com.groomai.auth.dto.AuthResponse;
import com.groomai.auth.dto.LoginRequest;
import com.groomai.auth.dto.LogoutRequest;
import com.groomai.auth.dto.RefreshTokenRequest;
import com.groomai.auth.dto.RegisterRequest;
import com.groomai.auth.dto.TokenRefreshResponse;
import com.groomai.auth.dto.UserSummaryResponse;
import com.groomai.auth.entity.RefreshToken;
import com.groomai.auth.exception.EmailAlreadyExistsException;
import com.groomai.auth.exception.InvalidCredentialsException;
import com.groomai.auth.exception.InvalidTokenException;
import com.groomai.auth.repository.RefreshTokenRepository;
import com.groomai.auth.security.JwtProperties;
import com.groomai.auth.security.JwtService;
import com.groomai.auth.security.TokenHashUtil;
import com.groomai.auth.service.AuthServiceImpl;
import com.groomai.user.entity.User;
import com.groomai.user.entity.UserProfile;
import com.groomai.user.repository.UserProfileRepository;
import com.groomai.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full end-to-end integration test verifying authentication, persistence,
 * uniqueness constraints, token rotation, and reuse defense on fresh PostgreSQL 16.
 */
class AuthPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AuthPostgresIntegrationTest.class);

    private static PostgreSQLContainer<?> postgres;
    private static DataSource dataSource;
    private static LocalContainerEntityManagerFactoryBean emf;

    private UserRepository userRepository;
    private UserProfileRepository userProfileRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthServiceImpl authService;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker is not available locally. Integration test runs in CI.");
            return false;
        }
    }

    @BeforeAll
    static void startContainer() {
        if (!isDockerAvailable()) return;

        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("groomai_test")
            .withUsername("test_user")
            .withPassword("test_password");
        postgres.start();

        dataSource = DataSourceBuilder.create()
            .driverClassName(postgres.getDriverClassName())
            .url(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .build();

        // Run Flyway migrations
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        if (!isDockerAvailable()) return;

        passwordEncoder = new BCryptPasswordEncoder(10);
        JwtProperties jwtProperties = new JwtProperties(
            "postgres-integration-test-secret-must-be-at-least-256-bits-long-key!",
            900000L,
            2592000000L
        );
        jwtService = new JwtService(jwtProperties);

        // Standalone JPA initialization for fast, targeted Testcontainers verification
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(false);

        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPackagesToScan("com.groomai.user.entity", "com.groomai.auth.entity");
        factoryBean.setJpaVendorAdapter(vendorAdapter);

        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        factoryBean.setJpaProperties(jpaProperties);
        factoryBean.afterPropertiesSet();

        var em = factoryBean.getObject().createEntityManager();
        var jpaFactory = new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em);

        userRepository = jpaFactory.getRepository(UserRepository.class);
        userProfileRepository = jpaFactory.getRepository(UserProfileRepository.class);
        refreshTokenRepository = jpaFactory.getRepository(RefreshTokenRepository.class);

        authService = new AuthServiceImpl(
            userRepository,
            userProfileRepository,
            refreshTokenRepository,
            passwordEncoder,
            jwtService
        );
    }

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("PostgreSQL: register creates User and UserProfile, hashes password with BCrypt")
    void postgres_register_shouldPersistUserAndProfile() {
        String email = "pg_user_" + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest(email, "Password123!", "Postgres User");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isNotNull();
        assertThat(response.email()).isEqualTo(email);
        assertThat(response.displayName()).isEqualTo("Postgres User");
        assertThat(response.accessToken()).isNotEmpty();
        assertThat(response.refreshToken()).startsWith("rt_");

        // Verify database persistence in PostgreSQL
        Optional<User> persistedUser = userRepository.findById(response.userId());
        assertThat(persistedUser).isPresent();
        assertThat(persistedUser.get().getEmail()).isEqualTo(email);
        assertThat(persistedUser.get().getPasswordHash()).startsWith("$2a$"); // BCrypt prefix
        assertThat(passwordEncoder.matches("Password123!", persistedUser.get().getPasswordHash())).isTrue();

        Optional<UserProfile> persistedProfile = userProfileRepository.findByUserId(response.userId());
        assertThat(persistedProfile).isPresent();
        assertThat(persistedProfile.get().getDisplayName()).isEqualTo("Postgres User");
    }

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("PostgreSQL: duplicate email registration is rejected by unique constraint")
    void postgres_register_shouldRejectDuplicateEmail() {
        String email = "duplicate_" + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest(email, "Password123!", "First User");
        authService.register(request);

        // Attempting second registration with same email
        RegisterRequest duplicateRequest = new RegisterRequest(email, "Password456!", "Second User");
        assertThatThrownBy(() -> authService.register(duplicateRequest))
            .isInstanceOf(EmailAlreadyExistsException.class)
            .hasMessageContaining(email);
    }

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("PostgreSQL: login succeeds with valid password and rejects invalid password")
    void postgres_login_shouldAuthenticateAndIssueTokens() {
        String email = "login_test_" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "CorrectPassword123!", "Login User"));

        // Valid login
        AuthResponse loginResponse = authService.login(new LoginRequest(email, "CorrectPassword123!"));
        assertThat(loginResponse.email()).isEqualTo(email);
        assertThat(loginResponse.accessToken()).isNotEmpty();

        // Invalid password
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPassword123!")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("PostgreSQL: token rotation revokes old refresh token and detects reuse")
    void postgres_refresh_rotationAndReuseDefense() {
        String email = "refresh_test_" + UUID.randomUUID() + "@example.com";
        AuthResponse registerResponse = authService.register(new RegisterRequest(email, "SecurePass123!", "Refresh User"));
        String originalRefreshToken = registerResponse.refreshToken();

        // First rotation: valid
        TokenRefreshResponse rotateResponse = authService.refresh(new RefreshTokenRequest(originalRefreshToken));
        assertThat(rotateResponse.accessToken()).isNotEmpty();
        assertThat(rotateResponse.refreshToken()).isNotEqualTo(originalRefreshToken);

        // Verify old token is revoked in database
        String oldHash = TokenHashUtil.hashToken(originalRefreshToken);
        Optional<RefreshToken> oldTokenDb = refreshTokenRepository.findByTokenHash(oldHash);
        assertThat(oldTokenDb).isPresent();
        assertThat(oldTokenDb.get().isRevoked()).isTrue();

        // Second rotation using already-rotated token -> REUSE DETECTION TRIGGERED
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(originalRefreshToken)))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("already been revoked or rotated");
    }

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("PostgreSQL: logout revokes refresh token so it cannot be used again")
    void postgres_logout_shouldRevokeRefreshToken() {
        String email = "logout_test_" + UUID.randomUUID() + "@example.com";
        AuthResponse registerResponse = authService.register(new RegisterRequest(email, "SecurePass123!", "Logout User"));
        String refreshToken = registerResponse.refreshToken();

        authService.logout(registerResponse.userId(), new LogoutRequest(refreshToken));

        // Attempting to refresh after logout
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(refreshToken)))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("PostgreSQL: /auth/me returns safe profile information for the authenticated user only")
    void postgres_getMe_shouldReturnUserProfile() {
        String email = "me_test_" + UUID.randomUUID() + "@example.com";
        AuthResponse registerResponse = authService.register(new RegisterRequest(email, "SecurePass123!", "Me User"));

        UserSummaryResponse meResponse = authService.getCurrentUser(registerResponse.userId());

        assertThat(meResponse.userId()).isEqualTo(registerResponse.userId());
        assertThat(meResponse.email()).isEqualTo(email);
        assertThat(meResponse.displayName()).isEqualTo("Me User");
        assertThat(meResponse.createdAt()).isNotNull();
    }
}
