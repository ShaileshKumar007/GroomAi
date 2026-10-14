package com.groomai.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groomai.jwt")
public record JwtProperties(
    String secret,
    long accessTokenExpirationMs,
    long refreshTokenExpirationMs
) {}
