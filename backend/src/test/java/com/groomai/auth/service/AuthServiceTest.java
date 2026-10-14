package com.groomai.auth.service;

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
import com.groomai.auth.exception.TokenExpiredException;
import com.groomai.auth.repository.RefreshTokenRepository;
import com.groomai.auth.security.JwtProperties;
import com.groomai.auth.security.JwtService;
import com.groomai.auth.security.TokenHashUtil;
import com.groomai.user.entity.User;
import com.groomai.user.entity.UserProfile;
import com.groomai.user.repository.UserProfileRepository;
import com.groomai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
            "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256-signing!",
            900000L,     // 15 min
            2592000000L  // 30 days
        );
        jwtService = new JwtService(jwtProperties);
        authService = new AuthServiceImpl(
            userRepository,
            userProfileRepository,
            refreshTokenRepository,
            passwordEncoder,
            jwtService
        );
    }

    @Test
    @DisplayName("register: successful registration hashes password, creates user profile, and issues tokens")
    void register_shouldSucceed() {
        RegisterRequest request = new RegisterRequest("user@example.com", "Password123!", "Alex Sharma");
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_password_123");

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(generatedId);
            return u;
        });

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(generatedId);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.displayName()).isEqualTo("Alex Sharma");
        assertThat(response.accessToken()).isNotEmpty();
        assertThat(response.refreshToken()).startsWith("rt_");
        assertThat(response.expiresIn()).isEqualTo(900L);

        // Verify password hashing was applied
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed_password_123");

        // Verify profile persistence
        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getDisplayName()).isEqualTo("Alex Sharma");
    }

    @Test
    @DisplayName("register: throws EmailAlreadyExistsException when email already registered")
    void register_shouldThrowWhenEmailExists() {
        RegisterRequest request = new RegisterRequest("duplicate@example.com", "Password123!", "Test");
        when(userRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(EmailAlreadyExistsException.class)
            .hasMessageContaining("duplicate@example.com");
    }

    @Test
    @DisplayName("login: successful login returns valid tokens")
    void login_shouldSucceedWithValidCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "Password123!");
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("user@example.com")
            .passwordHash("hashed_pw")
            .role("ROLE_USER")
            .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hashed_pw")).thenReturn(true);
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(
            UserProfile.builder().displayName("Alex").build()
        ));

        AuthResponse response = authService.login(request);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.displayName()).isEqualTo("Alex");
        assertThat(response.accessToken()).isNotEmpty();
        assertThat(response.refreshToken()).startsWith("rt_");
    }

    @Test
    @DisplayName("login: throws InvalidCredentialsException when password does not match")
    void login_shouldThrowWhenPasswordWrong() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword1!");
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("user@example.com")
            .passwordHash("hashed_pw")
            .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword1!", "hashed_pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("refresh: rotation issues new token pair and revokes previous token")
    void refresh_shouldRotateTokens() {
        String oldRawToken = TokenHashUtil.generateSecureRefreshToken();
        String oldHash = TokenHashUtil.hashToken(oldRawToken);

        User user = User.builder()
            .id(UUID.randomUUID())
            .email("user@example.com")
            .role("ROLE_USER")
            .build();

        RefreshToken oldToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .tokenHash(oldHash)
            .expiresAt(Instant.now().plusSeconds(3600))
            .isRevoked(false)
            .createdAt(Instant.now())
            .build();

        when(refreshTokenRepository.findByTokenHashWithLock(oldHash)).thenReturn(Optional.of(oldToken));

        TokenRefreshResponse response = authService.refresh(new RefreshTokenRequest(oldRawToken));

        assertThat(response.accessToken()).isNotEmpty();
        assertThat(response.refreshToken()).isNotEmpty();
        assertThat(response.refreshToken()).isNotEqualTo(oldRawToken);
        assertThat(oldToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(oldToken);
    }

    @Test
    @DisplayName("refresh: detects reuse of revoked token and invalidates all user tokens")
    void refresh_shouldDetectReuseAndRevokeAllSessions() {
        String stolenToken = TokenHashUtil.generateSecureRefreshToken();
        String stolenHash = TokenHashUtil.hashToken(stolenToken);
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("victim@example.com").build();

        RefreshToken revokedToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .tokenHash(stolenHash)
            .expiresAt(Instant.now().plusSeconds(3600))
            .isRevoked(true) // Already used/revoked
            .build();

        when(refreshTokenRepository.findByTokenHashWithLock(stolenHash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(stolenToken)))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("already been revoked or rotated");

        verify(refreshTokenRepository).revokeAllByUserId(userId);
    }

    @Test
    @DisplayName("refresh: rejects expired refresh token")
    void refresh_shouldRejectExpiredToken() {
        String expiredRaw = TokenHashUtil.generateSecureRefreshToken();
        String expiredHash = TokenHashUtil.hashToken(expiredRaw);

        RefreshToken expiredToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(User.builder().id(UUID.randomUUID()).build())
            .tokenHash(expiredHash)
            .expiresAt(Instant.now().minusSeconds(100)) // Expired
            .isRevoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHashWithLock(expiredHash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(expiredRaw)))
            .isInstanceOf(TokenExpiredException.class)
            .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("logout: revokes refresh token belonging to authenticated user")
    void logout_shouldRevokeUserRefreshToken() {
        UUID userId = UUID.randomUUID();
        String rawToken = TokenHashUtil.generateSecureRefreshToken();
        String tokenHash = TokenHashUtil.hashToken(rawToken);

        RefreshToken token = RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(User.builder().id(userId).build())
            .tokenHash(tokenHash)
            .isRevoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        authService.logout(userId, new LogoutRequest(rawToken));

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("getCurrentUser: returns safe user summary for authenticated userId")
    void getCurrentUser_shouldReturnUserSummary() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-15T00:00:00Z");
        User user = User.builder()
            .id(userId)
            .email("alex@example.com")
            .createdAt(createdAt)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(
            UserProfile.builder().displayName("Alex Sharma").build()
        ));

        UserSummaryResponse response = authService.getCurrentUser(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("alex@example.com");
        assertThat(response.displayName()).isEqualTo("Alex Sharma");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
