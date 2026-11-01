import React from 'react';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { AuthenticatedImage } from './AuthenticatedImage';
import { ScoreBreakdownView } from './ScoreBreakdownView';
import { OutfitRecommendationDto } from '../types/outfit.types';
import {
  formatOccasion,
  formatRole,
  getScoreColor,
  getScoreTier,
  partitionOutfitItems,
} from '../utils/outfitFormatting';

export interface OutfitCardProps {
  readonly outfit: OutfitRecommendationDto;
  readonly onPress: () => void;
  readonly onSave?: (outfit: OutfitRecommendationDto) => void;
  readonly isSaved?: boolean;
  readonly isSaving?: boolean;
  readonly testID?: string;
}

export function OutfitCard({
  outfit,
  onPress,
  onSave,
  isSaved,
  isSaving,
  testID,
}: OutfitCardProps): React.JSX.Element {
  const { coreItems, outerwear, accessory } = partitionOutfitItems(outfit.items);
  const scoreColor = getScoreColor(outfit.suitabilityScore);
  const scoreTier = getScoreTier(outfit.suitabilityScore);

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityLabel={`Outfit recommendation with score ${outfit.suitabilityScore}. ${outfit.stylingRationale}`}
      testID={testID ?? `outfit-card-${outfit.id}`}
    >
      {/* Top Header: Occasion & Suitability Score */}
      <View style={styles.header}>
        <View style={styles.occasionBadge}>
          <Text style={styles.occasionText}>{formatOccasion(outfit.occasion)}</Text>
        </View>

        <View style={styles.scoreContainer}>
          <Text style={styles.scoreTier}>{scoreTier}</Text>
          <View style={[styles.scorePill, { borderColor: scoreColor }]}>
            <Text style={[styles.scoreValue, { color: scoreColor }]}>
              {outfit.suitabilityScore}
            </Text>
          </View>
        </View>
      </View>

      {/* Garment Grid / Horizontal Showcase */}
      <View style={styles.garmentsContainer}>
        {coreItems.map((item) => (
          <View key={item.id} style={styles.garmentColumn}>
            <View style={styles.imageBox}>
              <AuthenticatedImage
                uri={item.downloadUrl}
                style={styles.garmentImage}
                testID={`outfit-item-img-${item.id}`}
                accessibilityLabel={item.name}
              />
              <View style={styles.roleTag}>
                <Text style={styles.roleTagText}>{formatRole(item.role)}</Text>
              </View>
            </View>
            <Text style={styles.garmentName} numberOfLines={1}>
              {item.name}
            </Text>
          </View>
        ))}

        {/* Optional Outerwear if present */}
        {outerwear && (
          <View key={outerwear.id} style={styles.garmentColumn}>
            <View style={styles.imageBox}>
              <AuthenticatedImage
                uri={outerwear.downloadUrl}
                style={styles.garmentImage}
                testID={`outfit-item-img-${outerwear.id}`}
                accessibilityLabel={outerwear.name}
              />
              <View style={[styles.roleTag, styles.optionalTag]}>
                <Text style={styles.roleTagText}>{formatRole(outerwear.role)}</Text>
              </View>
            </View>
            <Text style={styles.garmentName} numberOfLines={1}>
              {outerwear.name}
            </Text>
          </View>
        )}

        {/* Optional Accessory if present */}
        {accessory && (
          <View key={accessory.id} style={styles.garmentColumn}>
            <View style={styles.imageBox}>
              <AuthenticatedImage
                uri={accessory.downloadUrl}
                style={styles.garmentImage}
                testID={`outfit-item-img-${accessory.id}`}
                accessibilityLabel={accessory.name}
              />
              <View style={[styles.roleTag, styles.optionalTag]}>
                <Text style={styles.roleTagText}>{formatRole(accessory.role)}</Text>
              </View>
            </View>
            <Text style={styles.garmentName} numberOfLines={1}>
              {accessory.name}
            </Text>
          </View>
        )}
      </View>

      {/* Styling Rationale */}
      <View style={styles.rationaleBox}>
        <Text style={styles.rationaleQuoteMark}>“</Text>
        <Text style={styles.rationaleText} numberOfLines={3}>
          {outfit.stylingRationale}
        </Text>
      </View>

      {/* Sub-score Summary */}
      <ScoreBreakdownView breakdown={outfit.scoreBreakdown} compact={true} />

      {/* Footer CTA & Save */}
      <View style={styles.footer}>
        {onSave && (
          <TouchableOpacity
            style={[
              styles.saveButton,
              isSaved && styles.saveButtonSaved,
              isSaving && styles.saveButtonDisabled,
            ]}
            onPress={(e) => {
              e.stopPropagation();
              onSave(outfit);
            }}
            disabled={isSaved || isSaving}
            activeOpacity={0.7}
            testID={`save-outfit-btn-${outfit.id}`}
            accessibilityRole="button"
            accessibilityLabel={isSaved ? 'Outfit saved' : 'Save outfit'}
          >
            <Text
              style={[
                styles.saveButtonText,
                isSaved && styles.saveButtonTextSaved,
              ]}
            >
              {isSaved ? '✓ Saved' : isSaving ? 'Saving...' : '🔖 Save'}
            </Text>
          </TouchableOpacity>
        )}
        <Text style={styles.footerLink}>Inspect Outfit Details →</Text>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#334155',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  occasionBadge: {
    backgroundColor: '#0F172A',
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  occasionText: {
    color: '#CBD5E1',
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.3,
  },
  scoreContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  scoreTier: {
    color: '#94A3B8',
    fontSize: 12,
    fontWeight: '500',
  },
  scorePill: {
    backgroundColor: '#0F172A',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    borderWidth: 1.5,
  },
  scoreValue: {
    fontSize: 14,
    fontWeight: '700',
  },
  garmentsContainer: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 14,
  },
  garmentColumn: {
    flex: 1,
  },
  imageBox: {
    aspectRatio: 1,
    borderRadius: 10,
    overflow: 'hidden',
    backgroundColor: '#0F172A',
    position: 'relative',
    borderWidth: 1,
    borderColor: '#334155',
  },
  garmentImage: {
    width: '100%',
    height: '100%',
  },
  roleTag: {
    position: 'absolute',
    bottom: 4,
    left: 4,
    backgroundColor: 'rgba(15, 23, 42, 0.85)',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  optionalTag: {
    backgroundColor: 'rgba(30, 41, 59, 0.9)',
    borderWidth: 0.5,
    borderColor: '#64748B',
  },
  roleTagText: {
    color: '#F8FAFC',
    fontSize: 9,
    fontWeight: '600',
    letterSpacing: 0.2,
  },
  garmentName: {
    color: '#E2E8F0',
    fontSize: 11,
    fontWeight: '500',
    marginTop: 4,
    textAlign: 'center',
  },
  rationaleBox: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginVertical: 6,
    borderLeftWidth: 3,
    borderLeftColor: '#F59E0B',
    position: 'relative',
  },
  rationaleQuoteMark: {
    position: 'absolute',
    top: 2,
    left: 6,
    fontSize: 20,
    color: '#64748B',
    lineHeight: 20,
    opacity: 0.4,
  },
  rationaleText: {
    color: '#CBD5E1',
    fontSize: 12,
    lineHeight: 18,
    fontStyle: 'italic',
    paddingLeft: 10,
  },
  footer: {
    marginTop: 10,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#334155',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  saveButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 5,
    paddingHorizontal: 10,
    borderRadius: 8,
    backgroundColor: '#0F172A',
    borderWidth: 1,
    borderColor: '#F59E0B',
  },
  saveButtonSaved: {
    borderColor: '#10B981',
    backgroundColor: 'rgba(16, 185, 129, 0.1)',
  },
  saveButtonDisabled: {
    opacity: 0.6,
  },
  saveButtonText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#F59E0B',
  },
  saveButtonTextSaved: {
    color: '#10B981',
  },
  footerLink: {
    color: '#F59E0B',
    fontSize: 12,
    fontWeight: '600',
  },
});
