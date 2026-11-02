package com.groomai.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.user.dto.PatchStylePreferencesRequestDto;
import com.groomai.user.dto.StylePreferencesResponseDto;
import com.groomai.user.dto.UpdateStylePreferencesRequestDto;
import com.groomai.user.entity.StylePreferences;
import com.groomai.user.entity.User;
import com.groomai.user.enums.PreferredFit;
import com.groomai.user.enums.PrimaryStyle;
import com.groomai.user.enums.StyleGoal;
import com.groomai.user.exception.StylePreferencesValidationException;
import com.groomai.user.repository.StylePreferencesRepository;
import com.groomai.user.repository.UserRepository;
import com.groomai.wardrobe.enums.Color;
import com.groomai.wardrobe.enums.FormalityLevel;
import com.groomai.wardrobe.enums.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StylePreferencesServiceTest {

    @Mock
    private StylePreferencesRepository stylePreferencesRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StylePreferencesServiceImpl service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .id(userId)
            .email("test@example.com")
            .passwordHash("hashed")
            .build();
    }

    @Test
    @DisplayName("GET: returns documented defaults and profileCompleted=false when no record exists")
    void getStylePreferences_UnconfiguredUser_ReturnsDefaults() {
        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());

        StylePreferencesResponseDto response = service.getStylePreferences(userId);

        assertThat(response).isNotNull();
        assertThat(response.profileCompleted()).isFalse();
        assertThat(response.primaryStyle()).isEqualTo(PrimaryStyle.CASUAL);
        assertThat(response.preferredFit()).isEqualTo(PreferredFit.REGULAR);
        assertThat(response.defaultFormality()).isEqualTo(FormalityLevel.CASUAL);
        assertThat(response.colorPreferences()).isEmpty();
        assertThat(response.avoidedColors()).isEmpty();
        assertThat(response.patternPreferences()).isEmpty();
        assertThat(response.avoidedPatterns()).isEmpty();
        assertThat(response.styleGoals()).isEmpty();
    }

    @Test
    @DisplayName("GET: returns stored values and profileCompleted=true for configured user")
    void getStylePreferences_ExistingConfigured_ReturnsStoredValues() {
        StylePreferences existing = StylePreferences.builder()
            .id(UUID.randomUUID())
            .user(user)
            .primaryStyle("SMART_CASUAL")
            .preferredFit("SLIM")
            .defaultFormality("SMART_CASUAL")
            .profileCompleted(true)
            .colorPreferences(List.of("NAVY", "WHITE"))
            .avoidedColors(List.of("YELLOW"))
            .patternPreferences(List.of("SOLID"))
            .avoidedPatterns(List.of("FLORAL"))
            .styleGoals(List.of("LOOK_SHARPER"))
            .updatedAt(Instant.now())
            .build();

        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        StylePreferencesResponseDto response = service.getStylePreferences(userId);

        assertThat(response.profileCompleted()).isTrue();
        assertThat(response.primaryStyle()).isEqualTo(PrimaryStyle.SMART_CASUAL);
        assertThat(response.preferredFit()).isEqualTo(PreferredFit.SLIM);
        assertThat(response.defaultFormality()).isEqualTo(FormalityLevel.SMART_CASUAL);
        assertThat(response.colorPreferences()).containsExactly(Color.NAVY, Color.WHITE);
        assertThat(response.avoidedColors()).containsExactly(Color.YELLOW);
        assertThat(response.patternPreferences()).containsExactly(Pattern.SOLID);
        assertThat(response.avoidedPatterns()).containsExactly(Pattern.FLORAL);
        assertThat(response.styleGoals()).containsExactly(StyleGoal.LOOK_SHARPER);
    }

    @Test
    @DisplayName("PUT: replaces resource, creates record with profileCompleted=true")
    void updateStylePreferences_FirstTime_CreatesRecordAndMarksCompleted() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.MINIMALIST,
            PreferredFit.REGULAR,
            List.of(Color.BLACK, Color.WHITE),
            List.of(Color.RED),
            List.of(Pattern.SOLID),
            List.of(Pattern.GRAPHIC),
            FormalityLevel.SMART_CASUAL,
            List.of(StyleGoal.MINIMAL_EFFORT)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(stylePreferencesRepository.insertStylePreferencesIdempotent(
            any(), eq(userId), eq("MINIMALIST"), eq("REGULAR"), eq("SMART_CASUAL"),
            eq(true), anyString(), anyString(), anyString(), anyString(), anyString(),
            any(), any()
        )).thenReturn(1);

        StylePreferences savedEntity = StylePreferences.builder()
            .id(UUID.randomUUID())
            .user(user)
            .primaryStyle("MINIMALIST")
            .preferredFit("REGULAR")
            .defaultFormality("SMART_CASUAL")
            .profileCompleted(true)
            .colorPreferences(List.of("BLACK", "WHITE"))
            .avoidedColors(List.of("RED"))
            .patternPreferences(List.of("SOLID"))
            .avoidedPatterns(List.of("GRAPHIC"))
            .styleGoals(List.of("MINIMAL_EFFORT"))
            .updatedAt(Instant.now())
            .build();

        when(stylePreferencesRepository.findByUserId(userId))
            .thenReturn(Optional.empty()) // first check
            .thenReturn(Optional.of(savedEntity)); // check after insert

        StylePreferencesResponseDto response = service.updateStylePreferences(userId, request);

        assertThat(response.profileCompleted()).isTrue();
        assertThat(response.primaryStyle()).isEqualTo(PrimaryStyle.MINIMALIST);
        assertThat(response.colorPreferences()).containsExactly(Color.BLACK, Color.WHITE);
        assertThat(response.avoidedColors()).containsExactly(Color.RED);
    }

    @Test
    @DisplayName("PUT: updates existing record and sets profileCompleted=true")
    void updateStylePreferences_ExistingRecord_UpdatesAllFields() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CLASSIC,
            PreferredFit.SLIM,
            List.of(Color.NAVY),
            List.of(Color.YELLOW),
            List.of(Pattern.STRIPED),
            List.of(Pattern.FLORAL),
            FormalityLevel.BUSINESS_CASUAL,
            List.of(StyleGoal.LOOK_SHARPER)
        );

        StylePreferences existing = StylePreferences.builder()
            .id(UUID.randomUUID())
            .user(user)
            .primaryStyle("CASUAL")
            .profileCompleted(false)
            .colorPreferences(new ArrayList<>())
            .avoidedColors(new ArrayList<>())
            .patternPreferences(new ArrayList<>())
            .avoidedPatterns(new ArrayList<>())
            .styleGoals(new ArrayList<>())
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(stylePreferencesRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StylePreferencesResponseDto response = service.updateStylePreferences(userId, request);

        assertThat(response.profileCompleted()).isTrue();
        assertThat(response.primaryStyle()).isEqualTo(PrimaryStyle.CLASSIC);
        assertThat(response.preferredFit()).isEqualTo(PreferredFit.SLIM);
        assertThat(response.defaultFormality()).isEqualTo(FormalityLevel.BUSINESS_CASUAL);
        assertThat(response.colorPreferences()).containsExactly(Color.NAVY);
        assertThat(response.avoidedColors()).containsExactly(Color.YELLOW);
        assertThat(response.patternPreferences()).containsExactly(Pattern.STRIPED);
        assertThat(response.avoidedPatterns()).containsExactly(Pattern.FLORAL);
        assertThat(response.styleGoals()).containsExactly(StyleGoal.LOOK_SHARPER);
    }

    @Test
    @DisplayName("PUT: rejects duplicate colors in colorPreferences")
    void updateStylePreferences_DuplicateColors_ThrowsException() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.NAVY, Color.NAVY), // duplicate
            List.of(Color.YELLOW),
            List.of(Pattern.SOLID),
            List.of(Pattern.FLORAL),
            FormalityLevel.CASUAL,
            List.of(StyleGoal.LOOK_SHARPER)
        );

        assertThatThrownBy(() -> service.updateStylePreferences(userId, request))
            .isInstanceOf(StylePreferencesValidationException.class)
            .hasMessageContaining("duplicate colors");
    }

    @Test
    @DisplayName("PUT: rejects duplicate colors in avoidedColors")
    void updateStylePreferences_DuplicateAvoidedColors_ThrowsException() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.NAVY),
            List.of(Color.YELLOW, Color.YELLOW), // duplicate
            List.of(Pattern.SOLID),
            List.of(Pattern.FLORAL),
            FormalityLevel.CASUAL,
            List.of(StyleGoal.LOOK_SHARPER)
        );

        assertThatThrownBy(() -> service.updateStylePreferences(userId, request))
            .isInstanceOf(StylePreferencesValidationException.class)
            .hasMessageContaining("duplicate colors");
    }

    @Test
    @DisplayName("PUT: rejects intersection between colorPreferences and avoidedColors")
    void updateStylePreferences_ColorIntersection_ThrowsException() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.NAVY, Color.WHITE),
            List.of(Color.WHITE, Color.YELLOW), // WHITE intersects
            List.of(Pattern.SOLID),
            List.of(Pattern.FLORAL),
            FormalityLevel.CASUAL,
            List.of(StyleGoal.LOOK_SHARPER)
        );

        assertThatThrownBy(() -> service.updateStylePreferences(userId, request))
            .isInstanceOf(StylePreferencesValidationException.class)
            .hasMessageContaining("Color cannot be both preferred and avoided: WHITE");
    }

    @Test
    @DisplayName("PUT: rejects intersection between patternPreferences and avoidedPatterns")
    void updateStylePreferences_PatternIntersection_ThrowsException() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.NAVY),
            List.of(Color.YELLOW),
            List.of(Pattern.SOLID, Pattern.STRIPED),
            List.of(Pattern.STRIPED), // STRIPED intersects
            FormalityLevel.CASUAL,
            List.of(StyleGoal.LOOK_SHARPER)
        );

        assertThatThrownBy(() -> service.updateStylePreferences(userId, request))
            .isInstanceOf(StylePreferencesValidationException.class)
            .hasMessageContaining("Pattern cannot be both preferred and avoided: STRIPED");
    }

    @Test
    @DisplayName("PUT: rejects duplicate styleGoals")
    void updateStylePreferences_DuplicateGoals_ThrowsException() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.NAVY),
            List.of(Color.YELLOW),
            List.of(Pattern.SOLID),
            List.of(Pattern.FLORAL),
            FormalityLevel.CASUAL,
            List.of(StyleGoal.LOOK_SHARPER, StyleGoal.LOOK_SHARPER) // duplicate
        );

        assertThatThrownBy(() -> service.updateStylePreferences(userId, request))
            .isInstanceOf(StylePreferencesValidationException.class)
            .hasMessageContaining("duplicate goals");
    }

    @Test
    @DisplayName("PUT: rejects collections exceeding maximum size limits")
    void updateStylePreferences_ExceedingSizeLimits_ThrowsException() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.CASUAL,
            PreferredFit.REGULAR,
            List.of(Color.WHITE, Color.BLACK, Color.NAVY, Color.GREY, Color.BEIGE,
                    Color.BROWN, Color.BLUE, Color.GREEN, Color.OLIVE, Color.RED, Color.YELLOW), // 11 colors > 10
            List.of(),
            List.of(Pattern.SOLID),
            List.of(),
            FormalityLevel.CASUAL,
            List.of()
        );

        assertThatThrownBy(() -> service.updateStylePreferences(userId, request))
            .isInstanceOf(StylePreferencesValidationException.class)
            .hasMessageContaining("Maximum 10 color preferences allowed");
    }

    @Test
    @DisplayName("PUT: concurrent first-time creation safely falls back to updating the newly inserted record")
    void updateStylePreferences_ConcurrentInsert_ReconcilesGracefully() {
        UpdateStylePreferencesRequestDto request = new UpdateStylePreferencesRequestDto(
            PrimaryStyle.MINIMALIST,
            PreferredFit.REGULAR,
            List.of(Color.BLACK),
            List.of(Color.RED),
            List.of(Pattern.SOLID),
            List.of(Pattern.GRAPHIC),
            FormalityLevel.CASUAL,
            List.of(StyleGoal.MINIMAL_EFFORT)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stylePreferencesRepository.findByUserId(userId))
            .thenReturn(Optional.empty()) // initial check returns empty
            .thenReturn(Optional.of(StylePreferences.builder() // second check finds competing thread's row
                .id(UUID.randomUUID())
                .user(user)
                .primaryStyle("CASUAL")
                .profileCompleted(false)
                .colorPreferences(new ArrayList<>())
                .avoidedColors(new ArrayList<>())
                .patternPreferences(new ArrayList<>())
                .avoidedPatterns(new ArrayList<>())
                .styleGoals(new ArrayList<>())
                .build()));

        // Idempotent native insert returns 0 rows affected (concurrent winner)
        when(stylePreferencesRepository.insertStylePreferencesIdempotent(
            any(), eq(userId), anyString(), anyString(), anyString(),
            anyBoolean(), anyString(), anyString(), anyString(), anyString(), anyString(),
            any(), any()
        )).thenReturn(0);

        when(stylePreferencesRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StylePreferencesResponseDto response = service.updateStylePreferences(userId, request);

        assertThat(response.profileCompleted()).isTrue();
        assertThat(response.primaryStyle()).isEqualTo(PrimaryStyle.MINIMALIST);
        assertThat(response.colorPreferences()).containsExactly(Color.BLACK);
    }

    @Test
    @DisplayName("PATCH: unconfigured user partial patch creates record with profileCompleted=false")
    void patchStylePreferences_UnconfiguredUser_PreservesProfileCompletedFalse() {
        PatchStylePreferencesRequestDto request = new PatchStylePreferencesRequestDto(
            null,
            null,
            null,
            List.of(Color.YELLOW), // only supply avoidedColors
            null,
            null,
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stylePreferencesRepository.findByUserId(userId))
            .thenReturn(Optional.empty()) // initial check
            .thenReturn(Optional.of(StylePreferences.builder() // re-fetch after insert
                .id(UUID.randomUUID())
                .user(user)
                .primaryStyle("CASUAL")
                .preferredFit("REGULAR")
                .defaultFormality("CASUAL")
                .profileCompleted(false)
                .colorPreferences(new ArrayList<>())
                .avoidedColors(List.of("YELLOW"))
                .patternPreferences(new ArrayList<>())
                .avoidedPatterns(new ArrayList<>())
                .styleGoals(new ArrayList<>())
                .build()));

        when(stylePreferencesRepository.insertStylePreferencesIdempotent(
            any(), eq(userId), eq("CASUAL"), eq("REGULAR"), eq("CASUAL"),
            eq(false), // profileCompleted must remain false!
            anyString(), anyString(), anyString(), anyString(), anyString(),
            any(), any()
        )).thenReturn(1);

        StylePreferencesResponseDto response = service.patchStylePreferences(userId, request);

        assertThat(response.profileCompleted()).isFalse();
        assertThat(response.avoidedColors()).containsExactly(Color.YELLOW);
        assertThat(response.primaryStyle()).isEqualTo(PrimaryStyle.CASUAL);
    }

    @Test
    @DisplayName("PATCH: configured user partial patch preserves profileCompleted=true")
    void patchStylePreferences_ConfiguredUser_PreservesProfileCompletedTrue() {
        StylePreferences existing = StylePreferences.builder()
            .id(UUID.randomUUID())
            .user(user)
            .primaryStyle("SMART_CASUAL")
            .preferredFit("REGULAR")
            .defaultFormality("SMART_CASUAL")
            .profileCompleted(true)
            .colorPreferences(new ArrayList<>(List.of("NAVY", "WHITE")))
            .avoidedColors(new ArrayList<>(List.of("YELLOW")))
            .patternPreferences(new ArrayList<>(List.of("SOLID")))
            .avoidedPatterns(new ArrayList<>(List.of("FLORAL")))
            .styleGoals(new ArrayList<>(List.of("LOOK_SHARPER")))
            .build();

        PatchStylePreferencesRequestDto request = new PatchStylePreferencesRequestDto(
            null,
            PreferredFit.SLIM, // only update fit
            null,
            null,
            null,
            null,
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(stylePreferencesRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StylePreferencesResponseDto response = service.patchStylePreferences(userId, request);

        assertThat(response.profileCompleted()).isTrue();
        assertThat(response.preferredFit()).isEqualTo(PreferredFit.SLIM);
        assertThat(response.primaryStyle()).isEqualTo(PrimaryStyle.SMART_CASUAL);
        assertThat(response.colorPreferences()).containsExactly(Color.NAVY, Color.WHITE);
    }

    @Test
    @DisplayName("PATCH: supplying empty array clears that preference collection")
    void patchStylePreferences_EmptyArray_ClearsCollection() {
        StylePreferences existing = StylePreferences.builder()
            .id(UUID.randomUUID())
            .user(user)
            .primaryStyle("CASUAL")
            .profileCompleted(true)
            .colorPreferences(new ArrayList<>(List.of("NAVY", "WHITE")))
            .avoidedColors(new ArrayList<>(List.of("YELLOW")))
            .patternPreferences(new ArrayList<>(List.of("SOLID")))
            .avoidedPatterns(new ArrayList<>(List.of("FLORAL")))
            .styleGoals(new ArrayList<>(List.of("LOOK_SHARPER")))
            .build();

        PatchStylePreferencesRequestDto request = new PatchStylePreferencesRequestDto(
            null,
            null,
            List.of(), // explicitly empty array -> clears collection
            null,
            null,
            null,
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(stylePreferencesRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StylePreferencesResponseDto response = service.patchStylePreferences(userId, request);

        assertThat(response.colorPreferences()).isEmpty();
        assertThat(response.avoidedColors()).containsExactly(Color.YELLOW);
        assertThat(response.profileCompleted()).isTrue();
    }

    @Test
    @DisplayName("PATCH: rejects updating avoidedColors to a color already in existing colorPreferences")
    void patchStylePreferences_IntersectionWithExistingColors_ThrowsException() {
        StylePreferences existing = StylePreferences.builder()
            .id(UUID.randomUUID())
            .user(user)
            .colorPreferences(List.of("NAVY", "WHITE"))
            .avoidedColors(List.of("YELLOW"))
            .build();

        PatchStylePreferencesRequestDto request = new PatchStylePreferencesRequestDto(
            null,
            null,
            null,
            List.of("NAVY".equalsIgnoreCase("NAVY") ? Color.NAVY : Color.BLUE), // NAVY already in existing preferred
            null,
            null,
            null,
            null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stylePreferencesRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.patchStylePreferences(userId, request))
            .isInstanceOf(StylePreferencesValidationException.class)
            .hasMessageContaining("Color cannot be both preferred and avoided: NAVY");
    }
}
