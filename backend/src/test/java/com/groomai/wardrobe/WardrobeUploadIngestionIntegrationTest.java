package com.groomai.wardrobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.auth.dto.AuthResponse;
import com.groomai.auth.dto.RegisterRequest;
import com.groomai.auth.service.AuthService;
import com.groomai.user.entity.UserProfile;
import com.groomai.user.repository.UserProfileRepository;
import com.groomai.wardrobe.dto.CreateWardrobeItemRequestDto;
import com.groomai.wardrobe.dto.UploadIntentRequestDto;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("isDockerAvailable")
class WardrobeUploadIngestionIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(WardrobeUploadIngestionIntegrationTest.class);

    @TempDir
    static Path tempStorageDir;

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("groomai_ingestion_test")
        .withUsername("test_user")
        .withPassword("test_password");

    static {
        if (isDockerAvailable()) {
            postgres.start();
        }
    }

    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            log.warn("Docker not available; skipping WardrobeUploadIngestionIntegrationTest: {}", ex.getMessage());
            return false;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (isDockerAvailable() && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("groomai.storage.local.base-dir", () -> tempStorageDir.toString());
        }
    }

    @AfterAll
    static void tearDown() {
        if (postgres.isRunning()) {
            postgres.stop();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private WardrobeItemRepository wardrobeItemRepository;

    private byte[] createTestJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.DARK_GRAY);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("End-to-End: intent -> upload -> confirm -> stream -> tenancy & duplicate protection")
    void testFullWardrobeIngestionLifecycle() throws Exception {
        // 1. Register User 1
        String email = "wardrobe_tester_" + UUID.randomUUID() + "@example.com";
        AuthResponse user1Auth = authService.register(new RegisterRequest(
            email,
            "SecurePass123!",
            "Wardrobe Tester"
        ));
        String tokenUser1 = user1Auth.accessToken();
        UUID user1Id = user1Auth.userId();

        // 2. Request upload intent before consent -> 403 CONSENT_REQUIRED
        UploadIntentRequestDto intentReq = new UploadIntentRequestDto("image/jpeg", 2048500L);
        mockMvc.perform(post("/api/v1/wardrobe/upload-intent")
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intentReq)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        // 3. Grant wardrobe storage consent in user profile
        UserProfile profile = userProfileRepository.findByUserId(user1Id).orElseThrow();
        profile.setWardrobeStorageConsentGranted(true);
        profile.setWardrobeStorageConsentGrantedAt(Instant.now());
        userProfileRepository.save(profile);

        // 4. Request upload intent with consent -> 201 Created
        MvcResult intentResult = mockMvc.perform(post("/api/v1/wardrobe/upload-intent")
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(intentReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.uploadMode").value("LOCAL_STREAM"))
            .andExpect(jsonPath("$.httpMethod").value("POST"))
            .andExpect(jsonPath("$.requiredHeaders['Content-Type']").value("multipart/form-data"))
            .andReturn();

        String intentResponseBody = intentResult.getResponse().getContentAsString();
        String storageKey = objectMapper.readTree(intentResponseBody).get("storageKey").asText();
        String uploadTicket = objectMapper.readTree(intentResponseBody).get("uploadTicket").asText();
        String uploadUrl = objectMapper.readTree(intentResponseBody).get("uploadUrl").asText();
        assertThat(storageKey).startsWith("wardrobe/" + user1Id + "/");
        assertThat(uploadTicket).isNotBlank();
        assertThat(uploadUrl).doesNotContain("?ticket=");

        // 4b. Security Check: Upload without X-Upload-Ticket header -> 403 Forbidden with INVALID_UPLOAD_TICKET
        byte[] jpegBytes = createTestJpeg(400, 400);
        MockMultipartFile unauthenticatedFile = new MockMultipartFile(
            "file",
            "item.jpg",
            "image/jpeg",
            jpegBytes
        );
        mockMvc.perform(multipart("/api/v1/storage/upload/" + storageKey)
                .file(unauthenticatedFile)
                .header("Authorization", "Bearer " + tokenUser1))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_TICKET"));

        // 4c. Security Check: Ticket passed only in query param is rejected -> 403 Forbidden with INVALID_UPLOAD_TICKET
        mockMvc.perform(multipart("/api/v1/storage/upload/" + storageKey)
                .file(unauthenticatedFile)
                .header("Authorization", "Bearer " + tokenUser1)
                .param("ticket", uploadTicket))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_TICKET"));

        // 4d. Security Check: Upload ticket presented as Bearer access token is rejected -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/wardrobe/items")
                .header("Authorization", "Bearer " + uploadTicket))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 5. Upload genuine JPEG image to /api/v1/storage/upload/{storageKey} with valid X-Upload-Ticket header
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "item.jpg",
            "image/jpeg",
            jpegBytes
        );

        mockMvc.perform(multipart("/api/v1/storage/upload/" + storageKey)
                .file(file)
                .header("Authorization", "Bearer " + tokenUser1)
                .header("X-Upload-Ticket", uploadTicket))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("File uploaded successfully."))
            .andExpect(jsonPath("$.storageKey").value(storageKey));

        // 5b. Security Check: Repeat upload attempt to same storageKey (replay/overwrite) -> 409 Conflict
        mockMvc.perform(multipart("/api/v1/storage/upload/" + storageKey)
                .file(file)
                .header("Authorization", "Bearer " + tokenUser1)
                .header("X-Upload-Ticket", uploadTicket))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STORAGE_OBJECT_ALREADY_EXISTS"));

        // 6. Confirm and catalog item via POST /api/v1/wardrobe/items
        CreateWardrobeItemRequestDto createReq = new CreateWardrobeItemRequestDto(
            "Navy Oxford Button-Down",
            Category.TOP,
            "BUTTON_DOWN",
            Color.NAVY,
            Color.WHITE,
            Pattern.SOLID,
            FormalityLevel.SMART_CASUAL,
            Season.ALL_SEASON,
            storageKey
        );

        MvcResult confirmResult = mockMvc.perform(post("/api/v1/wardrobe/items")
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Navy Oxford Button-Down"))
            .andExpect(jsonPath("$.category").value("TOP"))
            .andExpect(jsonPath("$.primaryColor").value("NAVY"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.storageKey").value(storageKey))
            .andExpect(jsonPath("$.downloadUrl").isNotEmpty())
            .andReturn();

        String confirmResponseBody = confirmResult.getResponse().getContentAsString();
        String itemIdStr = objectMapper.readTree(confirmResponseBody).get("id").asText();
        UUID itemId = UUID.fromString(itemIdStr);

        // 7. Verify persistence in PostgreSQL
        Optional<WardrobeItem> itemOpt = wardrobeItemRepository.findByIdAndUserId(itemId, user1Id);
        assertThat(itemOpt).isPresent();
        WardrobeItem savedItem = itemOpt.get();
        assertThat(savedItem.getName()).isEqualTo("Navy Oxford Button-Down");
        assertThat(savedItem.getStatus()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(savedItem.getStorageKey()).isEqualTo(storageKey);

        // 7b. Security Check: Overwrite attempt after catalog confirmation is rejected -> 409 Conflict
        mockMvc.perform(multipart("/api/v1/storage/upload/" + storageKey)
                .file(file)
                .header("Authorization", "Bearer " + tokenUser1)
                .header("X-Upload-Ticket", uploadTicket))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STORAGE_OBJECT_ALREADY_EXISTS"));

        // 8. Stream image stream via authenticated GET /api/v1/storage/files/{storageKey}
        mockMvc.perform(get("/api/v1/storage/files/" + storageKey)
                .header("Authorization", "Bearer " + tokenUser1))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/jpeg")))
            .andExpect(header().string("Cache-Control", "private, max-age=900"));

        // 9. Register User 2: attempt cross-tenant image download -> 403 Forbidden
        String user2Email = "wardrobe_user2_" + UUID.randomUUID() + "@example.com";
        AuthResponse user2Auth = authService.register(new RegisterRequest(
            user2Email,
            "SecurePass123!",
            "Second User"
        ));
        String tokenUser2 = user2Auth.accessToken();

        mockMvc.perform(get("/api/v1/storage/files/" + storageKey)
                .header("Authorization", "Bearer " + tokenUser2))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 10. Attempt duplicate cataloging with same storageKey -> 409 Conflict
        mockMvc.perform(post("/api/v1/wardrobe/items")
                .header("Authorization", "Bearer " + tokenUser1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_ITEM"));
    }
}
