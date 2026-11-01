import React from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  TemperatureBracket,
  WeatherCondition,
} from '../types/outfit.types';
import {
  formatTemperatureBracket,
  formatWeatherCondition,
  SUPPORTED_TEMPERATURE_BRACKETS,
  SUPPORTED_WEATHER_CONDITIONS,
} from '../utils/outfitFormatting';

export interface WeatherContextSelectorProps {
  readonly selectedTemperature: TemperatureBracket | null;
  readonly selectedWeatherCondition: WeatherCondition | null;
  readonly onSelectTemperature: (temp: TemperatureBracket | null) => void;
  readonly onSelectWeatherCondition: (cond: WeatherCondition | null) => void;
  readonly onClearWeather: () => void;
  readonly disabled?: boolean;
}

export function WeatherContextSelector({
  selectedTemperature,
  selectedWeatherCondition,
  onSelectTemperature,
  onSelectWeatherCondition,
  onClearWeather,
  disabled = false,
}: WeatherContextSelectorProps): React.JSX.Element {
  const hasWeatherSelected =
    selectedTemperature !== null || selectedWeatherCondition !== null;

  return (
    <View style={styles.card} testID="weather-context-selector">
      <View style={styles.headerRow}>
        <View style={styles.titleContainer}>
          <Text style={styles.title}>Weather Context (Optional)</Text>
          <Text style={styles.subtitle}>
            {hasWeatherSelected
              ? 'Weather-tuned styling active'
              : 'Omitted: recommendations use garment seasonality'}
          </Text>
        </View>

        {hasWeatherSelected && (
          <TouchableOpacity
            style={styles.clearButton}
            onPress={onClearWeather}
            disabled={disabled}
            activeOpacity={0.7}
            accessibilityRole="button"
            accessibilityLabel="Clear weather context"
            testID="clear-weather-button"
          >
            <Text style={styles.clearButtonText}>Clear</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Temperature Bracket Selector */}
      <View style={styles.subSection}>
        <Text style={styles.subLabel}>Temperature</Text>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.scrollList}
        >
          {SUPPORTED_TEMPERATURE_BRACKETS.map((temp) => {
            const isSelected = selectedTemperature === temp;
            return (
              <TouchableOpacity
                key={temp}
                style={[
                  styles.chip,
                  isSelected ? styles.chipSelected : styles.chipUnselected,
                  disabled && styles.chipDisabled,
                ]}
                onPress={() =>
                  onSelectTemperature(isSelected ? null : temp)
                }
                disabled={disabled}
                activeOpacity={0.7}
                accessibilityRole="button"
                accessibilityState={{ selected: isSelected, disabled }}
                accessibilityLabel={`Temperature: ${formatTemperatureBracket(temp)}`}
                testID={`temp-chip-${temp.toLowerCase()}`}
              >
                <Text
                  style={[
                    styles.chipText,
                    isSelected ? styles.chipTextSelected : styles.chipTextUnselected,
                  ]}
                >
                  {formatTemperatureBracket(temp)}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      </View>

      {/* Weather Condition Selector */}
      <View style={styles.subSection}>
        <Text style={styles.subLabel}>Condition</Text>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.scrollList}
        >
          {SUPPORTED_WEATHER_CONDITIONS.map((cond) => {
            const isSelected = selectedWeatherCondition === cond;
            return (
              <TouchableOpacity
                key={cond}
                style={[
                  styles.chip,
                  isSelected ? styles.chipSelected : styles.chipUnselected,
                  disabled && styles.chipDisabled,
                ]}
                onPress={() =>
                  onSelectWeatherCondition(isSelected ? null : cond)
                }
                disabled={disabled}
                activeOpacity={0.7}
                accessibilityRole="button"
                accessibilityState={{ selected: isSelected, disabled }}
                accessibilityLabel={`Condition: ${formatWeatherCondition(cond)}`}
                testID={`weather-chip-${cond.toLowerCase()}`}
              >
                <Text
                  style={[
                    styles.chipText,
                    isSelected ? styles.chipTextSelected : styles.chipTextUnselected,
                  ]}
                >
                  {formatWeatherCondition(cond)}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 14,
    padding: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#334155',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  titleContainer: {
    flex: 1,
    marginRight: 8,
  },
  title: {
    fontSize: 14,
    fontWeight: '600',
    color: '#F8FAFC',
    letterSpacing: 0.2,
  },
  subtitle: {
    fontSize: 12,
    color: '#94A3B8',
    marginTop: 2,
    lineHeight: 16,
  },
  clearButton: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
    backgroundColor: '#0F172A',
    borderWidth: 1,
    borderColor: '#475569',
  },
  clearButtonText: {
    fontSize: 12,
    color: '#F59E0B',
    fontWeight: '600',
  },
  subSection: {
    marginTop: 10,
  },
  subLabel: {
    fontSize: 12,
    fontWeight: '500',
    color: '#CBD5E1',
    marginBottom: 6,
  },
  scrollList: {
    flexDirection: 'row',
    gap: 8,
    paddingVertical: 2,
  },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    minHeight: 32,
    justifyContent: 'center',
    alignItems: 'center',
  },
  chipSelected: {
    backgroundColor: '#0284C7', // Sky blue for weather selection
    borderColor: '#38BDF8',
  },
  chipUnselected: {
    backgroundColor: '#0F172A',
    borderColor: '#334155',
  },
  chipDisabled: {
    opacity: 0.5,
  },
  chipText: {
    fontSize: 12,
    fontWeight: '500',
  },
  chipTextSelected: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  chipTextUnselected: {
    color: '#94A3B8',
  },
});
