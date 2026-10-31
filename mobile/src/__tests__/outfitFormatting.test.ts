import {
  formatMissingCategories,
  formatOccasion,
  formatRole,
  formatTemperatureBracket,
  formatWeatherCondition,
  getScoreColor,
  getScoreTier,
  partitionOutfitItems,
  SUB_SCORE_MAX_VALUES,
  SUPPORTED_OCCASIONS,
  SUPPORTED_TEMPERATURE_BRACKETS,
  SUPPORTED_WEATHER_CONDITIONS,
} from '../utils/outfitFormatting';
import { OutfitItemDto } from '../types/outfit.types';

describe('Outfit Formatting Utilities', () => {
  describe('Occasion formatting', () => {
    it('formats all 7 supported occasions correctly', () => {
      expect(formatOccasion('CASUAL')).toBe('Casual');
      expect(formatOccasion('SMART_CASUAL')).toBe('Smart Casual');
      expect(formatOccasion('BUSINESS_CASUAL')).toBe('Business Casual');
      expect(formatOccasion('FORMAL')).toBe('Formal');
      expect(formatOccasion('DATE_NIGHT')).toBe('Date Night');
      expect(formatOccasion('LOUNGEWEAR')).toBe('Loungewear');
      expect(formatOccasion('ATHLETIC')).toBe('Athletic');
    });

    it('contains exactly the 7 backend-supported occasions', () => {
      expect(SUPPORTED_OCCASIONS).toHaveLength(7);
      expect(SUPPORTED_OCCASIONS).toContain('SMART_CASUAL');
      expect(SUPPORTED_OCCASIONS).toContain('CASUAL');
      expect(SUPPORTED_OCCASIONS).toContain('FORMAL');
    });
  });

  describe('Weather context formatting', () => {
    it('formats all 5 temperature brackets', () => {
      expect(formatTemperatureBracket('HOT')).toBe('Hot (> 28°C)');
      expect(formatTemperatureBracket('WARM')).toBe('Warm (22–28°C)');
      expect(formatTemperatureBracket('MILD')).toBe('Mild (15–22°C)');
      expect(formatTemperatureBracket('COOL')).toBe('Cool (8–15°C)');
      expect(formatTemperatureBracket('COLD')).toBe('Cold (< 8°C)');
    });

    it('contains exactly the 5 temperature brackets', () => {
      expect(SUPPORTED_TEMPERATURE_BRACKETS).toEqual([
        'HOT',
        'WARM',
        'MILD',
        'COOL',
        'COLD',
      ]);
    });

    it('formats all 4 weather conditions with emojis', () => {
      expect(formatWeatherCondition('SUNNY')).toBe('Sunny ☀️');
      expect(formatWeatherCondition('RAINY')).toBe('Rainy 🌧️');
      expect(formatWeatherCondition('WINDY')).toBe('Windy 💨');
      expect(formatWeatherCondition('SNOWY')).toBe('Snowy ❄️');
    });

    it('contains exactly the 4 weather conditions', () => {
      expect(SUPPORTED_WEATHER_CONDITIONS).toEqual([
        'SUNNY',
        'RAINY',
        'WINDY',
        'SNOWY',
      ]);
    });
  });

  describe('Role and category formatting', () => {
    it('formats standard garment roles', () => {
      expect(formatRole('TOP')).toBe('Top');
      expect(formatRole('BOTTOM')).toBe('Bottom');
      expect(formatRole('FOOTWEAR')).toBe('Footwear');
      expect(formatRole('OUTERWEAR')).toBe('Outerwear');
      expect(formatRole('ACCESSORY')).toBe('Accessory');
      expect(formatRole('custom')).toBe('Custom');
    });

    it('formats missing categories list for 422 user prompts', () => {
      expect(formatMissingCategories()).toBe('essential clothing items');
      expect(formatMissingCategories([])).toBe('essential clothing items');
      expect(formatMissingCategories(['BOTTOM'])).toBe('Bottom');
      expect(formatMissingCategories(['BOTTOM', 'FOOTWEAR'])).toBe('Bottom and Footwear');
      expect(formatMissingCategories(['TOP', 'BOTTOM', 'FOOTWEAR'])).toBe(
        'Top, Bottom, and Footwear'
      );
    });
  });

  describe('Score tiers and colors', () => {
    it('returns appropriate color according to score thresholds', () => {
      expect(getScoreColor(95)).toBe('#10B981'); // Green
      expect(getScoreColor(85)).toBe('#10B981'); // Green
      expect(getScoreColor(84)).toBe('#F59E0B'); // Amber
      expect(getScoreColor(70)).toBe('#F59E0B'); // Amber
      expect(getScoreColor(69)).toBe('#94A3B8'); // Slate
      expect(getScoreColor(50)).toBe('#94A3B8'); // Slate
    });

    it('returns appropriate descriptive tier', () => {
      expect(getScoreTier(92)).toBe('Exceptional Match');
      expect(getScoreTier(85)).toBe('Great Harmony');
      expect(getScoreTier(75)).toBe('Solid Pairing');
      expect(getScoreTier(65)).toBe('Moderate Compatibility');
    });

    it('has documented maximum values for score breakdown components', () => {
      expect(SUB_SCORE_MAX_VALUES.colorHarmony).toBe(30);
      expect(SUB_SCORE_MAX_VALUES.formalityAlignment).toBe(25);
      expect(SUB_SCORE_MAX_VALUES.seasonWeatherCompatibility).toBe(20);
      expect(SUB_SCORE_MAX_VALUES.patternRestraint).toBe(15);
      expect(SUB_SCORE_MAX_VALUES.preferenceMatch).toBe(10);
      expect(SUB_SCORE_MAX_VALUES.wearPenalty).toBe(20);
    });
  });

  describe('Outfit item partitioning', () => {
    const makeItem = (role: string, name: string): OutfitItemDto => ({
      role,
      id: `id-${role.toLowerCase()}`,
      name,
      category: role === 'TOP' ? 'TOP' : role === 'BOTTOM' ? 'BOTTOM' : 'FOOTWEAR',
      primaryColor: 'NAVY',
      secondaryColor: null,
      pattern: 'SOLID',
      formalityLevel: 'CASUAL',
      season: 'ALL_SEASON',
      downloadUrl: `http://localhost/images/${role.toLowerCase()}.jpg`,
    });

    it('partitions core 3-piece combination without outerwear or accessories', () => {
      const top = makeItem('TOP', 'White Oxford');
      const bottom = makeItem('BOTTOM', 'Navy Chinos');
      const footwear = makeItem('FOOTWEAR', 'Brown Loafers');

      const result = partitionOutfitItems([top, bottom, footwear]);

      expect(result.coreItems).toHaveLength(3);
      expect(result.coreItems[0].name).toBe('White Oxford');
      expect(result.coreItems[1].name).toBe('Navy Chinos');
      expect(result.coreItems[2].name).toBe('Brown Loafers');
      expect(result.outerwear).toBeNull();
      expect(result.accessory).toBeNull();
    });

    it('identifies optional outerwear when present', () => {
      const top = makeItem('TOP', 'White T-Shirt');
      const bottom = makeItem('BOTTOM', 'Blue Jeans');
      const footwear = makeItem('FOOTWEAR', 'White Sneakers');
      const outerwear = makeItem('OUTERWEAR', 'Navy Wool Peacoat');

      const result = partitionOutfitItems([top, bottom, footwear, outerwear]);

      expect(result.coreItems).toHaveLength(3);
      expect(result.outerwear).not.toBeNull();
      expect(result.outerwear?.name).toBe('Navy Wool Peacoat');
      expect(result.accessory).toBeNull();
    });

    it('identifies optional accessory when present', () => {
      const top = makeItem('TOP', 'Black Turtleneck');
      const bottom = makeItem('BOTTOM', 'Grey Trousers');
      const footwear = makeItem('FOOTWEAR', 'Chelsea Boots');
      const accessory = makeItem('ACCESSORY', 'Silver Watch');

      const result = partitionOutfitItems([top, bottom, footwear, accessory]);

      expect(result.coreItems).toHaveLength(3);
      expect(result.outerwear).toBeNull();
      expect(result.accessory).not.toBeNull();
      expect(result.accessory?.name).toBe('Silver Watch');
    });

    it('handles both optional outerwear and accessory together', () => {
      const top = makeItem('TOP', 'Oxford');
      const bottom = makeItem('BOTTOM', 'Chinos');
      const footwear = makeItem('FOOTWEAR', 'Boots');
      const outerwear = makeItem('OUTERWEAR', 'Blazer');
      const accessory = makeItem('ACCESSORY', 'Leather Belt');

      const result = partitionOutfitItems([top, bottom, footwear, outerwear, accessory]);

      expect(result.coreItems).toHaveLength(3);
      expect(result.outerwear?.name).toBe('Blazer');
      expect(result.accessory?.name).toBe('Leather Belt');
    });
  });
});
