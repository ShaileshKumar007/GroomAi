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
import com.groomai.auth.security.TokenHashUtil;
import com.groomai.auth.service.AuthService;
import com.groomai.user.entity.User;
import com.groomai.user.entity.UserProfile;
import com.groomai.user.repository.UserProfileRepository;
import com.groomai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full end-to-end integration test verifying authentication, persistence,
 * uniqueness constraints, token rotation, and reuse defense on fresh PostgreSQL 16.
 * Uses @SpringBootTest with Testcontainers and Flyway migrations.
 */
@SpringBootTest
@EnabledIf("isDockerAvailable")
class AuthPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AuthPostgresIntegrationTest.class);

    static final PostgreSQLContainer<?> postgres;

    static {
        if (isDockerAvailable()) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("groomai_test")
                .withUsername("test_user")
                .withPassword("test_password");
            postgres.start();
        } else {
            postgres = null;
        }
    }

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker is not available in local environment. Test runs in CI.");
            return false;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres != null) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
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
