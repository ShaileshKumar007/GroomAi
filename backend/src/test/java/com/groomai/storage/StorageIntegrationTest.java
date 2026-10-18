package com.groomai.storage;

import com.groomai.storage.config.StorageConfiguration;
import com.groomai.storage.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StorageConfiguration.class)
class StorageIntegrationTest {

    @TempDir
    static Path integrationTempDir;

    @Autowired
    private ObjectStorageService storageService;

    @Autowired
    private StorageProperties storageProperties;

    @DynamicPropertySource
    static void configureStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("groomai.storage.mode", () -> "local");
        registry.add("groomai.storage.local.base-dir", () -> integrationTempDir.toAbsolutePath().toString());
        registry.add("groomai.storage.local.base-url", () -> "http://localhost:8080");
    }

    @Test
    @DisplayName("Spring context should provide LocalStorageService bean configured with StorageProperties")
    void testSpringBeanConfiguration() {
        assertThat(storageService).isNotNull();
        assertThat(storageService).isInstanceOf(LocalStorageService.class);
        assertThat(storageService.getStorageMode()).isEqualTo(StorageMode.LOCAL_STREAM);

        assertThat(storageProperties.getMode()).isEqualTo("local");
        assertThat(storageProperties.getLocal().getBaseDir()).isEqualTo(integrationTempDir.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("Should perform end-to-end storage operations via injected Spring bean")
    void testInjectedStorageServiceOperations() throws IOException {
        String key = "wardrobe/test-user/integration-item.jpg";
        byte[] payload = "spring-storage-payload".getBytes(StandardCharsets.UTF_8);

        // Store
        storageService.store(key, new ByteArrayInputStream(payload), payload.length, "image/jpeg");
        assertThat(storageService.exists(key)).isTrue();

        // Load
        try (InputStream stream = storageService.load(key)) {
            byte[] read = stream.readAllBytes();
            assertThat(read).isEqualTo(payload);
        }

        // Delete
        storageService.delete(key);
        assertThat(storageService.exists(key)).isFalse();
    }
}
