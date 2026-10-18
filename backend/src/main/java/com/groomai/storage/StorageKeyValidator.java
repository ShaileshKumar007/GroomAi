package com.groomai.storage;

import com.groomai.storage.exception.InvalidStorageKeyException;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and sanitizes storage keys to prevent path traversal,
 * unauthorized filesystem probe, and invalid key structures.
 */
public final class StorageKeyValidator {

    public static final int MAX_KEY_LENGTH = 512;

    private static final Set<String> ALLOWED_PREFIXES = Set.of("wardrobe", "photos", "staging");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg");

    // Matches safe segments: alphanumeric, underscore, hyphen, dot (e.g. "wardrobe/uuid/item-123.jpg")
    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*/[a-zA-Z0-9_.-]+$");

    private StorageKeyValidator() {
        // Utility class
    }

    /**
     * Validates that the provided storage key meets syntax, prefix, and security constraints.
     *
     * @param storageKey the key to validate
     * @throws InvalidStorageKeyException if the key is invalid or contains traversal sequences
     */
    public static void validateKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new InvalidStorageKeyException("Storage key must not be null or blank.");
        }

        if (storageKey.length() > MAX_KEY_LENGTH) {
            throw new InvalidStorageKeyException("Storage key exceeds maximum length of " + MAX_KEY_LENGTH + " characters.");
        }

        // Reject null bytes, backslashes, and traversal sequences
        if (storageKey.contains("\0") || storageKey.contains("\\") || storageKey.contains("..")) {
            throw new InvalidStorageKeyException("Storage key contains illegal or path-traversal characters: " + storageKey);
        }

        if (storageKey.startsWith("/") || storageKey.endsWith("/")) {
            throw new InvalidStorageKeyException("Storage key must not start or end with a slash: " + storageKey);
        }

        if (storageKey.contains("//")) {
            throw new InvalidStorageKeyException("Storage key must not contain consecutive slashes: " + storageKey);
        }

        if (!SAFE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new InvalidStorageKeyException("Storage key does not match required safe format: " + storageKey);
        }

        String[] parts = storageKey.split("/");
        if (parts.length < 2) {
            throw new InvalidStorageKeyException("Storage key must contain at least a domain prefix and filename: " + storageKey);
        }

        String prefix = parts[0];
        if (!ALLOWED_PREFIXES.contains(prefix)) {
            throw new InvalidStorageKeyException("Storage key prefix '" + prefix + "' is not permitted. Allowed: " + ALLOWED_PREFIXES);
        }

        String filename = parts[parts.length - 1];
        String lowerFilename = filename.toLowerCase();
        boolean hasAllowedExt = ALLOWED_EXTENSIONS.stream().anyMatch(lowerFilename::endsWith);
        if (!hasAllowedExt) {
            throw new InvalidStorageKeyException("Storage key file extension is not permitted. Allowed extensions: " + ALLOWED_EXTENSIONS);
        }
    }

    /**
     * Resolves and verifies that a storage key resolves safely within the designated base directory.
     *
     * @param baseDir the root filesystem directory for storage
     * @param storageKey the storage key
     * @return normalized target Path guaranteed to reside strictly within baseDir
     * @throws InvalidStorageKeyException if resolved path traverses outside baseDir
     */
    public static Path resolveAndVerify(Path baseDir, String storageKey) {
        validateKey(storageKey);

        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path resolvedPath = normalizedBase.resolve(storageKey).normalize();

        if (!resolvedPath.startsWith(normalizedBase) || resolvedPath.equals(normalizedBase)) {
            throw new InvalidStorageKeyException("Path traversal attempt detected. Target path escapes base directory: " + storageKey);
        }

        return resolvedPath;
    }

    /**
     * Extracts the user UUID from a validated storage key structured as prefix/{userId}/filename.
     *
     * @param storageKey the storage key
     * @return the UUID of the owning user
     * @throws InvalidStorageKeyException if the key does not contain a valid user UUID segment
     */
    public static java.util.UUID extractUserId(String storageKey) {
        validateKey(storageKey);
        String[] parts = storageKey.split("/");
        if (parts.length < 3) {
            throw new InvalidStorageKeyException("Storage key must contain a user identifier segment: " + storageKey);
        }
        try {
            return java.util.UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidStorageKeyException("Storage key user segment is not a valid UUID: " + parts[1]);
        }
    }

    /**
     * Generates a canonical storage key for a wardrobe item belonging to the specified user.
     * Format: wardrobe/{userId}/{uuid}.jpg
     *
     * @param userId the user ID
     * @return canonical wardrobe storage key
     */
    public static String generateWardrobeKey(java.util.UUID userId) {
        if (userId == null) {
            throw new InvalidStorageKeyException("User ID must not be null when generating wardrobe storage key.");
        }
        return "wardrobe/" + userId + "/" + java.util.UUID.randomUUID() + ".jpg";
    }
}
