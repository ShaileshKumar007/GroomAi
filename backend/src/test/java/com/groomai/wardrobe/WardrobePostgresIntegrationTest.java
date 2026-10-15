package com.groomai.wardrobe;

import com.groomai.user.entity.StylePreferences;
import com.groomai.user.entity.User;
import com.groomai.user.entity.UserProfile;
import com.groomai.user.repository.StylePreferencesRepository;
import com.groomai.user.repository.UserProfileRepository;
import com.groomai.user.repository.UserRepository;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Pattern;
import com.groomai.wardrobe.enums.Season;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL 16 Testcontainers integration test verifying wardrobe data foundation:
 * persistence, tenancy isolation, constraints, cascade deletion, and pagination.
 */
@SpringBootTest
@EnabledIf("isDockerAvailable")
class WardrobePostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(WardrobePostgresIntegrationTest.class);

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("groomai_wardrobe_test")
        .withUsername("test_user")
        .withPassword("test_password");

    static {
        if (isDockerAvailable()) {
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (isDockerAvailable() && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.url", postgres::getJdbcUrl);
            registry.add("spring.flyway.user", postgres::getUsername);
            registry.add("spring.flyway.password", postgres::getPassword);
        }
    }

    @AfterAll
    static void tearDown() {
        if (postgres.isRunning()) {
            postgres.stop();
        }
    }

    static boolean isDockerAvailable() {
        try {
            boolean available = DockerClientFactory.instance().isDockerAvailable();
            if (!available) {
                log.info("Docker is not available. Skipping integration test.");
            }
            return available;
        } catch (Throwable t) {
            log.info("Docker check failed: {}. Skipping integration test.", t.getMessage());
            return false;
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private WardrobeItemRepository wardrobeItemRepository;

    @Autowired
    private StylePreferencesRepository stylePreferencesRepository;

    private User createTestUser(String email) {
        User user = User.builder()
            .email(email)
            .passwordHash("$2a$12$e80wYF8lZ70vMqmZ3O3YwOqL1E5gqN...")
            .role("ROLE_USER")
            .build();
        return userRepository.save(user);
    }

    @Test
    @DisplayName("Should persist and retrieve wardrobe item with all metadata and timestamps")
    void testSaveAndRetrieveWardrobeItem() {
        User user = createTestUser("wardrobe_user1_" + UUID.randomUUID() + "@example.com");

        WardrobeItem item = WardrobeItem.builder()
            .user(user)
            .name("Navy Oxford Button-Down")
            .category(Category.TOP)
            .subcategory("BUTTON_DOWN")
            .primaryColor(Color.NAVY)
            .secondaryColor(Color.WHITE)
            .pattern(Pattern.SOLID)
            .formalityLevel(FormalityLevel.SMART_CASUAL)
            .season(Season.ALL_SEASON)
            .storageKey("wardrobe/" + user.getId() + "/navy_oxford_" + UUID.randomUUID() + ".jpg")
            .status(ItemStatus.ACTIVE)
            .build();

        WardrobeItem saved = wardrobeItemRepository.save(item);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<WardrobeItem> retrieved = wardrobeItemRepository.findByIdAndUserId(saved.getId(), user.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getName()).isEqualTo("Navy Oxford Button-Down");
        assertThat(retrieved.get().getCategory()).isEqualTo(Category.TOP);
        assertThat(retrieved.get().getPrimaryColor()).isEqualTo(Color.NAVY);
        assertThat(retrieved.get().getSecondaryColor()).isEqualTo(Color.WHITE);
        assertThat(retrieved.get().getPattern()).isEqualTo(Pattern.SOLID);
        assertThat(retrieved.get().getFormalityLevel()).isEqualTo(FormalityLevel.SMART_CASUAL);
        assertThat(retrieved.get().getSeason()).isEqualTo(Season.ALL_SEASON);
        assertThat(retrieved.get().getStatus()).isEqualTo(ItemStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should strictly enforce tenancy isolation between users")
    void testTenancyIsolation() {
        User user1 = createTestUser("tenant1_" + UUID.randomUUID() + "@example.com");
        User user2 = createTestUser("tenant2_" + UUID.randomUUID() + "@example.com");

        WardrobeItem user1Item = wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user1)
            .name("User 1 Leather Boots")
            .category(Category.FOOTWEAR)
            .primaryColor(Color.BROWN)
            .storageKey("wardrobe/" + user1.getId() + "/boots_" + UUID.randomUUID() + ".jpg")
            .build());

        // User 2 cannot access User 1's item
        Optional<WardrobeItem> crossTenantAccess = wardrobeItemRepository.findByIdAndUserId(user1Item.getId(), user2.getId());
        assertThat(crossTenantAccess).isEmpty();

        boolean existsForUser2 = wardrobeItemRepository.existsByIdAndUserId(user1Item.getId(), user2.getId());
        assertThat(existsForUser2).isFalse();

        // User 1 can access their own item
        boolean existsForUser1 = wardrobeItemRepository.existsByIdAndUserId(user1Item.getId(), user1.getId());
        assertThat(existsForUser1).isTrue();
    }

    @Test
    @DisplayName("Should enforce uniqueness constraint on storage_key")
    void testStorageKeyUniqueness() {
        User user = createTestUser("storage_key_user_" + UUID.randomUUID() + "@example.com");
        String duplicateKey = "wardrobe/shared/duplicate_" + UUID.randomUUID() + ".jpg";

        wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user)
            .name("Item 1")
            .category(Category.TOP)
            .primaryColor(Color.BLACK)
            .storageKey(duplicateKey)
            .build());

        WardrobeItem duplicateItem = WardrobeItem.builder()
            .user(user)
            .name("Item 2")
            .category(Category.BOTTOM)
            .primaryColor(Color.BLUE)
            .storageKey(duplicateKey)
            .build();

        assertThatThrownBy(() -> wardrobeItemRepository.saveAndFlush(duplicateItem))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should cascade delete wardrobe items when owning user is deleted")
    void testUserCascadeDeletion() {
        User user = createTestUser("cascade_user_" + UUID.randomUUID() + "@example.com");

        WardrobeItem item1 = wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user)
            .name("Chinos")
            .category(Category.BOTTOM)
            .primaryColor(Color.BEIGE)
            .storageKey("wardrobe/" + user.getId() + "/chinos_" + UUID.randomUUID() + ".jpg")
            .build());

        WardrobeItem item2 = wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user)
            .name("Denim Jacket")
            .category(Category.OUTERWEAR)
            .primaryColor(Color.BLUE)
            .storageKey("wardrobe/" + user.getId() + "/jacket_" + UUID.randomUUID() + ".jpg")
            .build());

        assertThat(wardrobeItemRepository.existsById(item1.getId())).isTrue();
        assertThat(wardrobeItemRepository.existsById(item2.getId())).isTrue();

        // Delete user
        userRepository.delete(user);

        // Verify cascade deletion
        assertThat(wardrobeItemRepository.existsById(item1.getId())).isFalse();
        assertThat(wardrobeItemRepository.existsById(item2.getId())).isFalse();
    }

    @Test
    @DisplayName("Should filter and paginate items by user, category, and status")
    void testFilteringByCategoryAndStatus() {
        User user = createTestUser("filter_user_" + UUID.randomUUID() + "@example.com");

        // 2 Active Tops
        wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user).name("White Tee").category(Category.TOP).primaryColor(Color.WHITE)
            .storageKey("wardrobe/" + user.getId() + "/t1_" + UUID.randomUUID() + ".jpg").status(ItemStatus.ACTIVE).build());
        wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user).name("Black Polo").category(Category.TOP).primaryColor(Color.BLACK)
            .storageKey("wardrobe/" + user.getId() + "/t2_" + UUID.randomUUID() + ".jpg").status(ItemStatus.ACTIVE).build());

        // 1 Active Bottom
        wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user).name("Blue Jeans").category(Category.BOTTOM).primaryColor(Color.BLUE)
            .storageKey("wardrobe/" + user.getId() + "/b1_" + UUID.randomUUID() + ".jpg").status(ItemStatus.ACTIVE).build());

        // 1 Archived Top
        wardrobeItemRepository.save(WardrobeItem.builder()
            .user(user).name("Old Sweater").category(Category.TOP).primaryColor(Color.GREY)
            .storageKey("wardrobe/" + user.getId() + "/t3_" + UUID.randomUUID() + ".jpg").status(ItemStatus.ARCHIVED).build());

        // Assert counts
        assertThat(wardrobeItemRepository.countByUserIdAndStatus(user.getId(), ItemStatus.ACTIVE)).isEqualTo(3);
        assertThat(wardrobeItemRepository.countByUserIdAndStatus(user.getId(), ItemStatus.ARCHIVED)).isEqualTo(1);
        assertThat(wardrobeItemRepository.countByUserIdAndCategoryAndStatus(user.getId(), Category.TOP, ItemStatus.ACTIVE)).isEqualTo(2);

        // Assert paginated query
        Page<WardrobeItem> activeTopsPage = wardrobeItemRepository.findByUserIdAndCategoryAndStatus(
            user.getId(), Category.TOP, ItemStatus.ACTIVE, PageRequest.of(0, 10));
        assertThat(activeTopsPage.getTotalElements()).isEqualTo(2);

        // Assert storage keys query for outbox
        List<String> storageKeys = wardrobeItemRepository.findStorageKeysByUserId(user.getId());
        assertThat(storageKeys).hasSize(4);
    }

    @Test
    @DisplayName("Should persist and update dual decoupled privacy consents on UserProfile")
    void testUserProfileDualConsentUpdate() {
        User user = createTestUser("consent_user_" + UUID.randomUUID() + "@example.com");

        UserProfile profile = UserProfile.builder()
            .user(user)
            .displayName("Fashion User")
            .ageBracket("25-34")
            .wardrobeStorageConsentGranted(false)
            .aiStylingConsentGranted(false)
            .build();
        userProfileRepository.save(profile);

        // Grant wardrobe storage consent
        Instant storageConsentTime = Instant.now();
        profile.setWardrobeStorageConsentGranted(true);
        profile.setWardrobeStorageConsentGrantedAt(storageConsentTime);
        userProfileRepository.save(profile);

        UserProfile reloaded = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(reloaded.isWardrobeStorageConsentGranted()).isTrue();
        assertThat(reloaded.getWardrobeStorageConsentGrantedAt()).isNotNull();
        assertThat(reloaded.isAiStylingConsentGranted()).isFalse();

        // Grant AI styling consent independently
        Instant aiConsentTime = Instant.now();
        reloaded.setAiStylingConsentGranted(true);
        reloaded.setAiStylingConsentGrantedAt(aiConsentTime);
        userProfileRepository.save(reloaded);

        UserProfile fullyConsented = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(fullyConsented.isWardrobeStorageConsentGranted()).isTrue();
        assertThat(fullyConsented.isAiStylingConsentGranted()).isTrue();
        assertThat(fullyConsented.getAiStylingConsentGrantedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should persist and retrieve StylePreferences entity")
    void testStylePreferencesPersistence() {
        User user = createTestUser("style_user_" + UUID.randomUUID() + "@example.com");

        StylePreferences prefs = StylePreferences.builder()
            .user(user)
            .primaryStyle("SMART_CASUAL")
            .preferredFit("SLIM")
            .colorPreferences("[\"NAVY\", \"WHITE\", \"OLIVE\"]")
            .avoidedColors("[\"YELLOW\"]")
            .defaultFormality("SMART_CASUAL")
            .build();

        stylePreferencesRepository.save(prefs);

        Optional<StylePreferences> retrieved = stylePreferencesRepository.findByUserId(user.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPrimaryStyle()).isEqualTo("SMART_CASUAL");
        assertThat(retrieved.get().getPreferredFit()).isEqualTo("SLIM");
        assertThat(retrieved.get().getColorPreferences()).contains("NAVY");
        assertThat(retrieved.get().getAvoidedColors()).contains("YELLOW");
    }
}
