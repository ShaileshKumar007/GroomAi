package com.groomai.auth.security;

import com.groomai.user.entity.User;
import com.groomai.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        JwtProperties jwtProperties = new JwtProperties(
            "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256-signing!",
            900000L,
            2592000000L
        );
        jwtService = new JwtService(jwtProperties);
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("filter: valid Bearer token sets authentication in SecurityContext")
    void doFilter_shouldSetAuthenticationForValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("valid@example.com")
            .role("ROLE_USER")
            .build();

        String token = jwtService.generateAccessToken(user);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("valid@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("filter: rejects access token issued before tokens_invalid_before cutoff")
    void doFilter_shouldRejectTokenIssuedBeforeInvalidationCutoff() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("revoked@example.com")
            .role("ROLE_USER")
            .build();

        // Token issued at time T
        String token = jwtService.generateAccessToken(user);

        // Security event occurred AFTER token issuance (T + 1 second)
        user.setTokensInvalidBefore(Instant.now().plusSeconds(1));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        // Authentication must NOT be set
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("filter: rejects token if user row no longer exists (account deletion defense)")
    void doFilter_shouldRejectTokenIfUserDeleted() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("deleted@example.com")
            .role("ROLE_USER")
            .build();

        String token = jwtService.generateAccessToken(user);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(userRepository.findById(userId)).thenReturn(Optional.empty()); // User record deleted

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("filter: rejects upload-intent ticket when presented as Bearer access token")
    void doFilter_shouldRejectUploadTicketAsBearerToken() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("user@example.com")
            .role("ROLE_USER")
            .build();

        com.groomai.storage.config.StorageProperties storageProperties = new com.groomai.storage.config.StorageProperties();
        storageProperties.setUploadTicketSecret("dXBsb2FkLXRpY2tldC1zZWNyZXQtc2hvdWxkLWJlLWF0LWxlYXN0LTI1Ni1iaXRzLWZvci1oczI1Ng==");
        com.groomai.storage.UploadTicketServiceImpl uploadTicketService = new com.groomai.storage.UploadTicketServiceImpl(storageProperties);

        String uploadTicket = uploadTicketService.generateUploadTicket(userId, "wardrobe/" + userId + "/item-1.jpg", java.time.Duration.ofMinutes(15));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + uploadTicket);

        filter.doFilterInternal(request, response, filterChain);

        // Upload ticket must NEVER authenticate as an access token
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("filter: rejects token with purpose claim other than ACCESS_TOKEN")
    void doFilter_shouldRejectTokenWithInvalidPurposeClaim() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("user@example.com")
            .role("ROLE_USER")
            .build();

        // Even if signed with the JWT key, if purpose == UPLOAD_INTENT, it must be rejected
        String nonAccessToken = io.jsonwebtoken.Jwts.builder()
            .subject(userId.toString())
            .claim("email", user.getEmail())
            .claim("role", user.getRole())
            .claim("purpose", "UPLOAD_INTENT")
            .issuedAt(java.util.Date.from(Instant.now()))
            .expiration(java.util.Date.from(Instant.now().plusSeconds(900)))
            .signWith(jwtService.getSigningKey())
            .compact();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + nonAccessToken);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
