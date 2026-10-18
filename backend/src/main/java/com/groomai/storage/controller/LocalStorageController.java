package com.groomai.storage.controller;

import com.groomai.auth.security.UserPrincipal;
import com.groomai.storage.ObjectStorageService;
import com.groomai.storage.StorageKeyValidator;
import com.groomai.storage.UploadTicketService;
import com.groomai.storage.exception.InvalidStorageKeyException;
import com.groomai.storage.exception.StorageObjectNotFoundException;
import com.groomai.wardrobe.exception.InvalidImagePayloadException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Controller providing local storage ingestion and streaming endpoints for offline
 * development and physical device testing, matching docs/API_SPEC.md Section 4.1.1.
 */
@RestController
@RequestMapping("/api/v1/storage")
public class LocalStorageController {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageController.class);
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private final ObjectStorageService objectStorageService;
    private final UploadTicketService uploadTicketService;

    public LocalStorageController(
        ObjectStorageService objectStorageService,
        UploadTicketService uploadTicketService
    ) {
        this.objectStorageService = objectStorageService;
        this.uploadTicketService = uploadTicketService;
    }

    /**
     * Direct binary or multipart upload endpoint used in local development mode.
     * Enforces tenancy against storageKey user prefix and validates signed upload ticket.
     */
    @PostMapping(value = "/upload/{*storageKeyPath}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "image/jpeg", MediaType.ALL_VALUE})
    public ResponseEntity<Map<String, String>> uploadFile(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String storageKeyPath,
        @RequestParam(value = "file", required = false) MultipartFile multipartFile,
        @org.springframework.web.bind.annotation.RequestHeader(value = "X-Upload-Ticket", required = false) String ticketHeader,
        HttpServletRequest request
    ) throws IOException {

        String storageKey = sanitizeStorageKeyPath(storageKeyPath);
        verifyTenancy(principal, storageKey);

        if (ticketHeader == null || ticketHeader.isBlank()) {
            throw new com.groomai.storage.exception.InvalidUploadTicketException(
                "Missing required upload-intent authorization ticket. Provide via 'X-Upload-Ticket' header."
            );
        }
        String ticket = ticketHeader.trim();

        // Cryptographically validate the ticket against user, storageKey, purpose, and expiration
        uploadTicketService.validateUploadTicket(ticket, principal.id(), storageKey);

        if (multipartFile != null && !multipartFile.isEmpty()) {
            if (multipartFile.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new InvalidImagePayloadException("File exceeds maximum allowed upload size of 10MB.");
            }
            try (InputStream is = multipartFile.getInputStream()) {
                objectStorageService.store(storageKey, is, multipartFile.getSize(), "image/jpeg", ticket);
            }
        } else if (request instanceof MultipartHttpServletRequest multipartRequest && !multipartRequest.getFileMap().isEmpty()) {
            MultipartFile firstFile = multipartRequest.getFileMap().values().iterator().next();
            if (firstFile.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new InvalidImagePayloadException("File exceeds maximum allowed upload size of 10MB.");
            }
            try (InputStream is = firstFile.getInputStream()) {
                objectStorageService.store(storageKey, is, firstFile.getSize(), "image/jpeg", ticket);
            }
        } else {
            long contentLength = request.getContentLengthLong();
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                throw new InvalidImagePayloadException("File exceeds maximum allowed upload size of 10MB.");
            }
            try (InputStream is = request.getInputStream()) {
                objectStorageService.store(storageKey, is, contentLength, "image/jpeg", ticket);
            }
        }

        log.info("Successfully ingested local storage file at key [{}] for user [{}]", storageKey, principal.id());
        return ResponseEntity.ok(Map.of(
            "message", "File uploaded successfully.",
            "storageKey", storageKey
        ));
    }

    /**
     * Serves authenticated image stream from local disk with Content-Type: image/jpeg.
     * Enforces tenancy and short-lived caching headers.
     */
    @GetMapping(value = "/files/{*storageKeyPath}", produces = "image/jpeg")
    public ResponseEntity<InputStreamResource> getFile(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String storageKeyPath
    ) {
        String storageKey = sanitizeStorageKeyPath(storageKeyPath);
        verifyTenancy(principal, storageKey);

        if (!objectStorageService.exists(storageKey)) {
            throw new StorageObjectNotFoundException("Storage object not found for key: " + storageKey);
        }

        InputStream stream = objectStorageService.load(storageKey);

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=900")
            .body(new InputStreamResource(stream));
    }

    private String sanitizeStorageKeyPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new InvalidStorageKeyException("Storage key path is missing.");
        }
        String key = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        StorageKeyValidator.validateKey(key);
        return key;
    }

    private void verifyTenancy(UserPrincipal principal, String storageKey) {
        if (principal == null || principal.id() == null) {
            throw new AccessDeniedException("Authentication principal is missing.");
        }
        UUID keyOwnerId = StorageKeyValidator.extractUserId(storageKey);
        if (!keyOwnerId.equals(principal.id())) {
            log.warn("Cross-tenant storage access attempted: principal [{}] vs key owner [{}]", principal.id(), keyOwnerId);
            throw new AccessDeniedException("Access to storage key denied: cross-tenant access prohibited.");
        }
    }
}
