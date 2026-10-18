package com.groomai.storage;

import com.groomai.storage.config.StorageProperties;
import com.groomai.storage.exception.ExpiredUploadTicketException;
import com.groomai.storage.exception.InvalidUploadTicketException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class UploadTicketServiceImpl implements UploadTicketService {

    private static final Logger log = LoggerFactory.getLogger(UploadTicketServiceImpl.class);

    private final SecretKey signingKey;

    public UploadTicketServiceImpl(StorageProperties storageProperties) {
        if (storageProperties == null || storageProperties.getUploadTicketSecret() == null || storageProperties.getUploadTicketSecret().isBlank()) {
            throw new IllegalStateException("Storage upload ticket signing key is not configured.");
        }
        this.signingKey = resolveSigningKey(storageProperties.getUploadTicketSecret());
    }

    private SecretKey resolveSigningKey(String secret) {
        byte[] keyBytes;
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                keyBytes = decoded;
            } else {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("Storage upload ticket signing key must be at least 256 bits (32 bytes).");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateUploadTicket(UUID userId, String storageKey, Duration ttl) {
        if (userId == null) {
            throw new InvalidUploadTicketException("User ID must not be null when generating upload ticket.");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new InvalidUploadTicketException("Storage key must not be blank when generating upload ticket.");
        }

        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);

        return Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_STORAGE_KEY, storageKey)
            .claim(CLAIM_PURPOSE, PURPOSE_UPLOAD_INTENT)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact();
    }

    @Override
    public Claims validateUploadTicket(String ticket, UUID expectedUserId, String expectedStorageKey) {
        if (ticket == null || ticket.isBlank()) {
            throw new InvalidUploadTicketException("Upload authorization ticket is missing.");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(ticket)
                .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Upload ticket expired for storage key [{}]: {}", expectedStorageKey, e.getMessage());
            throw new ExpiredUploadTicketException("Upload intent ticket has expired.");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Upload ticket signature or structure invalid: {}", e.getMessage());
            throw new InvalidUploadTicketException("Upload authorization ticket is invalid or tampered with.");
        }

        // 1. Verify purpose claim
        String purpose = claims.get(CLAIM_PURPOSE, String.class);
        if (!PURPOSE_UPLOAD_INTENT.equals(purpose)) {
            log.warn("Upload ticket purpose mismatch: expected [{}] but found [{}]", PURPOSE_UPLOAD_INTENT, purpose);
            throw new InvalidUploadTicketException("Token purpose is invalid; access tokens cannot be used as upload-intent tickets.");
        }

        // 2. Verify subject (user ID)
        String subject = claims.getSubject();
        if (subject == null || expectedUserId == null || !expectedUserId.toString().equalsIgnoreCase(subject)) {
            log.warn("Upload ticket user mismatch: expected [{}] but found [{}]", expectedUserId, subject);
            throw new InvalidUploadTicketException("Upload ticket was issued for a different user.");
        }

        // 3. Verify storage key
        String storageKey = claims.get(CLAIM_STORAGE_KEY, String.class);
        if (storageKey == null || expectedStorageKey == null || !expectedStorageKey.equals(storageKey)) {
            log.warn("Upload ticket storage key mismatch: expected [{}] but found [{}]", expectedStorageKey, storageKey);
            throw new InvalidUploadTicketException("Upload ticket storage key does not match the requested target storage key.");
        }

        // 4. Verify expiration
        if (claims.getExpiration() == null || claims.getExpiration().toInstant().isBefore(Instant.now())) {
            throw new ExpiredUploadTicketException("Upload intent ticket has expired.");
        }

        return claims;
    }
}
