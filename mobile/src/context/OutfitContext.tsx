import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react';
import {
  apiGenerateOutfits,
  apiGetWearHistory,
  apiLogOutfitWear,
  apiSaveOutfit,
} from '../api/client';
import { ApiError } from '../types/api.types';
import {
  GenerateOutfitRequestPayload,
  LogOutfitWearPayload,
  LogOutfitWearResponseDto,
  Occasion,
  OutfitGeneratorError,
  OutfitRecommendationDto,
  OutfitWearHistoryDto,
  TemperatureBracket,
  WeatherCondition,
} from '../types/outfit.types';

export interface OutfitContextValue {
  readonly selectedOccasion: Occasion;
  readonly selectedTemperature: TemperatureBracket | null;
  readonly selectedWeatherCondition: WeatherCondition | null;
  readonly limit: number;
  readonly recommendations: readonly OutfitRecommendationDto[];
  readonly warnings: readonly string[];
  readonly isLoading: boolean;
  readonly hasGenerated: boolean;
  readonly error: OutfitGeneratorError | null;
  readonly savedOutfitsMap: Record<string, string>;
  readonly savingOutfitId: string | null;
  readonly loggingWearOutfitId: string | null;
  readonly wornOutfitsMap: Record<string, string>;
  readonly wearHistory: readonly OutfitWearHistoryDto[];
  readonly isHistoryLoading: boolean;
  readonly historyError: string | null;
  readonly setOccasion: (occasion: Occasion) => void;
  readonly setTemperature: (temp: TemperatureBracket | null) => void;
  readonly setWeatherCondition: (condition: WeatherCondition | null) => void;
  readonly clearWeather: () => void;
  readonly generateOutfits: () => Promise<void>;
  readonly retry: () => Promise<void>;
  readonly getOutfitById: (id: string) => OutfitRecommendationDto | undefined;
  readonly resetGenerator: () => void;
  readonly saveOutfit: (outfit: OutfitRecommendationDto) => Promise<string>;
  readonly logOutfitWear: (
    outfitIdOrRecommendation: string | OutfitRecommendationDto,
    payload?: LogOutfitWearPayload
  ) => Promise<LogOutfitWearResponseDto>;
  readonly fetchWearHistory: (page?: number, size?: number) => Promise<void>;
}

const OutfitContext = createContext<OutfitContextValue | undefined>(undefined);

export function OutfitProvider({
  children,
}: {
  readonly children: React.ReactNode;
}): React.JSX.Element {
  const [selectedOccasion, setSelectedOccasion] = useState<Occasion>('SMART_CASUAL');
  const [selectedTemperature, setSelectedTemperature] =
    useState<TemperatureBracket | null>(null);
  const [selectedWeatherCondition, setSelectedWeatherCondition] =
    useState<WeatherCondition | null>(null);
  const [limit] = useState<number>(3);

  const [recommendations, setRecommendations] = useState<
    readonly OutfitRecommendationDto[]
  >([]);
  const [warnings, setWarnings] = useState<readonly string[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [hasGenerated, setHasGenerated] = useState<boolean>(false);
  const [error, setError] = useState<OutfitGeneratorError | null>(null);

  const [savedOutfitsMap, setSavedOutfitsMap] = useState<Record<string, string>>({});
  const [savingOutfitId, setSavingOutfitId] = useState<string | null>(null);
  const [loggingWearOutfitId, setLoggingWearOutfitId] = useState<string | null>(null);
  const [wornOutfitsMap, setWornOutfitsMap] = useState<Record<string, string>>({});
  const [wearHistory, setWearHistory] = useState<readonly OutfitWearHistoryDto[]>([]);
  const [isHistoryLoading, setIsHistoryLoading] = useState<boolean>(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  const setOccasion = useCallback((occasion: Occasion) => {
    setSelectedOccasion(occasion);
    setError(null);
  }, []);

  const setTemperature = useCallback((temp: TemperatureBracket | null) => {
    setSelectedTemperature(temp);
    setError(null);
  }, []);

  const setWeatherCondition = useCallback(
    (condition: WeatherCondition | null) => {
      setSelectedWeatherCondition(condition);
      setError(null);
    },
    []
  );

  const clearWeather = useCallback(() => {
    setSelectedTemperature(null);
    setSelectedWeatherCondition(null);
    setError(null);
  }, []);

  const resetGenerator = useCallback(() => {
    setRecommendations([]);
    setWarnings([]);
    setHasGenerated(false);
    setError(null);
  }, []);

  const generateOutfits = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    const payload: GenerateOutfitRequestPayload = {
      occasion: selectedOccasion,
      limit,
      ...(selectedTemperature ? { temperatureBracket: selectedTemperature } : {}),
      ...(selectedWeatherCondition ? { weatherCondition: selectedWeatherCondition } : {}),
    };

    try {
      const response = await apiGenerateOutfits(payload);
      setRecommendations(response.outfits);
      setWarnings(response.warnings || []);
      setHasGenerated(true);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError({
          code: err.code,
          message: err.message,
          status: err.status,
          missingCategories: err.missingCategories,
        });
      } else {
        const msg = err instanceof Error ? err.message : 'Failed to generate recommendations';
        setError({
          code: 'NETWORK_ERROR',
          message: msg,
          status: 0,
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [selectedOccasion, selectedTemperature, selectedWeatherCondition, limit]);

  const retry = useCallback(async () => {
    await generateOutfits();
  }, [generateOutfits]);

  const getOutfitById = useCallback(
    (id: string): OutfitRecommendationDto | undefined => {
      return recommendations.find((outfit) => outfit.id === id);
    },
    [recommendations]
  );

  const saveOutfit = useCallback(
    async (outfit: OutfitRecommendationDto): Promise<string> => {
      if (savedOutfitsMap[outfit.id]) {
        return savedOutfitsMap[outfit.id];
      }
      setSavingOutfitId(outfit.id);
      try {
        const itemIds: readonly string[] = outfit.items
          .map((i) => i.id)
          .filter((id): id is string => Boolean(id));
        const payload = {
          itemIds,
          occasion: outfit.occasion,
          temperatureBracket: selectedTemperature,
          weatherCondition: selectedWeatherCondition,
          stylingRationale: outfit.stylingRationale,
          suitabilityScore: outfit.suitabilityScore,
        };
        const saved = await apiSaveOutfit(payload);
        setSavedOutfitsMap((prev) => ({
          ...prev,
          [outfit.id]: saved.id,
          [saved.id]: saved.id,
        }));
        return saved.id;
      } finally {
        setSavingOutfitId(null);
      }
    },
    [savedOutfitsMap, selectedTemperature, selectedWeatherCondition]
  );

  const logOutfitWear = useCallback(
    async (
      outfitIdOrRecommendation: string | OutfitRecommendationDto,
      payload?: LogOutfitWearPayload
    ): Promise<LogOutfitWearResponseDto> => {
      let targetOutfitId: string;
      let recId: string | null = null;

      if (typeof outfitIdOrRecommendation === 'string') {
        recId = outfitIdOrRecommendation;
        targetOutfitId = savedOutfitsMap[outfitIdOrRecommendation] || outfitIdOrRecommendation;
      } else {
        recId = outfitIdOrRecommendation.id;
        if (savedOutfitsMap[outfitIdOrRecommendation.id]) {
          targetOutfitId = savedOutfitsMap[outfitIdOrRecommendation.id];
        } else {
          targetOutfitId = await saveOutfit(outfitIdOrRecommendation);
        }
      }

      setLoggingWearOutfitId(targetOutfitId);
      try {
        const result = await apiLogOutfitWear(targetOutfitId, payload);
        setWornOutfitsMap((prev) => {
          const next = { ...prev, [targetOutfitId]: result.logId };
          if (recId) {
            next[recId] = result.logId;
          }
          return next;
        });
        return result;
      } finally {
        setLoggingWearOutfitId(null);
      }
    },
    [savedOutfitsMap, saveOutfit]
  );

  const fetchWearHistory = useCallback(
    async (page: number = 0, size: number = 20): Promise<void> => {
      setIsHistoryLoading(true);
      setHistoryError(null);
      try {
        const res = await apiGetWearHistory({ page, size });
        setWearHistory(res.content);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to fetch wear history';
        setHistoryError(msg);
      } finally {
        setIsHistoryLoading(false);
      }
    },
    []
  );

  const value = useMemo<OutfitContextValue>(
    () => ({
      selectedOccasion,
      selectedTemperature,
      selectedWeatherCondition,
      limit,
      recommendations,
      warnings,
      isLoading,
      hasGenerated,
      error,
      savedOutfitsMap,
      savingOutfitId,
      loggingWearOutfitId,
      wornOutfitsMap,
      wearHistory,
      isHistoryLoading,
      historyError,
      setOccasion,
      setTemperature,
      setWeatherCondition,
      clearWeather,
      generateOutfits,
      retry,
      getOutfitById,
      resetGenerator,
      saveOutfit,
      logOutfitWear,
      fetchWearHistory,
    }),
    [
      selectedOccasion,
      selectedTemperature,
      selectedWeatherCondition,
      limit,
      recommendations,
      warnings,
      isLoading,
      hasGenerated,
      error,
      savedOutfitsMap,
      savingOutfitId,
      loggingWearOutfitId,
      wornOutfitsMap,
      wearHistory,
      isHistoryLoading,
      historyError,
      setOccasion,
      setTemperature,
      setWeatherCondition,
      clearWeather,
      generateOutfits,
      retry,
      getOutfitById,
      resetGenerator,
      saveOutfit,
      logOutfitWear,
      fetchWearHistory,
    ]
  );

  return <OutfitContext.Provider value={value}>{children}</OutfitContext.Provider>;
}

export function useOutfitGenerator(): OutfitContextValue {
  const context = useContext(OutfitContext);
  if (!context) {
    throw new Error('useOutfitGenerator must be used within an OutfitProvider');
  }
  return context;
}
