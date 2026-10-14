package com.groomai.auth.service;

import com.groomai.auth.dto.AuthResponse;
import com.groomai.auth.dto.LoginRequest;
import com.groomai.auth.dto.LogoutRequest;
import com.groomai.auth.dto.RefreshTokenRequest;
import com.groomai.auth.dto.RegisterRequest;
import com.groomai.auth.dto.TokenRefreshResponse;
import com.groomai.auth.dto.UserSummaryResponse;

import java.util.UUID;

/**
 * Service interface for user identity, registration, and token lifecycle.
 */
public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    TokenRefreshResponse refresh(RefreshTokenRequest request);

    void logout(UUID authenticatedUserId, LogoutRequest request);

    UserSummaryResponse getCurrentUser(UUID authenticatedUserId);
}
