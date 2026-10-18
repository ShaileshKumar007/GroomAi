package com.groomai.storage.controller;

import com.groomai.auth.security.JwtAuthenticationFilter;
import com.groomai.auth.security.JwtService;
import com.groomai.auth.security.RestAuthenticationEntryPoint;
import com.groomai.common.config.SecurityConfig;
import com.groomai.common.exception.GlobalExceptionHandler;
import com.groomai.storage.ObjectStorageService;
import com.groomai.storage.UploadTicketService;
import com.groomai.storage.exception.StorageObjectNotFoundException;
import com.groomai.user.entity.User;
import com.groomai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocalStorageController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RestAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, JwtService.class})
class LocalStorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ObjectStorageService objectStorageService;

    @MockBean
    private UploadTicketService uploadTicketService;

    private UUID userId;
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("user@example.com").role("ROLE_USER").build();
        token = jwtService.generateAccessToken(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("POST /storage/upload: unauthenticated returns 401 Unauthorized")
    void upload_unauthenticated_returns401() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        mockMvc.perform(post("/api/v1/storage/upload/" + key)
                .contentType(MediaType.IMAGE_JPEG)
                .content(new byte[]{0x01, 0x02}))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /storage/upload: cross-tenant upload returns 403 Forbidden")
    void upload_crossTenant_returns403() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        String crossTenantKey = "wardrobe/" + otherUserId + "/item-123.jpg";

        mockMvc.perform(post("/api/v1/storage/upload/" + crossTenantKey)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.IMAGE_JPEG)
                .content(new byte[]{0x01, 0x02}))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /storage/upload: missing upload ticket returns 403 Forbidden with INVALID_UPLOAD_TICKET")
    void upload_missingTicket_returns403() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";

        mockMvc.perform(post("/api/v1/storage/upload/" + key)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.IMAGE_JPEG)
                .content(new byte[]{0x01, 0x02}))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_TICKET"));
    }

    @Test
    @DisplayName("POST /storage/upload: expired upload ticket returns 403 Forbidden with EXPIRED_UPLOAD_TICKET")
    void upload_expiredTicket_returns403() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        when(uploadTicketService.validateUploadTicket(eq("expired.ticket"), eq(userId), eq(key)))
            .thenThrow(new com.groomai.storage.exception.ExpiredUploadTicketException("Upload intent ticket has expired."));

        mockMvc.perform(post("/api/v1/storage/upload/" + key)
                .header("Authorization", "Bearer " + token)
                .header("X-Upload-Ticket", "expired.ticket")
                .contentType(MediaType.IMAGE_JPEG)
                .content(new byte[]{0x01, 0x02}))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EXPIRED_UPLOAD_TICKET"));
    }

    @Test
    @DisplayName("POST /storage/upload: ticket passed only in query param is rejected with 403 INVALID_UPLOAD_TICKET")
    void upload_queryParamTicket_returns403() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";

        mockMvc.perform(post("/api/v1/storage/upload/" + key)
                .header("Authorization", "Bearer " + token)
                .param("ticket", "some.ticket")
                .contentType(MediaType.IMAGE_JPEG)
                .content(new byte[]{0x01, 0x02}))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_TICKET"));
    }

    @Test
    @DisplayName("POST /storage/upload: valid multipart upload with X-Upload-Ticket header saves file and returns 200 OK")
    void upload_multipart_success() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "item-123.jpg",
            "image/jpeg",
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
        );

        mockMvc.perform(multipart("/api/v1/storage/upload/" + key)
                .file(file)
                .header("X-Upload-Ticket", "valid.upload.ticket")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("File uploaded successfully."))
            .andExpect(jsonPath("$.storageKey").value(key));

        verify(uploadTicketService).validateUploadTicket("valid.upload.ticket", userId, key);
        verify(objectStorageService).store(eq(key), any(), eq(3L), eq("image/jpeg"), eq("valid.upload.ticket"));
    }

    @Test
    @DisplayName("POST /storage/upload: target storage key already exists returns 409 Conflict with STORAGE_OBJECT_ALREADY_EXISTS")
    void upload_targetAlreadyExists_returns409() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        org.mockito.Mockito.doThrow(new com.groomai.storage.exception.StorageObjectAlreadyExistsException("Storage object already exists"))
            .when(objectStorageService).store(eq(key), any(), anyLong(), eq("image/jpeg"), eq("valid.ticket"));

        mockMvc.perform(post("/api/v1/storage/upload/" + key)
                .header("Authorization", "Bearer " + token)
                .header("X-Upload-Ticket", "valid.ticket")
                .contentType("image/jpeg")
                .content(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STORAGE_OBJECT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /storage/upload: valid binary upload with header ticket saves file and returns 200 OK")
    void upload_binary_success() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        byte[] payload = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        mockMvc.perform(post("/api/v1/storage/upload/" + key)
                .header("Authorization", "Bearer " + token)
                .header("X-Upload-Ticket", "valid.header.ticket")
                .contentType("image/jpeg")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("File uploaded successfully."))
            .andExpect(jsonPath("$.storageKey").value(key));

        verify(uploadTicketService).validateUploadTicket("valid.header.ticket", userId, key);
        verify(objectStorageService).store(eq(key), any(), anyLong(), eq("image/jpeg"), eq("valid.header.ticket"));
    }

    @Test
    @DisplayName("GET /storage/files: unauthenticated returns 401 Unauthorized")
    void getFile_unauthenticated_returns401() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        mockMvc.perform(get("/api/v1/storage/files/" + key))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /storage/files: cross-tenant access returns 403 Forbidden")
    void getFile_crossTenant_returns403() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        String crossTenantKey = "wardrobe/" + otherUserId + "/item-123.jpg";

        mockMvc.perform(get("/api/v1/storage/files/" + crossTenantKey)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /storage/files: missing object returns 404 Not Found")
    void getFile_notFound_returns404() throws Exception {
        String key = "wardrobe/" + userId + "/missing.jpg";
        when(objectStorageService.exists(key)).thenReturn(false);

        mockMvc.perform(get("/api/v1/storage/files/" + key)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STORAGE_OBJECT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /storage/files: valid file streams content with proper headers")
    void getFile_success() throws Exception {
        String key = "wardrobe/" + userId + "/item-123.jpg";
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(content));

        mockMvc.perform(get("/api/v1/storage/files/" + key)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/jpeg")))
            .andExpect(header().string("Cache-Control", "private, max-age=900"))
            .andExpect(content().bytes(content));
    }
}
