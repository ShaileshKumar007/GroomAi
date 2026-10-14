package com.groomai.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.auth.dto.AuthResponse;
import com.groomai.auth.dto.LoginRequest;
import com.groomai.auth.dto.RegisterRequest;
import com.groomai.auth.exception.EmailAlreadyExistsException;
import com.groomai.auth.exception.InvalidCredentialsException;
import com.groomai.auth.security.JwtAuthenticationFilter;
import com.groomai.auth.security.JwtProperties;
import com.groomai.auth.security.JwtService;
import com.groomai.auth.security.RestAuthenticationEntryPoint;
import com.groomai.auth.service.AuthService;
import com.groomai.common.config.SecurityConfig;
import com.groomai.common.exception.GlobalExceptionHandler;
import com.groomai.user.repository.UserRepository;
import com.groomai.auth.dto.UserSummaryResponse;
import com.groomai.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RestAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, JwtService.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /register: valid request returns 201 Created with tokens")
    void register_shouldReturn201() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "Password123!", "Alex");
        AuthResponse response = new AuthResponse(
            UUID.randomUUID(),
            "test@example.com",
            "Alex",
            "mock_access_token",
            "mock_refresh_token",
            900L
        );

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.accessToken").value("mock_access_token"))
            .andExpect(jsonPath("$.refreshToken").value("mock_refresh_token"))
            .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /register: weak password returns 400 Bad Request with VALIDATION_FAILED")
    void register_shouldReturn400OnWeakPassword() throws Exception {
        // Missing digit and special character
        RegisterRequest request = new RegisterRequest("test@example.com", "weakpass", "Alex");

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("POST /register: invalid email format returns 400 Bad Request")
    void register_shouldReturn400OnInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "Password123!", "Alex");

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /register: duplicate email returns 409 Conflict with EMAIL_ALREADY_EXISTS")
    void register_shouldReturn409OnDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("existing@example.com", "Password123!", "Alex");
        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("existing@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /login: invalid credentials returns 401 Unauthorized with INVALID_CREDENTIALS")
    void login_shouldReturn401OnBadCredentials() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("GET /me: without authentication returns 401 Unauthorized with standard error response")
    void getMe_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("GET /me: token issued before tokens_invalid_before returns 401 Unauthorized")
    void getMe_withTokenBeforeCutoff_shouldReturn401() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("cutoff@example.com")
            .role("ROLE_USER")
            .tokensInvalidBefore(Instant.now().plusSeconds(10))
            .build();

        String token = jwtService.generateAccessToken(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /me: expired token returns 401 Unauthorized")
    void getMe_withExpiredToken_shouldReturn401() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("exp@example.com").role("ROLE_USER").build();
        String expiredToken = jwtService.generateExpiredToken(user);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /me: token with invalid signature returns 401 Unauthorized")
    void getMe_withInvalidSignature_shouldReturn401() throws Exception {
        SecretKey otherKey = Keys.hmacShaKeyFor("different-secret-key-that-is-at-least-256-bits-long-123456!".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(otherKey)
            .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + forgedToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /me: malformed token returns 401 Unauthorized")
    void getMe_withMalformedToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer not.a.valid.jwt.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /me: token for deleted user returns 401 Unauthorized")
    void getMe_whenUserDeleted_shouldReturn401() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("deleted@example.com").role("ROLE_USER").build();
        String token = jwtService.generateAccessToken(user);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /me: valid token returns 200 OK with UserSummaryResponse")
    void getMe_withValidToken_shouldReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("valid@example.com").role("ROLE_USER").build();
        String token = jwtService.generateAccessToken(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authService.getCurrentUser(userId)).thenReturn(new UserSummaryResponse(
            userId,
            "valid@example.com",
            "Valid User",
            Instant.parse("2026-10-15T00:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.email").value("valid@example.com"))
            .andExpect(jsonPath("$.displayName").value("Valid User"));
    }
}
