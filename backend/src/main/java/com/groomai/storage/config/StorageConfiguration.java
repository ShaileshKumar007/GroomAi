package com.groomai.storage.config;

import com.groomai.storage.LocalStorageService;
import com.groomai.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring configuration providing the active ObjectStorageService bean
 * based on the configured groomai.storage.mode.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "groomai.storage.mode", havingValue = "local", matchIfMissing = true)
    public ObjectStorageService localStorageService(StorageProperties properties) {
        log.info("Configuring LocalStorageService with root baseDir: [{}]", properties.getLocal().getBaseDir());
        return new LocalStorageService(properties);
    }
}
