import React from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Occasion } from '../types/outfit.types';
import {
  formatOccasion,
  SUPPORTED_OCCASIONS,
} from '../utils/outfitFormatting';

export interface OccasionSelectorProps {
  readonly selectedOccasion: Occasion;
  readonly onSelectOccasion: (occasion: Occasion) => void;
  readonly disabled?: boolean;
}

export function OccasionSelector({
  selectedOccasion,
  onSelectOccasion,
  disabled = false,
}: OccasionSelectorProps): React.JSX.Element {
  return (
    <View style={styles.container}>
      <Text style={styles.sectionLabel}>Select Occasion</Text>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.scrollList}
      >
        {SUPPORTED_OCCASIONS.map((occasion) => {
          const isSelected = selectedOccasion === occasion;
          return (
            <TouchableOpacity
              key={occasion}
              style={[
                styles.chip,
                isSelected ? styles.chipSelected : styles.chipUnselected,
                disabled && styles.chipDisabled,
              ]}
              onPress={() => onSelectOccasion(occasion)}
              disabled={disabled}
              activeOpacity={0.7}
              accessibilityRole="button"
              accessibilityState={{ selected: isSelected, disabled }}
              accessibilityLabel={`Occasion: ${formatOccasion(occasion)}`}
              testID={`occasion-chip-${occasion.toLowerCase()}`}
            >
              <Text
                style={[
                  styles.chipText,
                  isSelected ? styles.chipTextSelected : styles.chipTextUnselected,
                ]}
              >
                {formatOccasion(occasion)}
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 20,
  },
  sectionLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#F8FAFC',
    marginBottom: 10,
    letterSpacing: 0.2,
  },
  scrollList: {
    flexDirection: 'row',
    gap: 8,
    paddingVertical: 2,
  },
  chip: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    borderWidth: 1,
    minHeight: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
  chipSelected: {
    backgroundColor: '#F59E0B',
    borderColor: '#F59E0B',
  },
  chipUnselected: {
    backgroundColor: '#1E293B',
    borderColor: '#334155',
  },
  chipDisabled: {
    opacity: 0.5,
  },
  chipText: {
    fontSize: 13,
    fontWeight: '600',
  },
  chipTextSelected: {
    color: '#0F172A',
  },
  chipTextUnselected: {
    color: '#CBD5E1',
  },
});
