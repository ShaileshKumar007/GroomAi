import React from 'react';
import { OccasionSelector } from '../components/OccasionSelector';
import { WeatherContextSelector } from '../components/WeatherContextSelector';
import { ScoreBreakdownView } from '../components/ScoreBreakdownView';
import { OutfitCard } from '../components/OutfitCard';
import { OutfitRecommendationDto } from '../types/outfit.types';

// Mock expo-secure-store
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => {}),
  deleteItemAsync: jest.fn(async () => {}),
}));

// Mock AuthenticatedImage
jest.mock('../components/AuthenticatedImage', () => ({
  AuthenticatedImage: 'AuthenticatedImage',
}));

// Mock react-native
jest.mock('react-native', () => ({
  Platform: {
    OS: 'ios',
    select: (obj: Record<string, unknown>) => obj.ios ?? obj.default,
  },
  StyleSheet: {
    create: (styles: Record<string, unknown>) => styles,
    absoluteFill: {},
  },
  View: 'View',
  Text: 'Text',
  TouchableOpacity: 'TouchableOpacity',
  ScrollView: 'ScrollView',
  Image: 'Image',
  ActivityIndicator: 'ActivityIndicator',
  SafeAreaView: 'SafeAreaView',
  FlatList: 'FlatList',
}));

describe('Outfit Recommendation Presentation Components', () => {
  const sampleOutfit: OutfitRecommendationDto = {
    id: 'test-outfit-uuid',
    occasion: 'SMART_CASUAL',
    suitabilityScore: 92,
    aiStylingAvailable: false,
    stylingRationale: 'Navy Oxford and Beige Chinos create a classic smart casual foundation.',
    scoreBreakdown: {
      colorHarmony: 28,
      formalityAlignment: 25,
      seasonWeatherCompatibility: 20,
      patternRestraint: 14,
      preferenceMatch: 5,
      wearPenalty: 0,
    },
    items: [
      {
        role: 'TOP',
        id: 'top-1',
        name: 'Navy Oxford Button-Down',
        category: 'TOP',
        primaryColor: 'NAVY',
        secondaryColor: null,
        pattern: 'SOLID',
        formalityLevel: 'SMART_CASUAL',
        season: 'ALL_SEASON',
        downloadUrl: 'http://localhost/images/navy.jpg',
      },
      {
        role: 'BOTTOM',
        id: 'bottom-1',
        name: 'Slim Beige Chinos',
        category: 'BOTTOM',
        primaryColor: 'BEIGE',
        secondaryColor: null,
        pattern: 'SOLID',
        formalityLevel: 'SMART_CASUAL',
        season: 'ALL_SEASON',
        downloadUrl: 'http://localhost/images/chinos.jpg',
      },
      {
        role: 'FOOTWEAR',
        id: 'footwear-1',
        name: 'Brown Leather Loafers',
        category: 'FOOTWEAR',
        primaryColor: 'BROWN',
        secondaryColor: null,
        pattern: 'SOLID',
        formalityLevel: 'SMART_CASUAL',
        season: 'ALL_SEASON',
        downloadUrl: 'http://localhost/images/loafers.jpg',
      },
    ],
  };

  describe('OccasionSelector', () => {
    it('creates JSX element with selectedOccasion', () => {
      const onSelect = jest.fn();
      const element = React.createElement(OccasionSelector, {
        selectedOccasion: 'SMART_CASUAL',
        onSelectOccasion: onSelect,
      });

      expect(React.isValidElement(element)).toBe(true);
      expect(element.props.selectedOccasion).toBe('SMART_CASUAL');
    });

    it('supports disabled prop', () => {
      const element = React.createElement(OccasionSelector, {
        selectedOccasion: 'FORMAL',
        onSelectOccasion: jest.fn(),
        disabled: true,
      });

      expect(element.props.disabled).toBe(true);
    });
  });

  describe('WeatherContextSelector', () => {
    it('creates JSX element when weather context is omitted', () => {
      const onClear = jest.fn();
      const element = React.createElement(WeatherContextSelector, {
        selectedTemperature: null,
        selectedWeatherCondition: null,
        onSelectTemperature: jest.fn(),
        onSelectWeatherCondition: jest.fn(),
        onClearWeather: onClear,
      });

      expect(React.isValidElement(element)).toBe(true);
      expect(element.props.selectedTemperature).toBeNull();
      expect(element.props.selectedWeatherCondition).toBeNull();
    });

    it('creates JSX element when weather context is supplied', () => {
      const element = React.createElement(WeatherContextSelector, {
        selectedTemperature: 'COOL',
        selectedWeatherCondition: 'RAINY',
        onSelectTemperature: jest.fn(),
        onSelectWeatherCondition: jest.fn(),
        onClearWeather: jest.fn(),
      });

      expect(element.props.selectedTemperature).toBe('COOL');
      expect(element.props.selectedWeatherCondition).toBe('RAINY');
    });
  });

  describe('ScoreBreakdownView', () => {
    it('renders in compact mode for recommendation cards', () => {
      const element = React.createElement(ScoreBreakdownView, {
        breakdown: sampleOutfit.scoreBreakdown,
        compact: true,
      });

      expect(React.isValidElement(element)).toBe(true);
      expect(element.props.compact).toBe(true);
    });

    it('renders in detailed mode for outfit detail screen', () => {
      const element = React.createElement(ScoreBreakdownView, {
        breakdown: sampleOutfit.scoreBreakdown,
        compact: false,
      });

      expect(React.isValidElement(element)).toBe(true);
      expect(element.props.compact).toBe(false);
    });
  });

  describe('OutfitCard', () => {
    it('creates recommendation card with core items', () => {
      const onPress = jest.fn();
      const element = React.createElement(OutfitCard, {
        outfit: sampleOutfit,
        onPress,
      });

      expect(React.isValidElement(element)).toBe(true);
      expect(element.props.outfit.id).toBe('test-outfit-uuid');
      expect(element.props.outfit.suitabilityScore).toBe(92);
      expect(element.props.outfit.items).toHaveLength(3);
    });

    it('renders with optional outerwear and accessory when present', () => {
      const outfitWithLayers: OutfitRecommendationDto = {
        ...sampleOutfit,
        items: [
          ...sampleOutfit.items,
          {
            role: 'OUTERWEAR',
            id: 'outer-1',
            name: 'Navy Peacoat',
            category: 'OUTERWEAR',
            primaryColor: 'NAVY',
            secondaryColor: null,
            pattern: 'SOLID',
            formalityLevel: 'SMART_CASUAL',
            season: 'WINTER',
            downloadUrl: 'http://localhost/images/peacoat.jpg',
          },
          {
            role: 'ACCESSORY',
            id: 'acc-1',
            name: 'Brown Leather Belt',
            category: 'ACCESSORY',
            primaryColor: 'BROWN',
            secondaryColor: null,
            pattern: 'SOLID',
            formalityLevel: 'SMART_CASUAL',
            season: 'ALL_SEASON',
            downloadUrl: 'http://localhost/images/belt.jpg',
          },
        ],
      };

      const element = React.createElement(OutfitCard, {
        outfit: outfitWithLayers,
        onPress: jest.fn(),
      });

      expect(element.props.outfit.items).toHaveLength(5);
    });
  });
});
