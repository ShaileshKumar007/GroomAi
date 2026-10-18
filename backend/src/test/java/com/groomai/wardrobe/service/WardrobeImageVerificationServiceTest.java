package com.groomai.wardrobe.service;

import com.groomai.storage.ObjectStorageService;
import com.groomai.storage.StorageObjectMetadata;
import com.groomai.storage.exception.StorageObjectNotFoundException;
import com.groomai.wardrobe.exception.InvalidImagePayloadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WardrobeImageVerificationServiceTest {

    @Mock
    private ObjectStorageService objectStorageService;

    private WardrobeImageVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new WardrobeImageVerificationServiceImpl(objectStorageService);
    }

    private byte[] createTestJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should succeed when image exists, has valid JPEG magic bytes, and dimensions >= 200x200")
    void testValidImagePayload() throws Exception {
        String key = "wardrobe/user-123/item-valid.jpg";
        byte[] jpegBytes = createTestJpeg(300, 300);

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, jpegBytes.length, "image/jpeg", Instant.now())
        );
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(jpegBytes));

        assertThatCode(() -> verificationService.verify(key)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw StorageObjectNotFoundException with BAD_REQUEST when storage object does not exist")
    void testObjectDoesNotExist() {
        String key = "wardrobe/user-123/missing.jpg";
        when(objectStorageService.exists(key)).thenReturn(false);

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(StorageObjectNotFoundException.class)
            .hasMessageContaining("Image asset not found in storage")
            .satisfies(ex -> assertThat(((StorageObjectNotFoundException) ex).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Should reject empty (0-byte) image payload")
    void testEmptyPayload() {
        String key = "wardrobe/user-123/empty.jpg";
        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, 0L, "image/jpeg", Instant.now())
        );

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Should reject image payload exceeding 10MB")
    void testOversizedPayload() {
        String key = "wardrobe/user-123/oversized.jpg";
        long oversized = 10 * 1024 * 1024 + 1;

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, oversized, "image/jpeg", Instant.now())
        );

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class)
            .hasMessageContaining("exceeds maximum allowed limit");
    }

    @Test
    @DisplayName("Should reject image without JPEG magic bytes (FF D8 FF)")
    void testInvalidMagicBytes() {
        String key = "wardrobe/user-123/fake.jpg";
        byte[] fakeBytes = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, fakeBytes.length, "image/jpeg", Instant.now())
        );
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(fakeBytes));

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class)
            .hasMessageContaining("valid JPEG magic bytes");
    }

    @Test
    @DisplayName("Should reject JPEG image with dimensions smaller than 200x200")
    void testDimensionsTooSmall() throws Exception {
        String key = "wardrobe/user-123/small.jpg";
        byte[] smallJpegBytes = createTestJpeg(150, 150);

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, smallJpegBytes.length, "image/jpeg", Instant.now())
        );
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(smallJpegBytes));

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class)
            .hasMessageContaining("must be at least 200x200 pixels");
    }

    @Test
    @DisplayName("Should reject PNG file saved with .jpg storage key")
    void testNonJpegFileWithJpgExtension() throws Exception {
        String key = "wardrobe/user-123/file.jpg";
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] pngBytes = baos.toByteArray();

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, pngBytes.length, "image/jpeg", Instant.now())
        );
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(pngBytes));

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class)
            .hasMessageContaining("valid JPEG magic bytes");
    }

    @Test
    @DisplayName("Should reject file with JPEG magic bytes but truncated header safely")
    void testMagicBytesPresentButTruncatedHeader() {
        String key = "wardrobe/user-123/truncated.jpg";
        // Magic bytes FF D8 FF followed by only 2 bytes (truncated before SOF frame marker)
        byte[] truncatedBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, truncatedBytes.length, "image/jpeg", Instant.now())
        );
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(truncatedBytes));

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class);
    }

    @Test
    @DisplayName("Should reject file with JPEG magic bytes followed by non-image corrupted noise safely")
    void testCorruptedDataAfterMagicBytes() {
        String key = "wardrobe/user-123/corrupt.jpg";
        byte[] corruptBytes = new byte[100];
        corruptBytes[0] = (byte) 0xFF;
        corruptBytes[1] = (byte) 0xD8;
        corruptBytes[2] = (byte) 0xFF;
        for (int i = 3; i < 100; i++) {
            corruptBytes[i] = (byte) (i & 0x7F);
        }

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, corruptBytes.length, "image/jpeg", Instant.now())
        );
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(corruptBytes));

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class);
    }

    @Test
    @DisplayName("Should reject JPEG where width is undersized (< 200px) even if height >= 200px")
    void testUndersizedWidthOnly() throws Exception {
        String key = "wardrobe/user-123/narrow.jpg";
        byte[] narrowBytes = createTestJpeg(150, 300);

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, narrowBytes.length, "image/jpeg", Instant.now())
        );
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(narrowBytes));

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class)
            .hasMessageContaining("must be at least 200x200 pixels");
    }

    @Test
    @DisplayName("Should reject JPEG where height is undersized (< 200px) even if width >= 200px")
    void testUndersizedHeightOnly() throws Exception {
        String key = "wardrobe/user-123/short.jpg";
        byte[] shortBytes = createTestJpeg(300, 150);

        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(
            new StorageObjectMetadata(key, shortBytes.length, "image/jpeg", Instant.now())
        );
        when(objectStorageService.load(key)).thenReturn(new ByteArrayInputStream(shortBytes));

        assertThatThrownBy(() -> verificationService.verify(key))
            .isInstanceOf(InvalidImagePayloadException.class)
            .hasMessageContaining("must be at least 200x200 pixels");
    }
}
