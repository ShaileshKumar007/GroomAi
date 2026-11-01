package com.groomai.outfit.service;

import com.groomai.outfit.dto.GenerateOutfitRequestDto;
import com.groomai.outfit.dto.GenerateOutfitResponseDto;
import com.groomai.outfit.dto.OutfitItemDto;
import com.groomai.outfit.dto.OutfitRecommendationDto;
import com.groomai.outfit.enums.Occasion;
import com.groomai.outfit.enums.TemperatureBracket;
import com.groomai.outfit.enums.WeatherCondition;
import com.groomai.outfit.exception.InsufficientWardrobeItemsException;
import com.groomai.outfit.model.CandidateOutfit;
import com.groomai.outfit.model.ScoredOutfit;
import com.groomai.outfit.scorer.OutfitScoringEngine;
import com.groomai.storage.ObjectStorageService;
import com.groomai.user.entity.StylePreferences;
import com.groomai.user.repository.StylePreferencesRepository;
import com.groomai.wardrobe.entity.WardrobeItem;
import com.groomai.wardrobe.enums.Category;
import com.groomai.wardrobe.enums.ItemStatus;
import com.groomai.wardrobe.enums.Season;
import com.groomai.wardrobe.repository.WardrobeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Production implementation of deterministic outfit recommendation engine.
 */
@Service
public class OutfitGeneratorServiceImpl implements OutfitGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OutfitGeneratorServiceImpl.class);

    private final WardrobeItemRepository wardrobeItemRepository;
    private final StylePreferencesRepository stylePreferencesRepository;
    private final ObjectStorageService objectStorageService;
    private final OutfitScoringEngine scoringEngine;
    private final OutfitPersistenceService outfitPersistenceService;

    public OutfitGeneratorServiceImpl(
        WardrobeItemRepository wardrobeItemRepository,
        StylePreferencesRepository stylePreferencesRepository,
        ObjectStorageService objectStorageService,
        OutfitScoringEngine scoringEngine
    ) {
        this(wardrobeItemRepository, stylePreferencesRepository, objectStorageService, scoringEngine, null);
    }

    @Autowired
    public OutfitGeneratorServiceImpl(
        WardrobeItemRepository wardrobeItemRepository,
        StylePreferencesRepository stylePreferencesRepository,
        ObjectStorageService objectStorageService,
        OutfitScoringEngine scoringEngine,
        @org.springframework.context.annotation.Lazy OutfitPersistenceService outfitPersistenceService
    ) {
        this.wardrobeItemRepository = wardrobeItemRepository;
        this.stylePreferencesRepository = stylePreferencesRepository;
        this.objectStorageService = objectStorageService;
        this.scoringEngine = scoringEngine;
        this.outfitPersistenceService = outfitPersistenceService;
    }

    @Override
    @Transactional(readOnly = true)
    public GenerateOutfitResponseDto generateOutfits(UUID userId, GenerateOutfitRequestDto request) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        GenerateOutfitRequestDto safeRequest = (request != null)
            ? request
            : new GenerateOutfitRequestDto(null, null, null, 3);

        int limit = safeRequest.effectiveLimit();
        Occasion occasion = safeRequest.occasion();
        TemperatureBracket temperatureBracket = safeRequest.temperatureBracket();
        WeatherCondition weatherCondition = safeRequest.weatherCondition();

        // 1. Fetch all active items belonging to the authenticated user
        List<WardrobeItem> activeWardrobe = wardrobeItemRepository.findByUserIdAndStatus(userId, ItemStatus.ACTIVE);

        List<WardrobeItem> tops = new ArrayList<>();
        List<WardrobeItem> bottoms = new ArrayList<>();
        List<WardrobeItem> shoes = new ArrayList<>();
        List<WardrobeItem> outerwear = new ArrayList<>();

        for (WardrobeItem item : activeWardrobe) {
            if (item.getCategory() == null) continue;
            switch (item.getCategory()) {
                case TOP -> tops.add(item);
                case BOTTOM -> bottoms.add(item);
                case FOOTWEAR -> shoes.add(item);
                case OUTERWEAR -> outerwear.add(item);
                case ACCESSORY -> {} // Accessories deferred in candidate search
            }
        }

        // 2. Precondition verification: must have >= 1 Top, Bottom, and Footwear
        List<Category> missingCategories = new ArrayList<>();
        if (tops.isEmpty()) missingCategories.add(Category.TOP);
        if (bottoms.isEmpty()) missingCategories.add(Category.BOTTOM);
        if (shoes.isEmpty()) missingCategories.add(Category.FOOTWEAR);

        if (!missingCategories.isEmpty()) {
            throw new InsufficientWardrobeItemsException(missingCategories);
        }

        // 3. Pre-filtering by occasion and temperature bounds
        List<WardrobeItem> filteredTops = filterByContext(tops, occasion, temperatureBracket);
        List<WardrobeItem> filteredBottoms = filterByContext(bottoms, occasion, temperatureBracket);
        List<WardrobeItem> filteredShoes = filterByContext(shoes, occasion, temperatureBracket);
        List<WardrobeItem> filteredOuterwear = filterByContext(outerwear, occasion, temperatureBracket);

        if (filteredTops.isEmpty() || filteredBottoms.isEmpty() || filteredShoes.isEmpty()) {
            String warningMsg = (occasion != null)
                ? "No compatible outfit combinations found matching occasion " + occasion + " with current wardrobe items."
                : "No compatible outfit combinations found matching specified filters.";
            return new GenerateOutfitResponseDto(Collections.emptyList(), List.of(warningMsg));
        }

        // 4. Style preferences lookup (falls back gracefully to neutral baseline if absent)
        StylePreferences preferences = stylePreferencesRepository.findByUserId(userId).orElse(null);

        // 5. Wear history lookup (reads persistent wear logs; falls back to zero penalty if absent)
        Map<UUID, Integer> wearDaysMap = (outfitPersistenceService != null)
            ? outfitPersistenceService.getRecentItemWearDays(userId)
            : Collections.emptyMap();

        // 6. Outerwear evaluation criteria
        boolean shouldEvaluateOuterwear = !filteredOuterwear.isEmpty()
            && (isLayeringWeather(temperatureBracket, weatherCondition) || isTailoredOccasion(occasion));

        // 7. Bounded min-heap priority queue for top-K selection
        PriorityQueue<ScoredOutfit> boundedQueue = new PriorityQueue<>(limit, ScoredOutfit.MIN_HEAP_COMPARATOR);

        for (WardrobeItem top : filteredTops) {
            for (WardrobeItem bottom : filteredBottoms) {
                if (hasImmediateFormalityClash(top, bottom) || hasImmediateSeasonClash(top, bottom)) {
                    continue;
                }

                for (WardrobeItem shoe : filteredShoes) {
                    if (hasImmediateFormalityClash(top, bottom, shoe) || hasImmediateSeasonClash(top, bottom, shoe)) {
                        continue;
                    }

                    CandidateOutfit coreCandidate = new CandidateOutfit(top, bottom, shoe);
                    Optional<ScoredOutfit> scoredCoreOpt = scoringEngine.scoreOutfit(
                        coreCandidate, occasion, temperatureBracket, weatherCondition, preferences, wearDaysMap
                    );

                    ScoredOutfit chosenScored = null;

                    if (shouldEvaluateOuterwear) {
                        ScoredOutfit bestLayered = null;
                        for (WardrobeItem outer : filteredOuterwear) {
                            if (hasImmediateFormalityClash(top, bottom, shoe, outer)
                                || hasImmediateSeasonClash(top, bottom, shoe, outer)) {
                                continue;
                            }

                            CandidateOutfit layeredCandidate = new CandidateOutfit(top, bottom, shoe, outer);
                            Optional<ScoredOutfit> scoredLayeredOpt = scoringEngine.scoreOutfit(
                                layeredCandidate, occasion, temperatureBracket, weatherCondition, preferences, wearDaysMap
                            );

                            if (scoredLayeredOpt.isPresent()) {
                                ScoredOutfit scoredLayered = scoredLayeredOpt.get();
                                if (bestLayered == null || ScoredOutfit.RANKING_COMPARATOR.compare(scoredLayered, bestLayered) < 0) {
                                    bestLayered = scoredLayered;
                                }
                            }
                        }

                        // Anti-duplication rule (Approved Decision #1):
                        // If layered outfit exists and scores >= unlayered core, it REPLACES the unlayered outfit.
                        // Never return both layered and unlayered versions of the same core combination.
                        if (bestLayered != null && scoredCoreOpt.isPresent()) {
                            chosenScored = (bestLayered.suitabilityScore() >= scoredCoreOpt.get().suitabilityScore())
                                ? bestLayered
                                : scoredCoreOpt.get();
                        } else if (bestLayered != null) {
                            chosenScored = bestLayered;
                        } else if (scoredCoreOpt.isPresent()) {
                            chosenScored = scoredCoreOpt.get();
                        }
                    } else {
                        if (scoredCoreOpt.isPresent()) {
                            chosenScored = scoredCoreOpt.get();
                        }
                    }

                    if (chosenScored != null) {
                        boundedQueue.offer(chosenScored);
                        if (boundedQueue.size() > limit) {
                            boundedQueue.poll(); // Evict the lowest-ranked candidate
                        }
                    }
                }
            }
        }

        if (boundedQueue.isEmpty()) {
            return new GenerateOutfitResponseDto(
                Collections.emptyList(),
                List.of("No compatible outfit combinations could be formed matching occasion " + occasion + ".")
            );
        }

        // 8. Extract and sort descending by full ranking comparator
        List<ScoredOutfit> rankedOutfits = boundedQueue.stream()
            .sorted(ScoredOutfit.RANKING_COMPARATOR)
            .toList();

        // 9. Map to DTOs with ephemeral storage download URLs
        List<OutfitRecommendationDto> recommendations = new ArrayList<>(rankedOutfits.size());
        for (ScoredOutfit scored : rankedOutfits) {
            CandidateOutfit candidate = scored.candidate();
            List<OutfitItemDto> itemDtos = new ArrayList<>(4);

            itemDtos.add(toItemDto("TOP", candidate.top()));
            itemDtos.add(toItemDto("BOTTOM", candidate.bottom()));
            itemDtos.add(toItemDto("FOOTWEAR", candidate.footwear()));

            if (candidate.outerwear() != null) {
                itemDtos.add(toItemDto("OUTERWEAR", candidate.outerwear()));
            }

            OutfitRecommendationDto recommendation = new OutfitRecommendationDto(
                scored.id(),
                occasion,
                scored.suitabilityScore(),
                false,
                scored.stylingRationale(),
                scored.scoreBreakdown(),
                itemDtos
            );
            recommendations.add(recommendation);
        }

        return new GenerateOutfitResponseDto(recommendations, Collections.emptyList());
    }

    private OutfitItemDto toItemDto(String role, WardrobeItem item) {
        String downloadUrl = objectStorageService.generateDownloadUrl(item.getStorageKey(), Duration.ofMinutes(15));
        return new OutfitItemDto(
            role,
            item.getId(),
            item.getName(),
            item.getCategory(),
            item.getPrimaryColor(),
            item.getSecondaryColor(),
            item.getPattern(),
            item.getFormalityLevel(),
            item.getSeason(),
            downloadUrl
        );
    }

    private List<WardrobeItem> filterByContext(List<WardrobeItem> items, Occasion occasion, TemperatureBracket temperatureBracket) {
        return items.stream()
            .filter(item -> {
                if (occasion != null && !occasion.isPermitted(item.getFormalityLevel())) {
                    return false;
                }
                if (temperatureBracket == TemperatureBracket.HOT && item.getSeason() == Season.WINTER) {
                    return false;
                }
                if (temperatureBracket == TemperatureBracket.COLD && item.getSeason() == Season.SUMMER) {
                    return false;
                }
                return true;
            })
            .toList();
    }

    private boolean isLayeringWeather(TemperatureBracket temperatureBracket, WeatherCondition weatherCondition) {
        if (temperatureBracket == TemperatureBracket.COLD || temperatureBracket == TemperatureBracket.COOL) {
            return true;
        }
        return weatherCondition == WeatherCondition.RAINY || weatherCondition == WeatherCondition.SNOWY;
    }

    private boolean isTailoredOccasion(Occasion occasion) {
        return occasion == Occasion.BUSINESS_CASUAL || occasion == Occasion.FORMAL;
    }

    private static boolean hasImmediateFormalityClash(WardrobeItem a, WardrobeItem b) {
        int rA = CandidateOutfit.getFormalityRank(a.getFormalityLevel());
        int rB = CandidateOutfit.getFormalityRank(b.getFormalityLevel());
        return Math.abs(rA - rB) >= 3;
    }

    private static boolean hasImmediateFormalityClash(WardrobeItem a, WardrobeItem b, WardrobeItem c) {
        int rA = CandidateOutfit.getFormalityRank(a.getFormalityLevel());
        int rB = CandidateOutfit.getFormalityRank(b.getFormalityLevel());
        int rC = CandidateOutfit.getFormalityRank(c.getFormalityLevel());
        int min = Math.min(rA, Math.min(rB, rC));
        int max = Math.max(rA, Math.max(rB, rC));
        return (max - min) >= 3;
    }

    private static boolean hasImmediateFormalityClash(WardrobeItem a, WardrobeItem b, WardrobeItem c, WardrobeItem d) {
        int rA = CandidateOutfit.getFormalityRank(a.getFormalityLevel());
        int rB = CandidateOutfit.getFormalityRank(b.getFormalityLevel());
        int rC = CandidateOutfit.getFormalityRank(c.getFormalityLevel());
        int rD = CandidateOutfit.getFormalityRank(d.getFormalityLevel());
        int min = Math.min(Math.min(rA, rB), Math.min(rC, rD));
        int max = Math.max(Math.max(rA, rB), Math.max(rC, rD));
        return (max - min) >= 3;
    }

    private static boolean hasImmediateSeasonClash(WardrobeItem a, WardrobeItem b) {
        Season sa = a.getSeason();
        Season sb = b.getSeason();
        return (sa == Season.SUMMER && sb == Season.WINTER) || (sa == Season.WINTER && sb == Season.SUMMER);
    }

    private static boolean hasImmediateSeasonClash(WardrobeItem a, WardrobeItem b, WardrobeItem c) {
        Season sa = a.getSeason();
        Season sb = b.getSeason();
        Season sc = c.getSeason();
        boolean hasSummer = (sa == Season.SUMMER || sb == Season.SUMMER || sc == Season.SUMMER);
        boolean hasWinter = (sa == Season.WINTER || sb == Season.WINTER || sc == Season.WINTER);
        return hasSummer && hasWinter;
    }

    private static boolean hasImmediateSeasonClash(WardrobeItem a, WardrobeItem b, WardrobeItem c, WardrobeItem d) {
        Season sa = a.getSeason();
        Season sb = b.getSeason();
        Season sc = c.getSeason();
        Season sd = d.getSeason();
        boolean hasSummer = (sa == Season.SUMMER || sb == Season.SUMMER || sc == Season.SUMMER || sd == Season.SUMMER);
        boolean hasWinter = (sa == Season.WINTER || sb == Season.WINTER || sc == Season.WINTER || sd == Season.WINTER);
        return hasSummer && hasWinter;
    }
}
