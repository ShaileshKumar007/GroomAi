package com.groomai.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.common.exception.GroomAiException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of StylePreferencesService providing deterministic validation,
 * idempotent persistence, and strict completion semantics.
 */
@Service
@Transactional(readOnly = true)
public class StylePreferencesServiceImpl implements StylePreferencesService {

    private static final Logger log = LoggerFactory.getLogger(StylePreferencesServiceImpl.class);

    private final StylePreferencesRepository stylePreferencesRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public StylePreferencesServiceImpl(
        StylePreferencesRepository stylePreferencesRepository,
        UserRepository userRepository,
        ObjectMapper objectMapper
    ) {
        this.stylePreferencesRepository = stylePreferencesRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public StylePreferencesResponseDto getStylePreferences(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return stylePreferencesRepository.findByUserId(userId)
            .map(StylePreferencesResponseDto::fromEntity)
            .orElseGet(StylePreferencesResponseDto::unconfigured);
    }

    @Override
    @Transactional
    public StylePreferencesResponseDto updateStylePreferences(UUID userId, UpdateStylePreferencesRequestDto request) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        validateUpdateRequest(request);

        // Verify user exists
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GroomAiException("User not found: " + userId, HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        Instant now = Instant.now();
        List<String> colors = request.colorPreferences().stream().map(Enum::name).toList();
        List<String> avoidedColors = request.avoidedColors().stream().map(Enum::name).toList();
        List<String> patterns = request.patternPreferences().stream().map(Enum::name).toList();
        List<String> avoidedPatterns = request.avoidedPatterns().stream().map(Enum::name).toList();
        List<String> goals = request.styleGoals().stream().map(Enum::name).toList();

        Optional<StylePreferences> existingOpt = stylePreferencesRepository.findByUserId(userId);
        if (existingOpt.isPresent()) {
            StylePreferences existing = existingOpt.get();
            existing.setPrimaryStyle(request.primaryStyle().name());
            existing.setPreferredFit(request.preferredFit().name());
            existing.setDefaultFormality(request.defaultFormality().name());
            existing.setColorPreferences(new ArrayList<>(colors));
            existing.setAvoidedColors(new ArrayList<>(avoidedColors));
            existing.setPatternPreferences(new ArrayList<>(patterns));
            existing.setAvoidedPatterns(new ArrayList<>(avoidedPatterns));
            existing.setStyleGoals(new ArrayList<>(goals));
            existing.setProfileCompleted(true);
            existing.setUpdatedAt(now);

            StylePreferences saved = stylePreferencesRepository.saveAndFlush(existing);
            log.info("Updated style preferences for user [{}], profileCompleted=true", userId);
            return StylePreferencesResponseDto.fromEntity(saved);
        }

        // Idempotent first-time insertion
        UUID newId = UUID.randomUUID();
        int rowsInserted = stylePreferencesRepository.insertStylePreferencesIdempotent(
            newId,
            userId,
            request.primaryStyle().name(),
            request.preferredFit().name(),
            request.defaultFormality().name(),
            true, // profileCompleted = true
            toJson(colors),
            toJson(avoidedColors),
            toJson(patterns),
            toJson(avoidedPatterns),
            toJson(goals),
            now,
            now
        );

        if (rowsInserted == 0) {
            // Concurrent race won by another thread; update the newly inserted row
            StylePreferences competing = stylePreferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new GroomAiException("Concurrent style preferences conflict", HttpStatus.CONFLICT, "CONCURRENT_CONFLICT"));

            competing.setPrimaryStyle(request.primaryStyle().name());
            competing.setPreferredFit(request.preferredFit().name());
            competing.setDefaultFormality(request.defaultFormality().name());
            competing.setColorPreferences(new ArrayList<>(colors));
            competing.setAvoidedColors(new ArrayList<>(avoidedColors));
            competing.setPatternPreferences(new ArrayList<>(patterns));
            competing.setAvoidedPatterns(new ArrayList<>(avoidedPatterns));
            competing.setStyleGoals(new ArrayList<>(goals));
            competing.setProfileCompleted(true);
            competing.setUpdatedAt(now);

            StylePreferences saved = stylePreferencesRepository.saveAndFlush(competing);
            log.info("Reconciled concurrent style preferences insert for user [{}], profileCompleted=true", userId);
            return StylePreferencesResponseDto.fromEntity(saved);
        }

        StylePreferences saved = stylePreferencesRepository.findByUserId(userId)
            .orElseThrow(() -> new GroomAiException("Failed to retrieve created style preferences", HttpStatus.INTERNAL_SERVER_ERROR, "RETRIEVAL_ERROR"));
        log.info("Created style preferences for user [{}], profileCompleted=true", userId);
        return StylePreferencesResponseDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public StylePreferencesResponseDto patchStylePreferences(UUID userId, PatchStylePreferencesRequestDto request) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        // Verify user exists
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GroomAiException("User not found: " + userId, HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        Optional<StylePreferences> existingOpt = stylePreferencesRepository.findByUserId(userId);
        StylePreferences existing = existingOpt.orElse(null);

        validatePatchRequest(request, existing);

        Instant now = Instant.now();

        if (existing != null) {
            if (request.primaryStyle() != null) {
                existing.setPrimaryStyle(request.primaryStyle().name());
            }
            if (request.preferredFit() != null) {
                existing.setPreferredFit(request.preferredFit().name());
            }
            if (request.defaultFormality() != null) {
                existing.setDefaultFormality(request.defaultFormality().name());
            }
            if (request.colorPreferences() != null) {
                existing.setColorPreferences(new ArrayList<>(request.colorPreferences().stream().map(Enum::name).toList()));
            }
            if (request.avoidedColors() != null) {
                existing.setAvoidedColors(new ArrayList<>(request.avoidedColors().stream().map(Enum::name).toList()));
            }
            if (request.patternPreferences() != null) {
                existing.setPatternPreferences(new ArrayList<>(request.patternPreferences().stream().map(Enum::name).toList()));
            }
            if (request.avoidedPatterns() != null) {
                existing.setAvoidedPatterns(new ArrayList<>(request.avoidedPatterns().stream().map(Enum::name).toList()));
            }
            if (request.styleGoals() != null) {
                existing.setStyleGoals(new ArrayList<>(request.styleGoals().stream().map(Enum::name).toList()));
            }
            // IMPORTANT: profileCompleted is preserved as-is!
            existing.setUpdatedAt(now);

            StylePreferences saved = stylePreferencesRepository.saveAndFlush(existing);
            log.info("Patched style preferences for user [{}], profileCompleted preserved as [{}]", userId, saved.isProfileCompleted());
            return StylePreferencesResponseDto.fromEntity(saved);
        }

        // Unconfigured user: create new record with defaults for omitted fields and profileCompleted=false
        String primaryStyle = request.primaryStyle() != null ? request.primaryStyle().name() : PrimaryStyle.CASUAL.name();
        String preferredFit = request.preferredFit() != null ? request.preferredFit().name() : PreferredFit.REGULAR.name();
        String defaultFormality = request.defaultFormality() != null ? request.defaultFormality().name() : FormalityLevel.CASUAL.name();
        List<String> colors = request.colorPreferences() != null ? request.colorPreferences().stream().map(Enum::name).toList() : List.of();
        List<String> avoidedColors = request.avoidedColors() != null ? request.avoidedColors().stream().map(Enum::name).toList() : List.of();
        List<String> patterns = request.patternPreferences() != null ? request.patternPreferences().stream().map(Enum::name).toList() : List.of();
        List<String> avoidedPatterns = request.avoidedPatterns() != null ? request.avoidedPatterns().stream().map(Enum::name).toList() : List.of();
        List<String> goals = request.styleGoals() != null ? request.styleGoals().stream().map(Enum::name).toList() : List.of();

        UUID newId = UUID.randomUUID();
        int rowsInserted = stylePreferencesRepository.insertStylePreferencesIdempotent(
            newId,
            userId,
            primaryStyle,
            preferredFit,
            defaultFormality,
            false, // profileCompleted remains false for partial patch on unconfigured user!
            toJson(colors),
            toJson(avoidedColors),
            toJson(patterns),
            toJson(avoidedPatterns),
            toJson(goals),
            now,
            now
        );

        if (rowsInserted == 0) {
            // Concurrent race won by another thread; apply patch to existing row
            StylePreferences competing = stylePreferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new GroomAiException("Concurrent style preferences conflict", HttpStatus.CONFLICT, "CONCURRENT_CONFLICT"));

            if (request.primaryStyle() != null) competing.setPrimaryStyle(request.primaryStyle().name());
            if (request.preferredFit() != null) competing.setPreferredFit(request.preferredFit().name());
            if (request.defaultFormality() != null) competing.setDefaultFormality(request.defaultFormality().name());
            if (request.colorPreferences() != null) competing.setColorPreferences(new ArrayList<>(request.colorPreferences().stream().map(Enum::name).toList()));
            if (request.avoidedColors() != null) competing.setAvoidedColors(new ArrayList<>(request.avoidedColors().stream().map(Enum::name).toList()));
            if (request.patternPreferences() != null) competing.setPatternPreferences(new ArrayList<>(request.patternPreferences().stream().map(Enum::name).toList()));
            if (request.avoidedPatterns() != null) competing.setAvoidedPatterns(new ArrayList<>(request.avoidedPatterns().stream().map(Enum::name).toList()));
            if (request.styleGoals() != null) competing.setStyleGoals(new ArrayList<>(request.styleGoals().stream().map(Enum::name).toList()));
            competing.setUpdatedAt(now);

            StylePreferences saved = stylePreferencesRepository.saveAndFlush(competing);
            log.info("Reconciled concurrent style preferences patch for user [{}], profileCompleted preserved as [{}]", userId, saved.isProfileCompleted());
            return StylePreferencesResponseDto.fromEntity(saved);
        }

        StylePreferences saved = stylePreferencesRepository.findByUserId(userId)
            .orElseThrow(() -> new GroomAiException("Failed to retrieve created style preferences", HttpStatus.INTERNAL_SERVER_ERROR, "RETRIEVAL_ERROR"));
        log.info("Created partial style preferences for unconfigured user [{}], profileCompleted=false", userId);
        return StylePreferencesResponseDto.fromEntity(saved);
    }

    private void validateUpdateRequest(UpdateStylePreferencesRequestDto request) {
        if (request.primaryStyle() == null) {
            throw new StylePreferencesValidationException("primaryStyle is required.");
        }
        if (request.preferredFit() == null) {
            throw new StylePreferencesValidationException("preferredFit is required.");
        }
        if (request.defaultFormality() == null) {
            throw new StylePreferencesValidationException("defaultFormality is required.");
        }
        if (request.colorPreferences() == null) {
            throw new StylePreferencesValidationException("colorPreferences is required.");
        }
        if (request.avoidedColors() == null) {
            throw new StylePreferencesValidationException("avoidedColors is required.");
        }
        if (request.patternPreferences() == null) {
            throw new StylePreferencesValidationException("patternPreferences is required.");
        }
        if (request.avoidedPatterns() == null) {
            throw new StylePreferencesValidationException("avoidedPatterns is required.");
        }
        if (request.styleGoals() == null) {
            throw new StylePreferencesValidationException("styleGoals is required.");
        }

        // Collection bounds
        if (request.colorPreferences().size() > 10) {
            throw new StylePreferencesValidationException("Maximum 10 color preferences allowed.");
        }
        if (request.avoidedColors().size() > 10) {
            throw new StylePreferencesValidationException("Maximum 10 avoided colors allowed.");
        }
        if (request.patternPreferences().size() > 5) {
            throw new StylePreferencesValidationException("Maximum 5 pattern preferences allowed.");
        }
        if (request.avoidedPatterns().size() > 5) {
            throw new StylePreferencesValidationException("Maximum 5 avoided patterns allowed.");
        }
        if (request.styleGoals().size() > 3) {
            throw new StylePreferencesValidationException("Maximum 3 style goals allowed.");
        }

        // Check null elements
        if (request.colorPreferences().stream().anyMatch(Objects::isNull)) {
            throw new StylePreferencesValidationException("colorPreferences must not contain null elements.");
        }
        if (request.avoidedColors().stream().anyMatch(Objects::isNull)) {
            throw new StylePreferencesValidationException("avoidedColors must not contain null elements.");
        }
        if (request.patternPreferences().stream().anyMatch(Objects::isNull)) {
            throw new StylePreferencesValidationException("patternPreferences must not contain null elements.");
        }
        if (request.avoidedPatterns().stream().anyMatch(Objects::isNull)) {
            throw new StylePreferencesValidationException("avoidedPatterns must not contain null elements.");
        }
        if (request.styleGoals().stream().anyMatch(Objects::isNull)) {
            throw new StylePreferencesValidationException("styleGoals must not contain null elements.");
        }

        // Duplicate checks
        Set<Color> colorSet = new HashSet<>(request.colorPreferences());
        if (colorSet.size() < request.colorPreferences().size()) {
            throw new StylePreferencesValidationException("colorPreferences must not contain duplicate colors.");
        }

        Set<Color> avoidedColorSet = new HashSet<>(request.avoidedColors());
        if (avoidedColorSet.size() < request.avoidedColors().size()) {
            throw new StylePreferencesValidationException("avoidedColors must not contain duplicate colors.");
        }

        // Color intersection check
        for (Color c : request.avoidedColors()) {
            if (colorSet.contains(c)) {
                throw new StylePreferencesValidationException("Color cannot be both preferred and avoided: " + c);
            }
        }

        Set<Pattern> patternSet = new HashSet<>(request.patternPreferences());
        if (patternSet.size() < request.patternPreferences().size()) {
            throw new StylePreferencesValidationException("patternPreferences must not contain duplicate patterns.");
        }

        Set<Pattern> avoidedPatternSet = new HashSet<>(request.avoidedPatterns());
        if (avoidedPatternSet.size() < request.avoidedPatterns().size()) {
            throw new StylePreferencesValidationException("avoidedPatterns must not contain duplicate patterns.");
        }

        // Pattern intersection check
        for (Pattern p : request.avoidedPatterns()) {
            if (patternSet.contains(p)) {
                throw new StylePreferencesValidationException("Pattern cannot be both preferred and avoided: " + p);
            }
        }

        Set<StyleGoal> goalSet = new HashSet<>(request.styleGoals());
        if (goalSet.size() < request.styleGoals().size()) {
            throw new StylePreferencesValidationException("styleGoals must not contain duplicate goals.");
        }
    }

    private void validatePatchRequest(PatchStylePreferencesRequestDto request, StylePreferences existing) {
        // Validate individual supplied fields
        if (request.colorPreferences() != null) {
            if (request.colorPreferences().size() > 10) {
                throw new StylePreferencesValidationException("Maximum 10 color preferences allowed.");
            }
            if (request.colorPreferences().stream().anyMatch(Objects::isNull)) {
                throw new StylePreferencesValidationException("colorPreferences must not contain null elements.");
            }
            Set<Color> set = new HashSet<>(request.colorPreferences());
            if (set.size() < request.colorPreferences().size()) {
                throw new StylePreferencesValidationException("colorPreferences must not contain duplicate colors.");
            }
        }

        if (request.avoidedColors() != null) {
            if (request.avoidedColors().size() > 10) {
                throw new StylePreferencesValidationException("Maximum 10 avoided colors allowed.");
            }
            if (request.avoidedColors().stream().anyMatch(Objects::isNull)) {
                throw new StylePreferencesValidationException("avoidedColors must not contain null elements.");
            }
            Set<Color> set = new HashSet<>(request.avoidedColors());
            if (set.size() < request.avoidedColors().size()) {
                throw new StylePreferencesValidationException("avoidedColors must not contain duplicate colors.");
            }
        }

        // Check resulting effective color intersection
        List<Color> effectivePreferredColors = request.colorPreferences() != null
            ? request.colorPreferences()
            : parseColors(existing != null ? existing.getColorPreferences() : null);

        List<Color> effectiveAvoidedColors = request.avoidedColors() != null
            ? request.avoidedColors()
            : parseColors(existing != null ? existing.getAvoidedColors() : null);

        Set<Color> preferredColorSet = new HashSet<>(effectivePreferredColors);
        for (Color c : effectiveAvoidedColors) {
            if (preferredColorSet.contains(c)) {
                throw new StylePreferencesValidationException("Color cannot be both preferred and avoided: " + c);
            }
        }

        if (request.patternPreferences() != null) {
            if (request.patternPreferences().size() > 5) {
                throw new StylePreferencesValidationException("Maximum 5 pattern preferences allowed.");
            }
            if (request.patternPreferences().stream().anyMatch(Objects::isNull)) {
                throw new StylePreferencesValidationException("patternPreferences must not contain null elements.");
            }
            Set<Pattern> set = new HashSet<>(request.patternPreferences());
            if (set.size() < request.patternPreferences().size()) {
                throw new StylePreferencesValidationException("patternPreferences must not contain duplicate patterns.");
            }
        }

        if (request.avoidedPatterns() != null) {
            if (request.avoidedPatterns().size() > 5) {
                throw new StylePreferencesValidationException("Maximum 5 avoided patterns allowed.");
            }
            if (request.avoidedPatterns().stream().anyMatch(Objects::isNull)) {
                throw new StylePreferencesValidationException("avoidedPatterns must not contain null elements.");
            }
            Set<Pattern> set = new HashSet<>(request.avoidedPatterns());
            if (set.size() < request.avoidedPatterns().size()) {
                throw new StylePreferencesValidationException("avoidedPatterns must not contain duplicate patterns.");
            }
        }

        // Check resulting effective pattern intersection
        List<Pattern> effectivePreferredPatterns = request.patternPreferences() != null
            ? request.patternPreferences()
            : parsePatterns(existing != null ? existing.getPatternPreferences() : null);

        List<Pattern> effectiveAvoidedPatterns = request.avoidedPatterns() != null
            ? request.avoidedPatterns()
            : parsePatterns(existing != null ? existing.getAvoidedPatterns() : null);

        Set<Pattern> preferredPatternSet = new HashSet<>(effectivePreferredPatterns);
        for (Pattern p : effectiveAvoidedPatterns) {
            if (preferredPatternSet.contains(p)) {
                throw new StylePreferencesValidationException("Pattern cannot be both preferred and avoided: " + p);
            }
        }

        if (request.styleGoals() != null) {
            if (request.styleGoals().size() > 3) {
                throw new StylePreferencesValidationException("Maximum 3 style goals allowed.");
            }
            if (request.styleGoals().stream().anyMatch(Objects::isNull)) {
                throw new StylePreferencesValidationException("styleGoals must not contain null elements.");
            }
            Set<StyleGoal> set = new HashSet<>(request.styleGoals());
            if (set.size() < request.styleGoals().size()) {
                throw new StylePreferencesValidationException("styleGoals must not contain duplicate goals.");
            }
        }
    }

    private static List<Color> parseColors(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
            .map(s -> {
                try {
                    return Color.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private static List<Pattern> parsePatterns(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
            .map(s -> {
                try {
                    return Pattern.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", obj, e);
            throw new GroomAiException("Internal JSON serialization error", HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_ERROR");
        }
    }
}
