import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ScoreBreakdownDto } from '../types/outfit.types';
import { SUB_SCORE_MAX_VALUES } from '../utils/outfitFormatting';

export interface ScoreBreakdownViewProps {
  readonly breakdown: ScoreBreakdownDto;
  readonly compact?: boolean;
}

interface ScoreRowData {
  readonly label: string;
  readonly score: number;
  readonly max: number;
  readonly isPenalty?: boolean;
}

export function ScoreBreakdownView({
  breakdown,
  compact = false,
}: ScoreBreakdownViewProps): React.JSX.Element {
  const rows: readonly ScoreRowData[] = [
    {
      label: 'Color Harmony',
      score: breakdown.colorHarmony,
      max: SUB_SCORE_MAX_VALUES.colorHarmony,
    },
    {
      label: 'Formality Alignment',
      score: breakdown.formalityAlignment,
      max: SUB_SCORE_MAX_VALUES.formalityAlignment,
    },
    {
      label: 'Season & Weather',
      score: breakdown.seasonWeatherCompatibility,
      max: SUB_SCORE_MAX_VALUES.seasonWeatherCompatibility,
    },
    {
      label: 'Pattern Restraint',
      score: breakdown.patternRestraint,
      max: SUB_SCORE_MAX_VALUES.patternRestraint,
    },
    {
      label: 'Preferences',
      score: breakdown.preferenceMatch,
      max: SUB_SCORE_MAX_VALUES.preferenceMatch,
    },
    ...(breakdown.wearPenalty > 0 || !compact
      ? [
          {
            label: 'Wear Repetition',
            score: breakdown.wearPenalty,
            max: SUB_SCORE_MAX_VALUES.wearPenalty,
            isPenalty: true,
          },
        ]
      : []),
  ];

  if (compact) {
    return (
      <View style={styles.compactContainer} testID="score-breakdown-compact">
        {rows.map((row) => (
          <View key={row.label} style={styles.compactPill}>
            <Text style={styles.compactPillLabel}>{row.label}</Text>
            <Text
              style={[
                styles.compactPillValue,
                row.isPenalty && row.score > 0 && styles.penaltyText,
              ]}
            >
              {row.isPenalty ? (row.score > 0 ? `-${row.score}` : '0') : `${row.score}/${row.max}`}
            </Text>
          </View>
        ))}
      </View>
    );
  }

  return (
    <View style={styles.detailedContainer} testID="score-breakdown-detailed">
      <Text style={styles.detailedTitle}>Suitability Score Breakdown</Text>
      {rows.map((row) => {
        const percentage = Math.min(100, Math.max(0, (row.score / row.max) * 100));
        return (
          <View key={row.label} style={styles.scoreRow}>
            <View style={styles.scoreRowHeader}>
              <Text style={styles.rowLabel}>{row.label}</Text>
              <Text
                style={[
                  styles.rowValue,
                  row.isPenalty && row.score > 0 && styles.penaltyText,
                ]}
              >
                {row.isPenalty
                  ? row.score > 0
                    ? `-${row.score} pts`
                    : 'No penalty'
                  : `${row.score} / ${row.max}`}
              </Text>
            </View>

            {/* Visual Progress Bar */}
            <View style={styles.barBackground}>
              <View
                style={[
                  styles.barFill,
                  { width: `${percentage}%` },
                  row.isPenalty ? styles.barPenalty : styles.barPositive,
                ]}
              />
            </View>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  compactContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginTop: 8,
  },
  compactPill: {
    backgroundColor: '#0F172A',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderWidth: 1,
    borderColor: '#334155',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  compactPillLabel: {
    color: '#94A3B8',
    fontSize: 11,
    fontWeight: '500',
  },
  compactPillValue: {
    color: '#F8FAFC',
    fontSize: 11,
    fontWeight: '700',
  },
  detailedContainer: {
    backgroundColor: '#0F172A',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
    marginVertical: 12,
  },
  detailedTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#F8FAFC',
    marginBottom: 12,
  },
  scoreRow: {
    marginBottom: 12,
  },
  scoreRowHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  rowLabel: {
    color: '#CBD5E1',
    fontSize: 12,
    fontWeight: '500',
  },
  rowValue: {
    color: '#F8FAFC',
    fontSize: 12,
    fontWeight: '600',
  },
  penaltyText: {
    color: '#F87171',
  },
  barBackground: {
    height: 6,
    backgroundColor: '#1E293B',
    borderRadius: 3,
    overflow: 'hidden',
  },
  barFill: {
    height: '100%',
    borderRadius: 3,
  },
  barPositive: {
    backgroundColor: '#F59E0B',
  },
  barPenalty: {
    backgroundColor: '#EF4444',
  },
});
