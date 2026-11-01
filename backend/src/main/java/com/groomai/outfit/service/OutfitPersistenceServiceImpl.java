package com.groomai.outfit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groomai.common.exception.GroomAiException;
import com.groomai.outfit.dto.LogOutfitWearRequestDto;
import com.groomai.outfit.dto.LogOutfitWearResponseDto;
import com.groomai.outfit.dto.OutfitItemDto;
import com.groomai.outfit.dto.OutfitWearHistoryDto;
import com.groomai.outfit.dto.SaveOutfitRequestDto;
import com.groomai.outfit.dto.SavedOutfitResponseDto;
import com.groomai.outfit.entity.Outfit;
import com.groomai.outfit.entity.OutfitItem;
import com.groomai.outfit.entity.OutfitWearLog;
import com.groomai.outfit.enums.ScoreStatus;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.outfit.model.ScoredOutfit;
import com.groomai.outfit.model.WearLogSnapshot;
import com.groomai.outfit.repository.OutfitItemRepository;
import com.groomai.outfit.repository.OutfitRepository;
import com.groomai.outfit.repository.OutfitWearLogRepository;
import com.groomai.outfit.scorer.OutfitScoringEngine;
import com.groomai.storage.ObjectStorageService;
import com.groomai.user.entity.StylePreferences;
import com.groomai.user.entity.User;
import com.groomai.user.repository.StylePreferencesRepository;
import com.groomai.user.repository.UserRepository;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of OutfitPersistenceService.
 * Enforces transactional safety, tenant isolation, authoritative backend scoring,
 * immutable garment/outfit snapshots, and idempotent wear logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutfitPersistenceServiceImpl implements OutfitPersistenceService {

    private final OutfitRepository outfitRepository;
    private final OutfitItemRepository outfitItemRepository;
    private final OutfitWearLogRepository outfitWearLogRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;
    private final StylePreferencesRepository stylePreferencesRepository;
    private final ObjectStorageService objectStorageService;
    private final OutfitScoringEngine outfitScoringEngine;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SavedOutfitResponseDto saveOutfit(UUID userId, SaveOutfitRequestDto request) {
        if (request.itemIds() == null || request.itemIds().size() < 3 || request.itemIds().size() > 5) {
            throw new GroomAiException(
                "Outfit must contain between 3 and 5 items",
                HttpStatus.BAD_REQUEST,
                "INVALID_ITEM_COUNT"
            );
        }

        // Prevent duplicate item references in a single outfit
        Set<UUID> uniqueIds = new HashSet<>(request.itemIds());
        if (uniqueIds.size() != request.itemIds().size()) {
            throw new GroomAiException(
                "An outfit cannot contain duplicate wardrobe items",
                HttpStatus.BAD_REQUEST,
                "DUPLICATE_ITEMS_IN_OUTFIT"
            );
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GroomAiException(
                "User not found: " + userId,
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND"
            ));

        // Fetch candidate items and enforce tenant isolation & active status
        List<WardrobeItem> items = wardrobeItemRepository.findAllById(request.itemIds());

        if (items.size() != request.itemIds().size()) {
            throw new GroomAiException(
                "One or more wardrobe items could not be found",
                HttpStatus.BAD_REQUEST,
                "INVALID_WARDROBE_ITEMS"
            );
        }

        for (WardrobeItem item : items) {
            if (item.getUser() == null || !userId.equals(item.getUser().getId())) {
                log.warn("Security violation: user [{}] attempted to save item [{}] belonging to another user",
                    userId, item.getId());
                throw new GroomAiException(
                    "You do not own all submitted wardrobe items",
                    HttpStatus.FORBIDDEN,
                    "UNAUTHORIZED_ITEM_ACCESS"
                );
            }
            if (item.getStatus() != ItemStatus.ACTIVE) {
                throw new GroomAiException(
                    "Cannot add inactive or archived item [" + item.getName() + "] to an outfit",
                    HttpStatus.BAD_REQUEST,
                    "INACTIVE_ITEM"
                );
            }
        }

        // Validate composition: exactly 1 TOP, 1 BOTTOM, 1 FOOTWEAR; at most 1 OUTERWEAR, 1 ACCESSORY
        WardrobeItem top = items.stream().filter(i -> i.getCategory() == Category.TOP).findFirst().orElse(null);
        WardrobeItem bottom = items.stream().filter(i -> i.getCategory() == Category.BOTTOM).findFirst().orElse(null);
        WardrobeItem footwear = items.stream().filter(i -> i.getCategory() == Category.FOOTWEAR).findFirst().orElse(null);
        WardrobeItem outerwear = items.stream().filter(i -> i.getCategory() == Category.OUTERWEAR).findFirst().orElse(null);
        WardrobeItem accessory = items.stream().filter(i -> i.getCategory() == Category.ACCESSORY).findFirst().orElse(null);

        long topCount = items.stream().filter(i -> i.getCategory() == Category.TOP).count();
        long bottomCount = items.stream().filter(i -> i.getCategory() == Category.BOTTOM).count();
        long footwearCount = items.stream().filter(i -> i.getCategory() == Category.FOOTWEAR).count();
        long outerwearCount = items.stream().filter(i -> i.getCategory() == Category.OUTERWEAR).count();
        long accessoryCount = items.stream().filter(i -> i.getCategory() == Category.ACCESSORY).count();

        if (topCount != 1 || bottomCount != 1 || footwearCount != 1 || outerwearCount > 1 || accessoryCount > 1
                || top == null || bottom == null || footwear == null) {
            throw new GroomAiException(
                "Outfit must contain exactly one Top, one Bottom, and one Footwear, with at most one Outerwear and one Accessory",
                HttpStatus.BAD_REQUEST,
                "INVALID_OUTFIT_COMPOSITION"
            );
        }

        // Authoritative backend scoring: never trust client-submitted scores
        CandidateOutfit candidate = new CandidateOutfit(top, bottom, footwear, outerwear, accessory);
        StylePreferences preferences = stylePreferencesRepository.findByUserId(userId).orElse(null);
        Map<UUID, Integer> wearDaysMap = getRecentItemWearDays(userId);

        Optional<ScoredOutfit> scoredOpt = outfitScoringEngine.scoreOutfit(
            candidate,
            request.occasion(),
            request.temperatureBracket(),
            request.weatherCondition(),
            preferences,
            wearDaysMap
        );

        Integer finalScore;
        ScoreStatus finalScoreStatus;
        String finalRationale;

        if (scoredOpt.isPresent()) {
            ScoredOutfit scored = scoredOpt.get();
            finalScore = scored.suitabilityScore();
            finalScoreStatus = ScoreStatus.CALCULATED;
            finalRationale = (request.stylingRationale() != null && !request.stylingRationale().isBlank())
                ? request.stylingRationale().trim()
                : scored.stylingRationale();
        } else {
            // User curated outfit that does not pass occasion/weather compatibility gates
            finalScore = null;
            finalScoreStatus = ScoreStatus.CUSTOM;
            finalRationale = (request.stylingRationale() != null && !request.stylingRationale().isBlank())
                ? request.stylingRationale().trim()
                : "Custom curated outfit combination for " + request.occasion();
        }

        String outfitName = (request.name() != null && !request.name().isBlank())
            ? request.name().trim()
            : null;

        Outfit outfit = Outfit.builder()
            .user(user)
            .name(outfitName)
            .occasion(request.occasion())
            .temperatureBracket(request.temperatureBracket())
            .weatherCondition(request.weatherCondition())
            .stylingRationale(finalRationale)
            .suitabilityScore(finalScore)
            .scoreStatus(finalScoreStatus)
            .build();

        for (WardrobeItem item : items) {
            OutfitItem outfitItem = OutfitItem.builder()
                .outfit(outfit)
                .wardrobeItem(item)
                .itemRole(item.getCategory().name())
                .category(item.getCategory())
                .subcategory(item.getSubcategory())
                .primaryColor(item.getPrimaryColor())
                .secondaryColor(item.getSecondaryColor())
                .pattern(item.getPattern())
                .originalStorageKey(item.getStorageKey())
                .build();
            outfit.addItem(outfitItem);
        }

        outfit = outfitRepository.save(outfit);
        log.info("Saved outfit [{}] with status [{}] and [{}] items for user [{}]",
            outfit.getId(), finalScoreStatus, items.size(), userId);

        return mapToSavedOutfitDto(outfit);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SavedOutfitResponseDto> getSavedOutfits(UUID userId, Pageable pageable) {
        return outfitRepository.findByUserId(userId, pageable)
            .map(this::mapToSavedOutfitDto);
    }

    @Override
    @Transactional(readOnly = true)
    public SavedOutfitResponseDto getSavedOutfitById(UUID userId, UUID outfitId) {
        Outfit outfit = outfitRepository.findByIdAndUserId(outfitId, userId)
            .orElseThrow(() -> new GroomAiException(
                "Outfit not found: " + outfitId,
                HttpStatus.NOT_FOUND,
                "OUTFIT_NOT_FOUND"
            ));
        return mapToSavedOutfitDto(outfit);
    }

    @Override
    @Transactional
    public LogOutfitWearResponseDto logOutfitWear(UUID userId, UUID outfitId, LogOutfitWearRequestDto request) {
        Outfit outfit = outfitRepository.findByIdAndUserId(outfitId, userId)
            .orElseThrow(() -> new GroomAiException(
                "Outfit not found: " + outfitId,
                HttpStatus.NOT_FOUND,
                "OUTFIT_NOT_FOUND"
            ));

        ZoneId zoneId = resolveZoneId(request != null ? request.timezone() : null);
        LocalDate todayInZone = LocalDate.now(zoneId);

        LocalDate wornDate = (request != null && request.wornDate() != null)
            ? request.wornDate()
            : todayInZone;

        if (wornDate.isAfter(todayInZone)) {
            throw new GroomAiException(
                "Cannot log outfit wear for future dates",
                HttpStatus.BAD_REQUEST,
                "INVALID_WEAR_DATE"
            );
        }

        // Build versioned immutable snapshot of the outfit and all its garments
        List<WearLogSnapshot.WearLogItemSnapshot> itemSnapshots = new ArrayList<>();
        if (outfit.getItems() != null) {
            for (OutfitItem oi : outfit.getItems()) {
                WardrobeItem wi = oi.getWardrobeItem();
                itemSnapshots.add(new WearLogSnapshot.WearLogItemSnapshot(
                    wi != null ? wi.getId() : null,
                    oi.getItemRole(),
                    oi.getCategory() != null ? oi.getCategory() : (wi != null ? wi.getCategory() : null),
                    oi.getSubcategory() != null ? oi.getSubcategory() : (wi != null ? wi.getSubcategory() : null),
                    oi.getPrimaryColor() != null ? oi.getPrimaryColor() : (wi != null ? wi.getPrimaryColor() : null),
                    oi.getSecondaryColor() != null ? oi.getSecondaryColor() : (wi != null ? wi.getSecondaryColor() : null),
                    oi.getPattern() != null ? oi.getPattern() : (wi != null ? wi.getPattern() : null),
                    oi.getFabric(),
                    oi.getBrand(),
                    oi.getOriginalStorageKey() != null ? oi.getOriginalStorageKey() : (wi != null ? wi.getStorageKey() : null)
                ));
            }
        }

        WearLogSnapshot snapshot = new WearLogSnapshot(
            1,
            outfit.getId(),
            outfit.getName(),
            outfit.getOccasion(),
            outfit.getTemperatureBracket(),
            outfit.getWeatherCondition(),
            outfit.getStylingRationale(),
            outfit.getSuitabilityScore(),
            outfit.getScoreStatus() != null ? outfit.getScoreStatus() : ScoreStatus.UNSCORED,
            itemSnapshots
        );

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WearLogSnapshot to JSON for outfit [{}]", outfitId, e);
            throw new GroomAiException("Internal wear log serialization failure", HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_ERROR");
        }

        UUID newLogId = UUID.randomUUID();
        Instant now = Instant.now();
        String notes = (request != null) ? request.notes() : null;

        // Atomic idempotent insertion: prevent duplicate logs via database unique constraint without transaction rollback hazard
        int rowsInserted = outfitWearLogRepository.insertWearLogIdempotent(
            newLogId,
            userId,
            outfitId,
            wornDate,
            notes,
            now,
            snapshotJson
        );

        if (rowsInserted == 0) {
            // Record already exists for (userId, outfitId, wornDate); return idempotent confirmation for existing record
            OutfitWearLog existing = outfitWearLogRepository.findByUserIdAndOutfitIdAndWornDate(userId, outfitId, wornDate)
                .orElseThrow(() -> new GroomAiException("Concurrent wear log conflict", HttpStatus.CONFLICT, "WEAR_LOG_CONFLICT"));

            log.info("Outfit [{}] was already recorded as worn on [{}] for user [{}]; returning idempotent confirmation for log [{}]",
                outfitId, wornDate, userId, existing.getId());

            return new LogOutfitWearResponseDto(
                existing.getId(),
                outfitId,
                wornDate,
                "Outfit wear was already recorded for this date. Repetition penalty is active for recommendations."
            );
        }

        log.info("Recorded wear log [{}] for outfit [{}] on date [{}] with snapshot", newLogId, outfitId, wornDate);

        return new LogOutfitWearResponseDto(
            newLogId,
            outfitId,
            wornDate,
            "Outfit wear successfully logged. Repetition penalty applied for upcoming recommendations."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OutfitWearHistoryDto> getWearHistory(UUID userId, Pageable pageable) {
        return outfitWearLogRepository.findByUserId(userId, pageable)
            .map(this::mapToWearHistoryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> getRecentItemWearDays(UUID userId) {
        List<Object[]> rows = outfitWearLogRepository.findLatestWornDatesByUserId(userId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<UUID, Integer> wearDaysMap = new HashMap<>();
        LocalDate today = LocalDate.now();

        for (Object[] row : rows) {
            UUID itemId = (UUID) row[0];
            LocalDate latestWornDate = (LocalDate) row[1];
            if (itemId != null && latestWornDate != null) {
                int days = (int) Math.max(0, ChronoUnit.DAYS.between(latestWornDate, today));
                wearDaysMap.put(itemId, days);
            }
        }

        return wearDaysMap;
    }

    private SavedOutfitResponseDto mapToSavedOutfitDto(Outfit outfit) {
        List<OutfitItemDto> itemDtos = new ArrayList<>();
        if (outfit.getItems() != null) {
            for (OutfitItem oi : outfit.getItems()) {
                itemDtos.add(mapToOutfitItemDto(oi));
            }
        }

        return new SavedOutfitResponseDto(
            outfit.getId(),
            outfit.getName(),
            outfit.getOccasion(),
            outfit.getTemperatureBracket(),
            outfit.getWeatherCondition(),
            outfit.getStylingRationale(),
            outfit.getSuitabilityScore(),
            outfit.getScoreStatus(),
            outfit.getCreatedAt(),
            itemDtos
        );
    }

    private OutfitWearHistoryDto mapToWearHistoryDto(OutfitWearLog wearLog) {
        WearLogSnapshot snapshot = wearLog.getSnapshot();
        if (snapshot != null) {
            // Authoritative historical rendering from immutable snapshot
            List<OutfitItemDto> items = new ArrayList<>();
            if (snapshot.items() != null) {
                for (WearLogSnapshot.WearLogItemSnapshot is : snapshot.items()) {
                    String downloadUrl = null;
                    String name = (is.subcategory() != null && !is.subcategory().isBlank())
                        ? is.subcategory()
                        : (is.category() != null ? is.category().name() : "Garment");

                    if (is.wardrobeItemId() != null) {
                        var liveItemOpt = wardrobeItemRepository.findById(is.wardrobeItemId());
                        if (liveItemOpt.isPresent() && liveItemOpt.get().getStatus() == ItemStatus.ACTIVE) {
                            WardrobeItem liveItem = liveItemOpt.get();
                            name = liveItem.getName();
                            if (liveItem.getStorageKey() != null) {
                                downloadUrl = objectStorageService.generateDownloadUrl(liveItem.getStorageKey(), Duration.ofMinutes(15));
                            }
                        }
                    }

                    items.add(new OutfitItemDto(
                        is.itemRole(),
                        is.wardrobeItemId(),
                        name,
                        is.category(),
                        is.primaryColor(),
                        is.secondaryColor(),
                        is.pattern(),
                        null,
                        null,
                        downloadUrl
                    ));
                }
            }

            return new OutfitWearHistoryDto(
                wearLog.getId(),
                snapshot.outfitId(),
                snapshot.name(),
                wearLog.getWornDate(),
                wearLog.getNotes(),
                wearLog.getCreatedAt(),
                snapshot.occasion(),
                snapshot.suitabilityScore(),
                snapshot.scoreStatus(),
                snapshot.stylingRationale(),
                items
            );
        }

        // Fallback for pre-V3 wear logs without snapshot
        Outfit outfit = wearLog.getOutfit();
        List<OutfitItemDto> items = new ArrayList<>();

        if (outfit != null && outfit.getItems() != null) {
            for (OutfitItem oi : outfit.getItems()) {
                items.add(mapToOutfitItemDto(oi));
            }
        }

        return new OutfitWearHistoryDto(
            wearLog.getId(),
            outfit != null ? outfit.getId() : null,
            outfit != null ? outfit.getName() : null,
            wearLog.getWornDate(),
            wearLog.getNotes(),
            wearLog.getCreatedAt(),
            outfit != null ? outfit.getOccasion() : null,
            outfit != null ? outfit.getSuitabilityScore() : null,
            outfit != null ? outfit.getScoreStatus() : ScoreStatus.UNSCORED,
            outfit != null ? outfit.getStylingRationale() : "Historical wear record",
            items
        );
    }

    private OutfitItemDto mapToOutfitItemDto(OutfitItem oi) {
        WardrobeItem wi = oi.getWardrobeItem();
        if (wi != null) {
            String downloadUrl = null;
            if (wi.getStorageKey() != null) {
                downloadUrl = objectStorageService.generateDownloadUrl(wi.getStorageKey(), Duration.ofMinutes(15));
            }
            return new OutfitItemDto(
                oi.getItemRole(),
                wi.getId(),
                wi.getName(),
                wi.getCategory(),
                wi.getPrimaryColor(),
                wi.getSecondaryColor(),
                wi.getPattern(),
                wi.getFormalityLevel(),
                wi.getSeason(),
                downloadUrl
            );
        } else {
            // Wardrobe item was eradicated: maintain immutable snapshot metadata
            String name = (oi.getSubcategory() != null && !oi.getSubcategory().isBlank())
                ? oi.getSubcategory()
                : (oi.getCategory() != null ? oi.getCategory().name() : "[Removed Item]");

            return new OutfitItemDto(
                oi.getItemRole(),
                null,
                name,
                oi.getCategory(),
                oi.getPrimaryColor(),
                oi.getSecondaryColor(),
                oi.getPattern(),
                null,
                null,
                null
            );
        }
    }

    private ZoneId resolveZoneId(String timezoneStr) {
        if (timezoneStr != null && !timezoneStr.isBlank()) {
            try {
                return ZoneId.of(timezoneStr.trim());
            } catch (Exception e) {
                log.warn("Invalid timezone [{}] provided. Defaulting to UTC.", timezoneStr);
            }
        }
        return ZoneOffset.UTC;
    }
}
