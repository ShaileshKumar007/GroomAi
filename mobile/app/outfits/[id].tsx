import React, { useState } from 'react';
import {
  ActivityIndicator,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useOutfitGenerator } from '../../src/context/OutfitContext';
import { AuthenticatedImage } from '../../src/components/AuthenticatedImage';
import { ScoreBreakdownView } from '../../src/components/ScoreBreakdownView';
import {
  formatOccasion,
  formatRole,
  getScoreColor,
  getScoreTier,
} from '../../src/utils/outfitFormatting';
import { OutfitItemDto } from '../../src/types/outfit.types';

// Color dots mapping
const COLOR_HEX_MAP: Record<string, string> = {
  WHITE: '#FFFFFF',
  BLACK: '#000000',
  NAVY: '#1E3A8A',
  GREY: '#64748B',
  BEIGE: '#D4B996',
  BROWN: '#78350F',
  BLUE: '#2563EB',
  GREEN: '#16A34A',
  OLIVE: '#556B2F',
  RED: '#DC2626',
  BURGUNDY: '#800020',
  YELLOW: '#EAB308',
  MULTI: '#A855F7',
};

function formatEnumLabel(value: string | null | undefined): string {
  if (!value) return 'None';
  return value
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

export default function OutfitDetailScreen(): React.JSX.Element {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const {
    getOutfitById,
    saveOutfit,
    logOutfitWear,
    savedOutfitsMap,
    savingOutfitId,
    loggingWearOutfitId,
    wornOutfitsMap,
  } = useOutfitGenerator();

  const [notes, setNotes] = useState<string>('');
  const [showNotesInput, setShowNotesInput] = useState<boolean>(false);
  const [wearSuccessMessage, setWearSuccessMessage] = useState<string | null>(null);
  const [saveSuccessMessage, setSaveSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const outfit = id ? getOutfitById(id) : undefined;

  if (!outfit) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerCard}>
          <Text style={styles.notFoundIcon}>👔</Text>
          <Text style={styles.notFoundTitle}>Outfit Not Found</Text>
          <Text style={styles.notFoundSubtitle}>
            This outfit recommendation is no longer in your active session. Recommendations are
            generated fresh based on your current wardrobe.
          </Text>
          <TouchableOpacity
            style={[styles.button, styles.primaryButton]}
            onPress={() => router.replace('/outfits')}
            testID="back-to-generator-button"
          >
            <Text style={styles.primaryButtonText}>Go to Outfit Generator</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const isSaved = Boolean(savedOutfitsMap[outfit.id]);
  const isSaving = savingOutfitId === outfit.id;
  const savedOutfitId = savedOutfitsMap[outfit.id];
  const isWorn = Boolean(
    (savedOutfitId && wornOutfitsMap[savedOutfitId]) || wornOutfitsMap[outfit.id]
  );
  const isLoggingWear =
    (savedOutfitId && loggingWearOutfitId === savedOutfitId) ||
    loggingWearOutfitId === outfit.id;

  const scoreColor = getScoreColor(outfit.suitabilityScore);
  const scoreTier = getScoreTier(outfit.suitabilityScore);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Header Bar */}
        <View style={styles.headerRow}>
          <View style={styles.occasionBadge}>
            <Text style={styles.occasionText}>{formatOccasion(outfit.occasion)}</Text>
          </View>
          <View style={styles.scoreContainer}>
            <Text style={styles.scoreTier}>{scoreTier}</Text>
            <View style={[styles.scoreBadge, { borderColor: scoreColor }]}>
              <Text style={[styles.scoreValue, { color: scoreColor }]}>
                {outfit.suitabilityScore}
              </Text>
            </View>
          </View>
        </View>

        {/* Styling Rationale Card */}
        <View style={styles.rationaleCard} testID="outfit-detail-rationale">
          <Text style={styles.rationaleTitle}>Styling Rationale</Text>
          <Text style={styles.rationaleText}>{outfit.stylingRationale}</Text>
        </View>

        {/* Detailed Score Breakdown */}
        <ScoreBreakdownView breakdown={outfit.scoreBreakdown} compact={false} />

        {/* Garment Composition List */}
        <View style={styles.compositionSection}>
          <Text style={styles.sectionTitle}>
            Outfit Composition ({outfit.items.length} Items)
          </Text>

          {outfit.items.map((item: OutfitItemDto, idx: number) => {
            const primaryHex = (item.primaryColor && COLOR_HEX_MAP[item.primaryColor]) || '#94A3B8';
            const secondaryHex = item.secondaryColor
              ? COLOR_HEX_MAP[item.secondaryColor] || '#94A3B8'
              : null;

            const itemKey = item.id || `${item.role}-${idx}`;

            return (
              <View
                key={itemKey}
                style={styles.garmentCard}
                testID={`detail-garment-card-${item.id || idx}`}
              >
                <View style={styles.garmentImageContainer}>
                  <AuthenticatedImage
                    uri={item.downloadUrl}
                    style={styles.garmentImage}
                    testID={`detail-garment-img-${item.id}`}
                    accessibilityLabel={item.name}
                  />
                  <View style={styles.garmentRoleTag}>
                    <Text style={styles.garmentRoleText}>
                      {formatRole(item.role)}
                    </Text>
                  </View>
                </View>

                <View style={styles.garmentDetails}>
                  <Text style={styles.garmentName}>{item.name}</Text>
                  <Text style={styles.garmentCategory}>
                    {formatEnumLabel(item.category)}
                  </Text>

                  {/* Metadata Chips / Attributes */}
                  <View style={styles.metadataGrid}>
                    <View style={styles.metaRow}>
                      <Text style={styles.metaLabel}>Color:</Text>
                      <View style={styles.colorPill}>
                        <View
                          style={[
                            styles.colorDot,
                            { backgroundColor: primaryHex },
                          ]}
                        />
                        <Text style={styles.metaValue}>
                          {formatEnumLabel(item.primaryColor)}
                        </Text>
                        {secondaryHex && item.secondaryColor && (
                          <>
                            <View
                              style={[
                                styles.colorDot,
                                { backgroundColor: secondaryHex, marginLeft: 4 },
                              ]}
                            />
                            <Text style={styles.metaValue}>
                              / {formatEnumLabel(item.secondaryColor)}
                            </Text>
                          </>
                        )}
                      </View>
                    </View>

                    <View style={styles.metaRow}>
                      <Text style={styles.metaLabel}>Formality:</Text>
                      <Text style={styles.metaValue}>
                        {formatEnumLabel(item.formalityLevel)}
                      </Text>
                    </View>

                    <View style={styles.metaRow}>
                      <Text style={styles.metaLabel}>Pattern:</Text>
                      <Text style={styles.metaValue}>
                        {formatEnumLabel(item.pattern)}
                      </Text>
                    </View>

                    <View style={styles.metaRow}>
                      <Text style={styles.metaLabel}>Season:</Text>
                      <Text style={styles.metaValue}>
                        {formatEnumLabel(item.season)}
                      </Text>
                    </View>
                  </View>
                </View>
              </View>
            );
          })}
        </View>

        {/* Persistence & Wear Logging Actions */}
        <View style={styles.actionsSection}>
          {saveSuccessMessage && (
            <View style={styles.feedbackBannerSuccess} testID="save-success-banner">
              <Text style={styles.feedbackTextSuccess}>✓ {saveSuccessMessage}</Text>
            </View>
          )}

          {wearSuccessMessage && (
            <View style={styles.feedbackBannerSuccess} testID="wear-success-banner">
              <Text style={styles.feedbackTextSuccess}>✓ {wearSuccessMessage}</Text>
            </View>
          )}

          {errorMessage && (
            <View style={styles.feedbackBannerError} testID="action-error-banner">
              <Text style={styles.feedbackTextError}>⚠️ {errorMessage}</Text>
            </View>
          )}

          {/* Save Outfit Button */}
          <TouchableOpacity
            style={[
              styles.button,
              isSaved ? styles.savedButton : styles.primaryButton,
              isSaving && styles.buttonDisabled,
            ]}
            onPress={async () => {
              if (isSaved || isSaving) return;
              try {
                setErrorMessage(null);
                await saveOutfit(outfit);
                setSaveSuccessMessage('Outfit saved to your collection!');
              } catch (err: unknown) {
                const msg = err instanceof Error ? err.message : 'Failed to save outfit';
                setErrorMessage(msg);
              }
            }}
            disabled={isSaved || isSaving}
            activeOpacity={0.8}
            testID="save-outfit-detail-btn"
          >
            {isSaving ? (
              <ActivityIndicator size="small" color="#0F172A" />
            ) : (
              <Text style={isSaved ? styles.savedButtonText : styles.primaryButtonText}>
                {isSaved ? '✓ Outfit Saved' : '🔖 Save Outfit'}
              </Text>
            )}
          </TouchableOpacity>

          {/* Mark as Worn Button */}
          <TouchableOpacity
            style={[
              styles.button,
              isWorn ? styles.wornButton : styles.wearButton,
              isLoggingWear && styles.buttonDisabled,
            ]}
            onPress={async () => {
              if (isLoggingWear) return;
              try {
                setErrorMessage(null);
                const res = await logOutfitWear(
                  outfit,
                  notes.trim() ? { notes: notes.trim() } : undefined
                );
                setWearSuccessMessage(
                  res.message || 'Outfit recorded as worn today!'
                );
                setShowNotesInput(false);
              } catch (err: unknown) {
                const msg = err instanceof Error ? err.message : 'Failed to log wear';
                setErrorMessage(msg);
              }
            }}
            disabled={isLoggingWear}
            activeOpacity={0.8}
            testID="mark-worn-detail-btn"
          >
            {isLoggingWear ? (
              <ActivityIndicator size="small" color="#FFFFFF" />
            ) : (
              <Text style={styles.wearButtonText}>
                {isWorn ? '✓ Worn Today (Tap to Log Again)' : '📅 Mark as Worn Today'}
              </Text>
            )}
          </TouchableOpacity>

          {/* Optional Wear Notes Toggle */}
          {!showNotesInput && (
            <TouchableOpacity
              onPress={() => setShowNotesInput(true)}
              style={styles.notesToggle}
              testID="add-wear-notes-toggle"
            >
              <Text style={styles.notesToggleText}>
                {notes.trim() ? '📝 Edit wear notes' : '+ Add wear notes (e.g. event details)'}
              </Text>
            </TouchableOpacity>
          )}

          {showNotesInput && (
            <View style={styles.notesInputContainer}>
              <TextInput
                style={styles.notesInput}
                placeholder="Notes (e.g., Dinner with friends at Bistro)"
                placeholderTextColor="#64748B"
                value={notes}
                onChangeText={setNotes}
                maxLength={255}
                testID="wear-notes-input"
              />
            </View>
          )}
        </View>

        {/* Return Button */}
        <TouchableOpacity
          style={[styles.button, styles.secondaryButton]}
          onPress={() => router.back()}
          activeOpacity={0.8}
          testID="back-to-recommendations-btn"
        >
          <Text style={styles.secondaryButtonText}>← Back to Recommendations</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 40,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  occasionBadge: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#334155',
  },
  occasionText: {
    color: '#F8FAFC',
    fontSize: 13,
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
    fontSize: 13,
    fontWeight: '500',
  },
  scoreBadge: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 8,
    borderWidth: 1.5,
  },
  scoreValue: {
    fontSize: 16,
    fontWeight: '700',
  },
  rationaleCard: {
    backgroundColor: '#1E293B',
    borderRadius: 14,
    padding: 16,
    marginBottom: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#F59E0B',
    borderWidth: 1,
    borderColor: '#334155',
  },
  rationaleTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  rationaleText: {
    fontSize: 13,
    color: '#CBD5E1',
    lineHeight: 20,
    fontStyle: 'italic',
  },
  compositionSection: {
    marginTop: 8,
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 12,
  },
  garmentCard: {
    flexDirection: 'row',
    backgroundColor: '#1E293B',
    borderRadius: 14,
    padding: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#334155',
    gap: 12,
  },
  garmentImageContainer: {
    width: 90,
    height: 90,
    borderRadius: 8,
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
  garmentRoleTag: {
    position: 'absolute',
    bottom: 3,
    left: 3,
    backgroundColor: 'rgba(15, 23, 42, 0.85)',
    paddingHorizontal: 5,
    paddingVertical: 2,
    borderRadius: 4,
  },
  garmentRoleText: {
    color: '#F8FAFC',
    fontSize: 9,
    fontWeight: '700',
  },
  garmentDetails: {
    flex: 1,
    justifyContent: 'center',
  },
  garmentName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#F8FAFC',
    marginBottom: 2,
  },
  garmentCategory: {
    fontSize: 12,
    color: '#F59E0B',
    fontWeight: '500',
    marginBottom: 8,
  },
  metadataGrid: {
    gap: 4,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  metaLabel: {
    fontSize: 11,
    color: '#64748B',
    fontWeight: '500',
    width: 60,
  },
  metaValue: {
    fontSize: 11,
    color: '#CBD5E1',
    fontWeight: '500',
  },
  colorPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  colorDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    borderWidth: 0.5,
    borderColor: '#64748B',
  },
  button: {
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 4,
  },
  primaryButton: {
    backgroundColor: '#F59E0B',
  },
  primaryButtonText: {
    color: '#0F172A',
    fontSize: 14,
    fontWeight: '700',
  },
  secondaryButton: {
    backgroundColor: '#1E293B',
    borderWidth: 1,
    borderColor: '#334155',
  },
  secondaryButtonText: {
    color: '#F8FAFC',
    fontSize: 14,
    fontWeight: '600',
  },
  centerCard: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  notFoundIcon: {
    fontSize: 40,
    marginBottom: 12,
  },
  notFoundTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  notFoundSubtitle: {
    fontSize: 13,
    color: '#94A3B8',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 20,
  },
  actionsSection: {
    marginBottom: 16,
    gap: 8,
  },
  feedbackBannerSuccess: {
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    borderWidth: 1,
    borderColor: '#10B981',
    borderRadius: 8,
    padding: 12,
    marginBottom: 4,
  },
  feedbackTextSuccess: {
    color: '#34D399',
    fontSize: 13,
    fontWeight: '600',
    lineHeight: 18,
  },
  feedbackBannerError: {
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderWidth: 1,
    borderColor: '#EF4444',
    borderRadius: 8,
    padding: 12,
    marginBottom: 4,
  },
  feedbackTextError: {
    color: '#F87171',
    fontSize: 13,
    fontWeight: '600',
  },
  savedButton: {
    backgroundColor: '#1E293B',
    borderWidth: 1.5,
    borderColor: '#10B981',
  },
  savedButtonText: {
    color: '#10B981',
    fontSize: 14,
    fontWeight: '700',
  },
  wearButton: {
    backgroundColor: '#3B82F6',
  },
  wearButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  wornButton: {
    backgroundColor: '#1E293B',
    borderWidth: 1.5,
    borderColor: '#3B82F6',
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  notesToggle: {
    alignSelf: 'center',
    paddingVertical: 6,
    marginTop: 2,
  },
  notesToggleText: {
    color: '#94A3B8',
    fontSize: 12,
    textDecorationLine: 'underline',
  },
  notesInputContainer: {
    marginTop: 4,
    marginBottom: 4,
  },
  notesInput: {
    backgroundColor: '#1E293B',
    borderWidth: 1,
    borderColor: '#334155',
    borderRadius: 8,
    padding: 10,
    color: '#F8FAFC',
    fontSize: 13,
  },
});
