/**
 * Formatting and display utilities for outfit recommendation presentation.
 * Adheres to Groom.ai minimal, fashion-first design language.
 */

import {
  Occasion,
  OutfitItemDto,
  TemperatureBracket,
  WeatherCondition,
} from '../types/outfit.types';

export const SUPPORTED_OCCASIONS: readonly Occasion[] = [
  'CASUAL',
  'SMART_CASUAL',
  'BUSINESS_CASUAL',
  'FORMAL',
  'DATE_NIGHT',
  'LOUNGEWEAR',
  'ATHLETIC',
] as const;

export const SUPPORTED_TEMPERATURE_BRACKETS: readonly TemperatureBracket[] = [
  'HOT',
  'WARM',
  'MILD',
  'COOL',
  'COLD',
] as const;

export const SUPPORTED_WEATHER_CONDITIONS: readonly WeatherCondition[] = [
  'SUNNY',
  'RAINY',
  'WINDY',
  'SNOWY',
] as const;

export const SUB_SCORE_MAX_VALUES = {
  colorHarmony: 30,
  formalityAlignment: 25,
  seasonWeatherCompatibility: 20,
  patternRestraint: 15,
  preferenceMatch: 10,
  wearPenalty: 20,
} as const;

export function formatOccasion(occasion: Occasion): string {
  switch (occasion) {
    case 'CASUAL':
      return 'Casual';
    case 'SMART_CASUAL':
      return 'Smart Casual';
    case 'BUSINESS_CASUAL':
      return 'Business Casual';
    case 'FORMAL':
      return 'Formal';
    case 'DATE_NIGHT':
      return 'Date Night';
    case 'LOUNGEWEAR':
      return 'Loungewear';
    case 'ATHLETIC':
      return 'Athletic';
    default: {
      const _exhaustive: never = occasion;
      return _exhaustive;
    }
  }
}

export function formatTemperatureBracket(temp: TemperatureBracket): string {
  switch (temp) {
    case 'HOT':
      return 'Hot (> 28°C)';
    case 'WARM':
      return 'Warm (22–28°C)';
    case 'MILD':
      return 'Mild (15–22°C)';
    case 'COOL':
      return 'Cool (8–15°C)';
    case 'COLD':
      return 'Cold (< 8°C)';
    default: {
      const _exhaustive: never = temp;
      return _exhaustive;
    }
  }
}

export function formatWeatherCondition(condition: WeatherCondition): string {
  switch (condition) {
    case 'SUNNY':
      return 'Sunny ☀️';
    case 'RAINY':
      return 'Rainy 🌧️';
    case 'WINDY':
      return 'Windy 💨';
    case 'SNOWY':
      return 'Snowy ❄️';
    default: {
      const _exhaustive: never = condition;
      return _exhaustive;
    }
  }
}

export function formatRole(role: string): string {
  switch (role.toUpperCase()) {
    case 'TOP':
      return 'Top';
    case 'BOTTOM':
      return 'Bottom';
    case 'FOOTWEAR':
      return 'Footwear';
    case 'OUTERWEAR':
      return 'Outerwear';
    case 'ACCESSORY':
      return 'Accessory';
    default:
      return role.charAt(0).toUpperCase() + role.slice(1).toLowerCase();
  }
}

export function formatMissingCategories(categories?: readonly string[]): string {
  if (!categories || categories.length === 0) {
    return 'essential clothing items';
  }
  const formatted = categories.map((cat) => formatRole(cat));
  if (formatted.length === 1) {
    return formatted[0];
  }
  if (formatted.length === 2) {
    return `${formatted[0]} and ${formatted[1]}`;
  }
  const last = formatted[formatted.length - 1];
  const head = formatted.slice(0, -1).join(', ');
  return `${head}, and ${last}`;
}

export function getScoreColor(score: number | null | undefined): string {
  if (score === null || score === undefined) return '#94A3B8';
  if (score >= 85) return '#10B981'; // Green
  if (score >= 70) return '#F59E0B'; // Amber
  return '#94A3B8'; // Slate
}

export function getScoreTier(score: number | null | undefined): string {
  if (score === null || score === undefined) return 'Custom Match';
  if (score >= 90) return 'Exceptional Match';
  if (score >= 80) return 'Great Harmony';
  if (score >= 70) return 'Solid Pairing';
  return 'Moderate Compatibility';
}

/**
 * Organizes outfit items into core (Top, Bottom, Footwear) and optional (Outerwear, Accessory)
 * to ensure clear visual hierarchy.
 */
export function partitionOutfitItems(items: readonly OutfitItemDto[]): {
  readonly coreItems: readonly OutfitItemDto[];
  readonly outerwear: OutfitItemDto | null;
  readonly accessory: OutfitItemDto | null;
} {
  let top: OutfitItemDto | null = null;
  let bottom: OutfitItemDto | null = null;
  let footwear: OutfitItemDto | null = null;
  let outerwear: OutfitItemDto | null = null;
  let accessory: OutfitItemDto | null = null;

  for (const item of items) {
    const roleUpper = item.role.toUpperCase();
    if (roleUpper === 'TOP' && !top) {
      top = item;
    } else if (roleUpper === 'BOTTOM' && !bottom) {
      bottom = item;
    } else if (roleUpper === 'FOOTWEAR' && !footwear) {
      footwear = item;
    } else if (roleUpper === 'OUTERWEAR' && !outerwear) {
      outerwear = item;
    } else if (roleUpper === 'ACCESSORY' && !accessory) {
      accessory = item;
    }
  }

  const coreItems: OutfitItemDto[] = [];
  if (top) coreItems.push(top);
  if (bottom) coreItems.push(bottom);
  if (footwear) coreItems.push(footwear);

  return {
    coreItems,
    outerwear,
    accessory,
  };
}
