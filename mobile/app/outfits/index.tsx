import React from 'react';
import {
  ActivityIndicator,
  FlatList,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../../src/context/AuthContext';
import { useOutfitGenerator } from '../../src/context/OutfitContext';
import { OccasionSelector } from '../../src/components/OccasionSelector';
import { WeatherContextSelector } from '../../src/components/WeatherContextSelector';
import { OutfitCard } from '../../src/components/OutfitCard';
import { OutfitRecommendationDto } from '../../src/types/outfit.types';
import { formatMissingCategories } from '../../src/utils/outfitFormatting';

export default function OutfitRecommendationScreen(): React.JSX.Element {
  const router = useRouter();
  const { isAuthenticated, user } = useAuth();
  const {
    selectedOccasion,
    selectedTemperature,
    selectedWeatherCondition,
    recommendations,
    warnings,
    isLoading,
    hasGenerated,
    error,
    savedOutfitsMap,
    savingOutfitId,
    saveOutfit,
    setOccasion,
    setTemperature,
    setWeatherCondition,
    clearWeather,
    generateOutfits,
    retry,
    resetGenerator,
  } = useOutfitGenerator();

  // Authentication Guard
  if (!isAuthenticated || !user) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerContainer}>
          <Text style={styles.emptyTitle}>Personalized Outfits</Text>
          <Text style={styles.emptySubtitle}>
            Please sign in to receive outfit recommendations from your wardrobe.
          </Text>
          <TouchableOpacity
            style={[styles.button, styles.primaryButton]}
            onPress={() => router.replace('/')}
            testID="sign-in-redirect-btn"
          >
            <Text style={styles.primaryButtonText}>Sign In</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const renderHeader = () => (
    <View style={styles.headerContainer}>
      {/* Intro Heading with History Action */}
      <View style={styles.titleSection}>
        <View style={styles.titleRow}>
          <View style={styles.titleTextContainer}>
            <Text style={styles.screenTitle}>Curated Outfits</Text>
            <Text style={styles.screenSubtitle}>
              Combinations crafted exclusively from your active wardrobe.
            </Text>
          </View>
          <TouchableOpacity
            style={styles.historyButton}
            onPress={() => router.push('/outfits/history')}
            testID="view-history-header-btn"
            accessibilityRole="button"
            accessibilityLabel="View outfit wear history"
          >
            <Text style={styles.historyButtonText}>📜 History</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Occasion Selection */}
      <OccasionSelector
        selectedOccasion={selectedOccasion}
        onSelectOccasion={setOccasion}
        disabled={isLoading}
      />

      {/* Weather Context Selection */}
      <WeatherContextSelector
        selectedTemperature={selectedTemperature}
        selectedWeatherCondition={selectedWeatherCondition}
        onSelectTemperature={setTemperature}
        onSelectWeatherCondition={setWeatherCondition}
        onClearWeather={clearWeather}
        disabled={isLoading}
      />

      {/* Primary Action Button */}
      <TouchableOpacity
        style={[styles.button, styles.generateButton, isLoading && styles.buttonDisabled]}
        onPress={generateOutfits}
        disabled={isLoading}
        activeOpacity={0.8}
        accessibilityRole="button"
        accessibilityLabel="Generate Outfits"
        testID="generate-outfits-button"
      >
        {isLoading ? (
          <View style={styles.loadingRow}>
            <ActivityIndicator size="small" color="#0F172A" />
            <Text style={styles.generateButtonText}>Curating Outfits...</Text>
          </View>
        ) : (
          <Text style={styles.generateButtonText}>
            {hasGenerated ? '✨ Regenerate Outfits' : '✨ Generate Outfits'}
          </Text>
        )}
      </TouchableOpacity>

      {/* Warnings Banner if returned by backend */}
      {warnings.length > 0 && (
        <View style={styles.warningBanner} testID="outfits-warning-banner">
          {warnings.map((warning, idx) => (
            <Text key={idx} style={styles.warningText}>
              ℹ️ {warning}
            </Text>
          ))}
        </View>
      )}

      {/* General Error Banner (Network / 500 / 400) */}
      {error && error.code !== 'INSUFFICIENT_WARDROBE_ITEMS' && (
        <View style={styles.errorBanner} testID="outfits-error-banner">
          <Text style={styles.errorTitle}>Unable to Generate Outfits</Text>
          <Text style={styles.errorMessage}>{error.message}</Text>
          <TouchableOpacity
            style={styles.retryButton}
            onPress={retry}
            activeOpacity={0.7}
            testID="retry-generate-button"
          >
            <Text style={styles.retryButtonText}>Try Again</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Insufficient Wardrobe State (HTTP 422) */}
      {error && error.code === 'INSUFFICIENT_WARDROBE_ITEMS' && (
        <View style={styles.insufficientCard} testID="insufficient-wardrobe-card">
          <Text style={styles.insufficientIcon}>👗</Text>
          <Text style={styles.insufficientTitle}>Wardrobe Building Required</Text>
          <Text style={styles.insufficientText}>
            To generate full outfit recommendations, you need at least one Top, one Bottom,
            and one Footwear item cataloged in your active wardrobe.
          </Text>
          {error.missingCategories && error.missingCategories.length > 0 && (
            <View style={styles.missingPillsContainer}>
              <Text style={styles.missingPillsLabel}>
                Missing {formatMissingCategories(error.missingCategories)}:
              </Text>
              <View style={styles.pillsRow}>
                {error.missingCategories.map((cat) => (
                  <View key={cat} style={styles.missingPill}>
                    <Text style={styles.missingPillText}>{cat}</Text>
                  </View>
                ))}
              </View>
            </View>
          )}
          <View style={styles.insufficientActions}>
            <TouchableOpacity
              style={[styles.button, styles.primaryButton]}
              onPress={() => router.push('/wardrobe/add')}
              testID="add-missing-items-button"
            >
              <Text style={styles.primaryButtonText}>+ Add Missing Garments</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, styles.secondaryButton]}
              onPress={() => router.push('/wardrobe')}
              testID="view-wardrobe-button"
            >
              <Text style={styles.secondaryButtonText}>View Wardrobe</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* Empty Recommendations State (HTTP 200 with 0 outfits) */}
      {!isLoading && !error && hasGenerated && recommendations.length === 0 && (
        <View style={styles.emptyOutfitsCard} testID="empty-outfits-card">
          <Text style={styles.emptyOutfitsIcon}>🔍</Text>
          <Text style={styles.emptyOutfitsTitle}>No Matching Combinations</Text>
          <Text style={styles.emptyOutfitsText}>
            None of your active items formed a complete Top + Bottom + Footwear pairing meeting
            the formality constraints for this occasion.
          </Text>
          <Text style={styles.emptyOutfitsHint}>
            Try selecting a different occasion (e.g. Casual or Smart Casual) or adding more
            complementary clothing pieces.
          </Text>
          <TouchableOpacity
            style={[styles.button, styles.secondaryButton]}
            onPress={resetGenerator}
            testID="reset-generator-button"
          >
            <Text style={styles.secondaryButtonText}>Reset Filters</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Initial Prompt State (before first generation) */}
      {!isLoading && !error && !hasGenerated && (
        <View style={styles.initialPromptCard} testID="initial-prompt-card">
          <Text style={styles.promptIcon}>✨</Text>
          <Text style={styles.promptTitle}>Ready to Style You</Text>
          <Text style={styles.promptText}>
            Select your target occasion above, optionally specify the weather, and tap
            Generate Outfits. Our deterministic styling engine evaluates color harmony,
            formality clustering, and garment seasonality.
          </Text>
          <View style={styles.ruleBadge}>
            <Text style={styles.ruleBadgeText}>
              🛡️ Zero Hallucination: Recommends only clothes you already own.
            </Text>
          </View>
        </View>
      )}

      {/* Recommendations Header if available */}
      {!isLoading && recommendations.length > 0 && (
        <View style={styles.resultsHeader}>
          <Text style={styles.resultsTitle}>
            Recommended Combinations ({recommendations.length})
          </Text>
          <Text style={styles.resultsSubtitle}>
            Ranked by color harmony, formality, and seasonal alignment.
          </Text>
        </View>
      )}
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={isLoading || error ? [] : recommendations}
        keyExtractor={(item: OutfitRecommendationDto) => item.id}
        renderItem={({ item }: { item: OutfitRecommendationDto }) => (
          <OutfitCard
            outfit={item}
            onPress={() => router.push(`/outfits/${item.id}`)}
            isSaved={Boolean(savedOutfitsMap[item.id])}
            isSaving={savingOutfitId === item.id}
            onSave={saveOutfit}
          />
        )}
        ListHeaderComponent={renderHeader}
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  listContent: {
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 40,
  },
  headerContainer: {
    marginBottom: 8,
  },
  titleSection: {
    marginBottom: 20,
  },
  titleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  titleTextContainer: {
    flex: 1,
    paddingRight: 12,
  },
  historyButton: {
    backgroundColor: '#1E293B',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#334155',
  },
  historyButtonText: {
    color: '#F8FAFC',
    fontSize: 12,
    fontWeight: '600',
  },
  screenTitle: {
    fontSize: 26,
    fontWeight: '700',
    color: '#F8FAFC',
    letterSpacing: -0.5,
  },
  screenSubtitle: {
    fontSize: 14,
    color: '#94A3B8',
    marginTop: 4,
    lineHeight: 20,
  },
  button: {
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 4,
  },
  generateButton: {
    backgroundColor: '#F59E0B',
    marginBottom: 20,
  },
  generateButtonText: {
    color: '#0F172A',
    fontSize: 15,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  buttonDisabled: {
    opacity: 0.7,
  },
  loadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
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
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    marginBottom: 24,
    lineHeight: 20,
  },
  warningBanner: {
    backgroundColor: '#1E293B',
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
    borderLeftWidth: 3,
    borderLeftColor: '#38BDF8',
    gap: 4,
  },
  warningText: {
    color: '#E0F2FE',
    fontSize: 12,
    lineHeight: 18,
  },
  errorBanner: {
    backgroundColor: '#7F1D1D',
    borderRadius: 10,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#DC2626',
  },
  errorTitle: {
    color: '#FEE2E2',
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 4,
  },
  errorMessage: {
    color: '#FECACA',
    fontSize: 13,
    lineHeight: 18,
    marginBottom: 12,
  },
  retryButton: {
    alignSelf: 'flex-start',
    backgroundColor: '#B91C1C',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
  },
  insufficientCard: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 20,
    alignItems: 'center',
    marginVertical: 12,
    borderWidth: 1,
    borderColor: '#F59E0B',
  },
  insufficientIcon: {
    fontSize: 36,
    marginBottom: 12,
  },
  insufficientTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
    textAlign: 'center',
  },
  insufficientText: {
    fontSize: 13,
    color: '#CBD5E1',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 16,
  },
  missingPillsContainer: {
    width: '100%',
    marginBottom: 16,
    alignItems: 'center',
  },
  missingPillsLabel: {
    fontSize: 12,
    color: '#F59E0B',
    fontWeight: '600',
    marginBottom: 6,
  },
  pillsRow: {
    flexDirection: 'row',
    gap: 8,
  },
  missingPill: {
    backgroundColor: '#0F172A',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#EF4444',
  },
  missingPillText: {
    color: '#FCA5A5',
    fontSize: 12,
    fontWeight: '700',
  },
  insufficientActions: {
    width: '100%',
    gap: 8,
  },
  emptyOutfitsCard: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
    marginVertical: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  emptyOutfitsIcon: {
    fontSize: 32,
    marginBottom: 10,
  },
  emptyOutfitsTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 6,
  },
  emptyOutfitsText: {
    fontSize: 13,
    color: '#CBD5E1',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 8,
  },
  emptyOutfitsHint: {
    fontSize: 12,
    color: '#94A3B8',
    textAlign: 'center',
    lineHeight: 16,
    marginBottom: 16,
  },
  initialPromptCard: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 20,
    alignItems: 'center',
    marginVertical: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  promptIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  promptTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 6,
  },
  promptText: {
    fontSize: 13,
    color: '#94A3B8',
    textAlign: 'center',
    lineHeight: 19,
    marginBottom: 14,
  },
  ruleBadge: {
    backgroundColor: '#0F172A',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#334155',
  },
  ruleBadgeText: {
    color: '#10B981',
    fontSize: 11,
    fontWeight: '600',
  },
  resultsHeader: {
    marginTop: 10,
    marginBottom: 14,
  },
  resultsTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  resultsSubtitle: {
    fontSize: 12,
    color: '#94A3B8',
    marginTop: 2,
  },
});
