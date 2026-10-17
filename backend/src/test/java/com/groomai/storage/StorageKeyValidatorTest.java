package com.groomai.storage;

import com.groomai.storage.exception.InvalidStorageKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageKeyValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "wardrobe/user-123/item-456.jpg",
        "wardrobe/d290f1ee-6c54-4b01-90e6-d701748f0851/item-abc.jpeg",
        "photos/user-123/portrait.jpg",
        "staging/user-123/upload_789.jpg"
    })
    @DisplayName("Should accept valid storage keys")
    void testValidStorageKeys(String key) {
        StorageKeyValidator.validateKey(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "../etc/passwd",
        "../../etc/shadow",
        "wardrobe/../secret.jpg",
        "wardrobe/user/../../etc/passwd.jpg",
        "wardrobe\\user\\item.jpg",
        "/wardrobe/user/item.jpg",
        "wardrobe/user/item.jpg/",
        "wardrobe//user/item.jpg",
        "etc/passwd.jpg",
        "other/user/item.jpg",
        "wardrobe/user/item.png",
        "wardrobe/user/item.heic",
        "wardrobe/user/item.exe",
        "wardrobe/user/item",
        "wardrobe/user/item.JPG.exe"
    })
    @DisplayName("Should reject invalid or dangerous storage keys")
    void testInvalidStorageKeys(String key) {
        assertThatThrownBy(() -> StorageKeyValidator.validateKey(key))
            .isInstanceOf(InvalidStorageKeyException.class);
    }

    @Test
    @DisplayName("Should reject null storage key")
    void testNullStorageKey() {
        assertThatThrownBy(() -> StorageKeyValidator.validateKey(null))
            .isInstanceOf(InvalidStorageKeyException.class);
    }

    @Test
    @DisplayName("Should reject key with null bytes")
    void testNullByteStorageKey() {
        assertThatThrownBy(() -> StorageKeyValidator.validateKey("wardrobe/user/item\0.jpg"))
            .isInstanceOf(InvalidStorageKeyException.class);
    }

    @Test
    @DisplayName("Should reject key exceeding 512 characters")
    void testExcessiveLengthStorageKey() {
        String longKey = "wardrobe/user/" + "a".repeat(505) + ".jpg";
        assertThatThrownBy(() -> StorageKeyValidator.validateKey(longKey))
            .isInstanceOf(InvalidStorageKeyException.class)
            .hasMessageContaining("exceeds maximum length");
    }

    @Test
    @DisplayName("Should safely resolve path within baseDir")
    void testResolveAndVerifySuccess(@TempDir Path tempDir) {
        String key = "wardrobe/user-123/item.jpg";
        Path resolved = StorageKeyValidator.resolveAndVerify(tempDir, key);

        assertThat(resolved).isNotNull();
        assertThat(resolved.toString()).startsWith(tempDir.toAbsolutePath().normalize().toString());
        assertThat(resolved.getFileName().toString()).isEqualTo("item.jpg");
    }

    @Test
    @DisplayName("Should block path traversal attempts in resolveAndVerify")
    void testResolveAndVerifyTraversal(@TempDir Path tempDir) {
        assertThatThrownBy(() -> StorageKeyValidator.resolveAndVerify(tempDir, "../../../etc/passwd.jpg"))
            .isInstanceOf(InvalidStorageKeyException.class);
    }
}
