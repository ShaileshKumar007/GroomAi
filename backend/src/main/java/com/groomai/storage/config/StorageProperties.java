package com.groomai.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage configuration properties supporting local and S3 storage modes.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "groomai.storage")
public class StorageProperties {

    /**
     * Active storage mode: "local" (default for development/offline) or "s3" (production).
     */
    private String mode = "local";

    /**
     * Dedicated HMAC-SHA256 signing secret for short-lived upload intent tickets.
     * Isolated from the primary JWT access token secret.
     */
    private String uploadTicketSecret = "dXBsb2FkLXRpY2tldC1zZWNyZXQtc2hvdWxkLWJlLWF0LWxlYXN0LTI1Ni1iaXRzLWZvci1oczI1Ng==";

    /**
     * Local filesystem storage configuration.
     */
    private Local local = new Local();

    /**
     * Cloud S3 storage configuration (used in production/staging).
     */
    private S3 s3 = new S3();

    /**
     * Abandoned upload sweeper configuration.
     */
    private AbandonedSweeper abandonedSweeper = new AbandonedSweeper();

    @Getter
    @Setter
    public static class Local {
        /**
         * Filesystem root directory for local storage. Default: "./data/storage".
         */
        private String baseDir = "./data/storage";

        /**
         * Base URL for the backend hosting the local storage streaming endpoints.
         * Default: "http://localhost:8080".
         */
        private String baseUrl = "http://localhost:8080";
    }

    @Getter
    @Setter
    public static class S3 {
        private String bucket;
        private String region = "us-east-1";
        private String endpoint;
    }

    @Getter
    @Setter
    public static class AbandonedSweeper {
        /**
         * Whether the scheduled sweeper task is active. Default: true.
         */
        private boolean enabled = true;

        /**
         * Minimum age before unconfirmed upload is considered abandoned and eligible for cleanup.
         * Default: 24 hours.
         */
        private int gracePeriodHours = 24;
    }
}
