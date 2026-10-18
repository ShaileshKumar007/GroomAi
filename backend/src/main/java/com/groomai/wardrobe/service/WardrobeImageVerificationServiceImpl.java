package com.groomai.wardrobe.service;

import com.groomai.storage.ObjectStorageService;
import com.groomai.storage.StorageObjectMetadata;
import com.groomai.storage.exception.StorageObjectNotFoundException;
import com.groomai.wardrobe.exception.InvalidImagePayloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Service
public class WardrobeImageVerificationServiceImpl implements WardrobeImageVerificationService {

    private static final Logger log = LoggerFactory.getLogger(WardrobeImageVerificationServiceImpl.class);

    public static final long MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    public static final int MIN_WIDTH = 200;
    public static final int MIN_HEIGHT = 200;

    private final ObjectStorageService objectStorageService;

    public WardrobeImageVerificationServiceImpl(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    @Override
    public void verify(String storageKey) {
        if (!objectStorageService.exists(storageKey)) {
            throw new StorageObjectNotFoundException(
                "Image asset not found in storage for key: " + storageKey,
                HttpStatus.BAD_REQUEST
            );
        }

        StorageObjectMetadata metadata = objectStorageService.getMetadata(storageKey);
        if (metadata.contentLength() <= 0) {
            throw new InvalidImagePayloadException("Uploaded image payload is empty.");
        }
        if (metadata.contentLength() > MAX_IMAGE_SIZE_BYTES) {
            throw new InvalidImagePayloadException("Uploaded image size exceeds maximum allowed limit of 10MB (10485760 bytes).");
        }

        try (InputStream inputStream = objectStorageService.load(storageKey);
             ImageInputStream iis = ImageIO.createImageInputStream(inputStream)) {

            if (iis == null) {
                throw new InvalidImagePayloadException("Failed to open image input stream for key: " + storageKey);
            }

            // 1. Verify JPEG SOI magic bytes (FF D8 FF)
            byte[] magic = new byte[3];
            int bytesRead = iis.read(magic);
            if (bytesRead < 3 || (magic[0] & 0xFF) != 0xFF || (magic[1] & 0xFF) != 0xD8 || (magic[2] & 0xFF) != 0xFF) {
                throw new InvalidImagePayloadException("Uploaded file does not have valid JPEG magic bytes (FF D8 FF).");
            }

            // 2. Seek back to start for ImageReader header inspection
            iis.seek(0);

            // 3. Inspect dimensions using ImageReader without raster decompression
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new InvalidImagePayloadException("No image reader available to parse image format for key: " + storageKey);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width < MIN_WIDTH || height < MIN_HEIGHT) {
                    throw new InvalidImagePayloadException(
                        "Image dimensions (" + width + "x" + height + ") must be at least " + MIN_WIDTH + "x" + MIN_HEIGHT + " pixels."
                    );
                }
            } finally {
                reader.dispose();
            }
        } catch (InvalidImagePayloadException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Corrupted or invalid image payload for key [{}]: {}", storageKey, e.getMessage());
            throw new InvalidImagePayloadException("Corrupted or invalid image payload: " + e.getMessage());
        }
    }
}
