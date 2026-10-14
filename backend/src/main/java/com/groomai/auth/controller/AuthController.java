package com.groomai.auth.controller;

import com.groomai.auth.dto.AuthResponse;
import com.groomai.auth.dto.LoginRequest;
import com.groomai.auth.dto.LogoutRequest;
import com.groomai.auth.dto.RefreshTokenRequest;
import com.groomai.auth.dto.RegisterRequest;
import com.groomai.auth.dto.TokenRefreshResponse;
import com.groomai.auth.dto.UserSummaryResponse;
import com.groomai.auth.security.UserPrincipal;
import com.groomai.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication and session REST controller matching docs/API_SPEC.md Section 2.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(principal.id(), request);
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummaryResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserSummaryResponse response = authService.getCurrentUser(principal.id());
        return ResponseEntity.ok(response);
    }
}
