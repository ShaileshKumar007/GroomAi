package com.groomai.storage;

import com.groomai.auth.security.JwtProperties;
import com.groomai.auth.security.JwtService;
import com.groomai.storage.config.StorageProperties;
import com.groomai.storage.exception.ExpiredUploadTicketException;
import com.groomai.storage.exception.InvalidUploadTicketException;
import com.groomai.user.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadTicketServiceTest {

    private StorageProperties storageProperties;
    private JwtProperties jwtProperties;
    private UploadTicketService uploadTicketService;
    private JwtService jwtService;

    private UUID userId;
    private String storageKey;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setUploadTicketSecret("dXBsb2FkLXRpY2tldC1zZWNyZXQtc2hvdWxkLWJlLWF0LWxlYXN0LTI1Ni1iaXRzLWZvci1oczI1Ng==");

        jwtProperties = new JwtProperties(
            "dGVzdC1zZWNyZXQta2V5LXNob3VsZC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25nLWZvci1oczI1Ng==",
            900000L,
            2592000000L
        );

        uploadTicketService = new UploadTicketServiceImpl(storageProperties);
        jwtService = new JwtService(jwtProperties);

        userId = UUID.randomUUID();
        storageKey = "wardrobe/" + userId + "/item-123.jpg";
    }

    @Test
    @DisplayName("Should successfully generate and validate a valid upload-intent ticket")
    void testGenerateAndValidateValidTicket() {
        String ticket = uploadTicketService.generateUploadTicket(userId, storageKey, Duration.ofMinutes(15));
        assertThat(ticket).isNotBlank();

        Claims claims = uploadTicketService.validateUploadTicket(ticket, userId, storageKey);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get(UploadTicketService.CLAIM_STORAGE_KEY, String.class)).isEqualTo(storageKey);
        assertThat(claims.get(UploadTicketService.CLAIM_PURPOSE, String.class)).isEqualTo(UploadTicketService.PURPOSE_UPLOAD_INTENT);
    }

    @Test
    @DisplayName("Should reject an expired upload ticket with ExpiredUploadTicketException")
    void testValidateExpiredTicket_throwsExpired() {
        // Generate ticket with negative TTL (already expired)
        String expiredTicket = uploadTicketService.generateUploadTicket(userId, storageKey, Duration.ofSeconds(-10));

        assertThatThrownBy(() -> uploadTicketService.validateUploadTicket(expiredTicket, userId, storageKey))
            .isInstanceOf(ExpiredUploadTicketException.class)
            .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should reject a tampered ticket signature with InvalidUploadTicketException")
    void testValidateTamperedSignature_throwsInvalid() {
        String validTicket = uploadTicketService.generateUploadTicket(userId, storageKey, Duration.ofMinutes(15));
        String tamperedTicket = validTicket.substring(0, validTicket.length() - 6) + "xxxxxx";

        assertThatThrownBy(() -> uploadTicketService.validateUploadTicket(tamperedTicket, userId, storageKey))
            .isInstanceOf(InvalidUploadTicketException.class)
            .hasMessageContaining("invalid or tampered with");
    }

    @Test
    @DisplayName("Should reject a ticket presented by a different user than the ticket subject")
    void testValidateTicketOtherUser_throwsInvalid() {
        UUID otherUserId = UUID.randomUUID();
        String ticket = uploadTicketService.generateUploadTicket(userId, storageKey, Duration.ofMinutes(15));

        assertThatThrownBy(() -> uploadTicketService.validateUploadTicket(ticket, otherUserId, storageKey))
            .isInstanceOf(InvalidUploadTicketException.class)
            .hasMessageContaining("issued for a different user");
    }

    @Test
    @DisplayName("Should reject a ticket presented for a different storage key")
    void testValidateTicketOtherStorageKey_throwsInvalid() {
        String otherKey = "wardrobe/" + userId + "/other-item.jpg";
        String ticket = uploadTicketService.generateUploadTicket(userId, storageKey, Duration.ofMinutes(15));

        assertThatThrownBy(() -> uploadTicketService.validateUploadTicket(ticket, userId, otherKey))
            .isInstanceOf(InvalidUploadTicketException.class)
            .hasMessageContaining("does not match the requested target storage key");
    }

    @Test
    @DisplayName("Should reject an access token used as an upload ticket (key isolation)")
    void testAccessTokenCannotBeUsedAsUploadTicket() {
        User user = User.builder().id(userId).email("user@example.com").role("ROLE_USER").build();
        String accessToken = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> uploadTicketService.validateUploadTicket(accessToken, userId, storageKey))
            .isInstanceOf(InvalidUploadTicketException.class);
    }

    @Test
    @DisplayName("Should reject token signed with ticket key if purpose is not UPLOAD_INTENT")
    void testValidateTokenWithInvalidPurpose_throwsInvalid() {
        String invalidPurposeToken = io.jsonwebtoken.Jwts.builder()
            .subject(userId.toString())
            .claim(UploadTicketService.CLAIM_STORAGE_KEY, storageKey)
            .claim(UploadTicketService.CLAIM_PURPOSE, "OTHER_PURPOSE")
            .issuedAt(new java.util.Date())
            .expiration(new java.util.Date(System.currentTimeMillis() + 60000))
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(storageProperties.getUploadTicketSecret())))
            .compact();

        assertThatThrownBy(() -> uploadTicketService.validateUploadTicket(invalidPurposeToken, userId, storageKey))
            .isInstanceOf(InvalidUploadTicketException.class)
            .hasMessageContaining("access tokens cannot be used as upload-intent tickets");
    }

    @Test
    @DisplayName("Should reject null or blank upload ticket")
    void testMissingOrBlankTicket_throwsInvalid() {
        assertThatThrownBy(() -> uploadTicketService.validateUploadTicket(null, userId, storageKey))
            .isInstanceOf(InvalidUploadTicketException.class)
            .hasMessageContaining("ticket is missing");

        assertThatThrownBy(() -> uploadTicketService.validateUploadTicket("   ", userId, storageKey))
            .isInstanceOf(InvalidUploadTicketException.class)
            .hasMessageContaining("ticket is missing");
    }

    @Test
    @DisplayName("Constructor should fail safely when uploadTicketSecret is missing or blank")
    void testConstructor_missingSecret_throwsIllegalState() {
        StorageProperties emptyProps = new StorageProperties();
        emptyProps.setUploadTicketSecret(null);

        assertThatThrownBy(() -> new UploadTicketServiceImpl(emptyProps))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("signing key is not configured");

        emptyProps.setUploadTicketSecret("   ");
        assertThatThrownBy(() -> new UploadTicketServiceImpl(emptyProps))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("signing key is not configured");
    }

    @Test
    @DisplayName("Constructor should fail safely when uploadTicketSecret is under 256 bits")
    void testConstructor_weakSecret_throwsIllegalState() {
        StorageProperties weakProps = new StorageProperties();
        weakProps.setUploadTicketSecret("too-short-secret");

        assertThatThrownBy(() -> new UploadTicketServiceImpl(weakProps))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be at least 256 bits");
    }

    @Test
    @DisplayName("Upload ticket signed with dedicated secret cannot be parsed or authenticated by JwtService")
    void testUploadTicketCannotBeParsedByJwtService() {
        String uploadTicket = uploadTicketService.generateUploadTicket(userId, storageKey, Duration.ofMinutes(15));
        assertThat(uploadTicket).isNotBlank();

        // JwtService must fail to parse claims due to mismatched signature key
        java.util.Optional<Claims> claimsOpt = jwtService.parseClaims(uploadTicket);
        assertThat(claimsOpt).isEmpty();
    }
}
