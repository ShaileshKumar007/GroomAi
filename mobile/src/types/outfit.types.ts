/**
 * Outfit recommendation domain models matching docs/API_SPEC.md Section 5.1
 * and backend com.groomai.outfit DTO contracts.
 * Strict TypeScript types with zero `any`.
 */

import {
  Category,
  Color,
  FormalityLevel,
  Pattern,
  Season,
} from './wardrobe.types';

export type Occasion =
  | 'CASUAL'
  | 'SMART_CASUAL'
  | 'BUSINESS_CASUAL'
  | 'FORMAL'
  | 'DATE_NIGHT'
  | 'LOUNGEWEAR'
  | 'ATHLETIC';

export type TemperatureBracket = 'HOT' | 'WARM' | 'MILD' | 'COOL' | 'COLD';

export type WeatherCondition = 'SUNNY' | 'RAINY' | 'WINDY' | 'SNOWY';

export type OutfitRole = 'TOP' | 'BOTTOM' | 'FOOTWEAR' | 'OUTERWEAR' | 'ACCESSORY';

export interface OutfitItemDto {
  readonly role: string;
  readonly id: string;
  readonly name: string;
  readonly category: Category;
  readonly primaryColor: Color;
  readonly secondaryColor: Color | null;
  readonly pattern: Pattern;
  readonly formalityLevel: FormalityLevel;
  readonly season: Season;
  readonly downloadUrl: string;
}

export interface ScoreBreakdownDto {
  readonly colorHarmony: number;
  readonly formalityAlignment: number;
  readonly seasonWeatherCompatibility: number;
  readonly patternRestraint: number;
  readonly preferenceMatch: number;
  readonly wearPenalty: number;
}

export interface OutfitRecommendationDto {
  readonly id: string;
  readonly occasion: Occasion;
  readonly suitabilityScore: number;
  readonly aiStylingAvailable: boolean;
  readonly stylingRationale: string;
  readonly scoreBreakdown: ScoreBreakdownDto;
  readonly items: readonly OutfitItemDto[];
}

export interface GenerateOutfitRequestPayload {
  readonly occasion?: Occasion;
  readonly temperatureBracket?: TemperatureBracket;
  readonly weatherCondition?: WeatherCondition;
  readonly limit?: number;
}

export interface GenerateOutfitResponseDto {
  readonly outfits: readonly OutfitRecommendationDto[];
  readonly warnings: readonly string[];
}

export interface OutfitGeneratorError {
  readonly code: string;
  readonly message: string;
  readonly status?: number;
  readonly missingCategories?: readonly string[];
}

export interface SaveOutfitRequestPayload {
  readonly itemIds: readonly string[];
  readonly occasion: Occasion;
  readonly temperatureBracket?: TemperatureBracket | null;
  readonly weatherCondition?: WeatherCondition | null;
  readonly stylingRationale?: string;
  readonly suitabilityScore?: number;
}

export interface SavedOutfitResponseDto {
  readonly id: string;
  readonly occasion: Occasion;
  readonly temperatureBracket?: TemperatureBracket | null;
  readonly weatherCondition?: WeatherCondition | null;
  readonly stylingRationale: string;
  readonly suitabilityScore: number;
  readonly createdAt: string;
  readonly items: readonly OutfitItemDto[];
}

export interface LogOutfitWearPayload {
  readonly wornDate?: string;
  readonly notes?: string;
}

export interface LogOutfitWearResponseDto {
  readonly logId: string;
  readonly outfitId: string;
  readonly wornDate: string;
  readonly message: string;
}

export interface OutfitWearHistoryDto {
  readonly logId: string;
  readonly outfitId: string | null;
  readonly wornDate: string;
  readonly notes: string | null;
  readonly createdAt: string;
  readonly occasion: Occasion | null;
  readonly suitabilityScore: number | null;
  readonly stylingRationale: string;
  readonly items: readonly OutfitItemDto[];
}
