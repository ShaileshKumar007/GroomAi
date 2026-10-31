import {
  GenerateOutfitRequestPayload,
  GenerateOutfitResponseDto,
  Occasion,
  OutfitGeneratorError,
  OutfitRecommendationDto,
  TemperatureBracket,
  WeatherCondition,
} from '../types/outfit.types';
import { ApiError } from '../types/api.types';
import { formatMissingCategories } from '../utils/outfitFormatting';

describe('Outfit Recommendation Workflow State Logic', () => {
  interface OutfitGeneratorState {
    selectedOccasion: Occasion;
    selectedTemperature: TemperatureBracket | null;
    selectedWeatherCondition: WeatherCondition | null;
    limit: number;
    recommendations: readonly OutfitRecommendationDto[];
    warnings: readonly string[];
    isLoading: boolean;
    hasGenerated: boolean;
    error: OutfitGeneratorError | null;
  }

  function createInitialState(): OutfitGeneratorState {
    return {
      selectedOccasion: 'SMART_CASUAL',
      selectedTemperature: null,
      selectedWeatherCondition: null,
      limit: 3,
      recommendations: [],
      warnings: [],
      isLoading: false,
      hasGenerated: false,
      error: null,
    };
  }

  function selectOccasion(state: OutfitGeneratorState, occasion: Occasion): OutfitGeneratorState {
    return {
      ...state,
      selectedOccasion: occasion,
      error: null,
    };
  }

  function selectTemperature(
    state: OutfitGeneratorState,
    temp: TemperatureBracket | null
  ): OutfitGeneratorState {
    const next = state.selectedTemperature === temp ? null : temp;
    return {
      ...state,
      selectedTemperature: next,
      error: null,
    };
  }

  function selectWeatherCondition(
    state: OutfitGeneratorState,
    condition: WeatherCondition | null
  ): OutfitGeneratorState {
    const next = state.selectedWeatherCondition === condition ? null : condition;
    return {
      ...state,
      selectedWeatherCondition: next,
      error: null,
    };
  }

  function clearWeather(state: OutfitGeneratorState): OutfitGeneratorState {
    return {
      ...state,
      selectedTemperature: null,
      selectedWeatherCondition: null,
      error: null,
    };
  }

  function buildRequestPayload(state: OutfitGeneratorState): GenerateOutfitRequestPayload {
    return {
      occasion: state.selectedOccasion,
      limit: state.limit,
      ...(state.selectedTemperature ? { temperatureBracket: state.selectedTemperature } : {}),
      ...(state.selectedWeatherCondition ? { weatherCondition: state.selectedWeatherCondition } : {}),
    };
  }

  function handleSuccessResponse(
    state: OutfitGeneratorState,
    response: GenerateOutfitResponseDto
  ): OutfitGeneratorState {
    return {
      ...state,
      recommendations: response.outfits,
      warnings: response.warnings || [],
      isLoading: false,
      hasGenerated: true,
      error: null,
    };
  }

  function handleError(state: OutfitGeneratorState, err: unknown): OutfitGeneratorState {
    if (err instanceof ApiError) {
      return {
        ...state,
        isLoading: false,
        error: {
          code: err.code,
          message: err.message,
          status: err.status,
          missingCategories: err.missingCategories,
        },
      };
    }
    const msg = err instanceof Error ? err.message : 'Unknown generation error';
    return {
      ...state,
      isLoading: false,
      error: {
        code: 'NETWORK_ERROR',
        message: msg,
        status: 0,
      },
    };
  }

  function resetGenerator(state: OutfitGeneratorState): OutfitGeneratorState {
    return {
      ...state,
      recommendations: [],
      warnings: [],
      hasGenerated: false,
      error: null,
    };
  }

  function getOutfitById(
    recommendations: readonly OutfitRecommendationDto[],
    id: string
  ): OutfitRecommendationDto | undefined {
    return recommendations.find((outfit) => outfit.id === id);
  }

  describe('Initial state and configuration', () => {
    it('initializes with SMART_CASUAL occasion and no weather context', () => {
      const state = createInitialState();
      expect(state.selectedOccasion).toBe('SMART_CASUAL');
      expect(state.selectedTemperature).toBeNull();
      expect(state.selectedWeatherCondition).toBeNull();
      expect(state.limit).toBe(3);
      expect(state.recommendations).toHaveLength(0);
      expect(state.hasGenerated).toBe(false);
      expect(state.error).toBeNull();
    });
  });

  describe('Occasion transitions', () => {
    it('updates occasion and clears existing errors', () => {
      let state = createInitialState();
      state = handleError(state, new ApiError('Prior error', 'UNKNOWN', 500));
      expect(state.error).not.toBeNull();

      state = selectOccasion(state, 'FORMAL');
      expect(state.selectedOccasion).toBe('FORMAL');
      expect(state.error).toBeNull();
    });
  });

  describe('Weather context transitions', () => {
    it('selects and deselects temperature bracket (toggle behavior)', () => {
      let state = createInitialState();

      state = selectTemperature(state, 'COLD');
      expect(state.selectedTemperature).toBe('COLD');

      // Tapping the same temperature deselects it
      state = selectTemperature(state, 'COLD');
      expect(state.selectedTemperature).toBeNull();
    });

    it('selects and deselects weather condition (toggle behavior)', () => {
      let state = createInitialState();

      state = selectWeatherCondition(state, 'RAINY');
      expect(state.selectedWeatherCondition).toBe('RAINY');

      // Tapping the same condition deselects it
      state = selectWeatherCondition(state, 'RAINY');
      expect(state.selectedWeatherCondition).toBeNull();
    });

    it('clears both temperature and condition with clearWeather', () => {
      let state = createInitialState();
      state = selectTemperature(state, 'HOT');
      state = selectWeatherCondition(state, 'SUNNY');

      expect(state.selectedTemperature).toBe('HOT');
      expect(state.selectedWeatherCondition).toBe('SUNNY');

      state = clearWeather(state);
      expect(state.selectedTemperature).toBeNull();
      expect(state.selectedWeatherCondition).toBeNull();
    });
  });

  describe('Request payload assembly', () => {
    it('omits weather keys when weather is not specified', () => {
      const state = createInitialState();
      const payload = buildRequestPayload(state);

      expect(payload.occasion).toBe('SMART_CASUAL');
      expect(payload.limit).toBe(3);
      expect(payload.temperatureBracket).toBeUndefined();
      expect(payload.weatherCondition).toBeUndefined();
    });

    it('includes temperature and weather condition when specified', () => {
      let state = createInitialState();
      state = selectOccasion(state, 'CASUAL');
      state = selectTemperature(state, 'MILD');
      state = selectWeatherCondition(state, 'WINDY');

      const payload = buildRequestPayload(state);
      expect(payload.occasion).toBe('CASUAL');
      expect(payload.temperatureBracket).toBe('MILD');
      expect(payload.weatherCondition).toBe('WINDY');
      expect(payload.limit).toBe(3);
    });
  });

  describe('Success and empty recommendation handling', () => {
    const mockOutfit: OutfitRecommendationDto = {
      id: 'outfit-abc-123',
      occasion: 'SMART_CASUAL',
      suitabilityScore: 88,
      aiStylingAvailable: false,
      stylingRationale: 'Balanced combination of neutrals.',
      scoreBreakdown: {
        colorHarmony: 28,
        formalityAlignment: 25,
        seasonWeatherCompatibility: 20,
        patternRestraint: 15,
        preferenceMatch: 0,
        wearPenalty: 0,
      },
      items: [
        {
          role: 'TOP',
          id: 'item-top-1',
          name: 'White Linen Shirt',
          category: 'TOP',
          primaryColor: 'WHITE',
          secondaryColor: null,
          pattern: 'SOLID',
          formalityLevel: 'SMART_CASUAL',
          season: 'SUMMER',
          downloadUrl: 'http://localhost/images/shirt.jpg',
        },
        {
          role: 'BOTTOM',
          id: 'item-bottom-1',
          name: 'Beige Shorts',
          category: 'BOTTOM',
          primaryColor: 'BEIGE',
          secondaryColor: null,
          pattern: 'SOLID',
          formalityLevel: 'SMART_CASUAL',
          season: 'SUMMER',
          downloadUrl: 'http://localhost/images/shorts.jpg',
        },
        {
          role: 'FOOTWEAR',
          id: 'item-footwear-1',
          name: 'Espadrilles',
          category: 'FOOTWEAR',
          primaryColor: 'BROWN',
          secondaryColor: null,
          pattern: 'SOLID',
          formalityLevel: 'SMART_CASUAL',
          season: 'SUMMER',
          downloadUrl: 'http://localhost/images/espadrilles.jpg',
        },
      ],
    };

    it('handles multiple recommendations returned from backend', () => {
      let state = createInitialState();
      const response: GenerateOutfitResponseDto = {
        outfits: [mockOutfit, { ...mockOutfit, id: 'outfit-def-456', suitabilityScore: 82 }],
        warnings: [],
      };

      state = handleSuccessResponse(state, response);
      expect(state.recommendations).toHaveLength(2);
      expect(state.hasGenerated).toBe(true);
      expect(state.isLoading).toBe(false);
      expect(state.error).toBeNull();
    });

    it('handles empty recommendations with warning on 200 OK', () => {
      let state = createInitialState();
      const response: GenerateOutfitResponseDto = {
        outfits: [],
        warnings: ['No active combinations met occasion requirements.'],
      };

      state = handleSuccessResponse(state, response);
      expect(state.recommendations).toHaveLength(0);
      expect(state.warnings).toContain('No active combinations met occasion requirements.');
      expect(state.hasGenerated).toBe(true);
    });

    it('resets generator state back to pre-generation condition', () => {
      let state = createInitialState();
      state = handleSuccessResponse(state, { outfits: [mockOutfit], warnings: [] });
      expect(state.hasGenerated).toBe(true);

      state = resetGenerator(state);
      expect(state.recommendations).toHaveLength(0);
      expect(state.warnings).toHaveLength(0);
      expect(state.hasGenerated).toBe(false);
    });
  });

  describe('Error handling & HTTP 422 insufficient wardrobe items', () => {
    it('captures 422 INSUFFICIENT_WARDROBE_ITEMS with missing categories', () => {
      let state = createInitialState();
      const apiError = new ApiError(
        'Wardrobe requires at least 1 Top, 1 Bottom, and 1 Footwear',
        'INSUFFICIENT_WARDROBE_ITEMS',
        422,
        ['BOTTOM', 'FOOTWEAR']
      );

      state = handleError(state, apiError);
      expect(state.error).not.toBeNull();
      expect(state.error?.code).toBe('INSUFFICIENT_WARDROBE_ITEMS');
      expect(state.error?.status).toBe(422);
      expect(state.error?.missingCategories).toEqual(['BOTTOM', 'FOOTWEAR']);
      expect(formatMissingCategories(state.error?.missingCategories)).toBe(
        'Bottom and Footwear'
      );
    });

    it('captures generic network error safely', () => {
      let state = createInitialState();
      state = handleError(state, new Error('Network timeout'));

      expect(state.error).not.toBeNull();
      expect(state.error?.code).toBe('NETWORK_ERROR');
      expect(state.error?.message).toBe('Network timeout');
      expect(state.error?.status).toBe(0);
    });
  });

  describe('Outfit detail lookup', () => {
    const item1: OutfitRecommendationDto = {
      id: 'uuid-1',
      occasion: 'FORMAL',
      suitabilityScore: 90,
      aiStylingAvailable: false,
      stylingRationale: 'Classic charcoal suit.',
      scoreBreakdown: {
        colorHarmony: 30,
        formalityAlignment: 25,
        seasonWeatherCompatibility: 20,
        patternRestraint: 15,
        preferenceMatch: 0,
        wearPenalty: 0,
      },
      items: [],
    };

    const item2: OutfitRecommendationDto = {
      ...item1,
      id: 'uuid-2',
      suitabilityScore: 85,
    };

    it('finds outfit by ID from recommendations array', () => {
      const found = getOutfitById([item1, item2], 'uuid-2');
      expect(found).toBeDefined();
      expect(found?.id).toBe('uuid-2');
      expect(found?.suitabilityScore).toBe(85);
    });

    it('returns undefined when outfit ID is not in active recommendations', () => {
      const found = getOutfitById([item1, item2], 'uuid-unknown');
      expect(found).toBeUndefined();
    });
  });
});
