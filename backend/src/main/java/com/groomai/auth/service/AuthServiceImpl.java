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
import com.groomai.auth.security.JwtService;
import com.groomai.auth.security.TokenHashUtil;
import com.groomai.common.exception.GroomAiException;
import com.groomai.user.entity.User;
import com.groomai.user.entity.UserProfile;
import com.groomai.user.repository.UserProfileRepository;
import com.groomai.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
        UserRepository userRepository,
        UserProfileRepository userProfileRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        try {
            User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role("ROLE_USER")
                .build();
            user = userRepository.save(user);

            String displayName = (request.displayName() != null && !request.displayName().isBlank())
                ? request.displayName().trim()
                : null;

            UserProfile profile = UserProfile.builder()
                .user(user)
                .displayName(displayName)
                .build();
            userProfileRepository.save(profile);

            String accessToken = jwtService.generateAccessToken(user);
            String rawRefreshToken = TokenHashUtil.generateSecureRefreshToken();
            String tokenHash = TokenHashUtil.hashToken(rawRefreshToken);
            Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

            RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .isRevoked(false)
                .build();
            refreshTokenRepository.save(refreshToken);

            log.info("Registered new user with ID [{}]", user.getId());

            return new AuthResponse(
                user.getId(),
                user.getEmail(),
                displayName,
                accessToken,
                rawRefreshToken,
                jwtService.getAccessTokenExpirationSeconds()
            );
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration safety guard
            log.warn("Concurrent duplicate registration attempt for email [{}]", normalizedEmail);
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email [{}]", normalizedEmail);
            throw new InvalidCredentialsException();
        }

        String displayName = userProfileRepository.findByUserId(user.getId())
            .map(UserProfile::getDisplayName)
            .orElse(null);

        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = TokenHashUtil.generateSecureRefreshToken();
        String tokenHash = TokenHashUtil.hashToken(rawRefreshToken);
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .tokenHash(tokenHash)
            .expiresAt(expiresAt)
            .isRevoked(false)
            .build();
        refreshTokenRepository.save(refreshToken);

        log.info("Successful login for user ID [{}]", user.getId());

        return new AuthResponse(
            user.getId(),
            user.getEmail(),
            displayName,
            accessToken,
            rawRefreshToken,
            jwtService.getAccessTokenExpirationSeconds()
        );
    }

    @Override
    @Transactional(noRollbackFor = {InvalidTokenException.class, TokenExpiredException.class})
    public TokenRefreshResponse refresh(RefreshTokenRequest request) {
        String tokenHash = TokenHashUtil.hashToken(request.refreshToken().trim());

        RefreshToken token = refreshTokenRepository.findByTokenHashWithLock(tokenHash)
            .orElseThrow(() -> new InvalidTokenException("Invalid refresh token."));

        // Refresh-token reuse detection: if already revoked, revoke all tokens for this user
        if (token.isRevoked()) {
            log.warn("Detected reuse of revoked refresh token for user ID [{}]. Revoking all active sessions.",
                token.getUser().getId());
            refreshTokenRepository.revokeAllByUserId(token.getUser().getId());
            throw new InvalidTokenException("Refresh token has already been revoked or rotated.");
        }

        // Expiration check
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new TokenExpiredException("Refresh token has expired.");
        }

        // Security cutoff check (e.g., password reset / account revoke)
        User user = token.getUser();
        if (user.getTokensInvalidBefore() != null && token.getCreatedAt().isBefore(user.getTokensInvalidBefore())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new InvalidTokenException("Refresh token was invalidated by a security event.");
        }

        // Rotate: revoke the consumed refresh token
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // Issue new token pair
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRawRefreshToken = TokenHashUtil.generateSecureRefreshToken();
        String newTokenHash = TokenHashUtil.hashToken(newRawRefreshToken);
        Instant newExpiresAt = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

        RefreshToken newRefreshToken = RefreshToken.builder()
            .user(user)
            .tokenHash(newTokenHash)
            .expiresAt(newExpiresAt)
            .isRevoked(false)
            .build();
        refreshTokenRepository.save(newRefreshToken);

        log.info("Rotated refresh token for user ID [{}]", user.getId());

        return new TokenRefreshResponse(
            newAccessToken,
            newRawRefreshToken,
            jwtService.getAccessTokenExpirationSeconds()
        );
    }

    @Override
    @Transactional
    public void logout(UUID authenticatedUserId, LogoutRequest request) {
        String tokenHash = TokenHashUtil.hashToken(request.refreshToken().trim());

        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        tokenOpt.ifPresent(token -> {
            if (token.getUser().getId().equals(authenticatedUserId)) {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                log.info("Revoked refresh token on logout for user ID [{}]", authenticatedUserId);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryResponse getCurrentUser(UUID authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
            .orElseThrow(() -> new GroomAiException("User not found.", HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));

        String displayName = userProfileRepository.findByUserId(user.getId())
            .map(UserProfile::getDisplayName)
            .orElse(null);

        return new UserSummaryResponse(
            user.getId(),
            user.getEmail(),
            displayName,
            user.getCreatedAt()
        );
    }
}
