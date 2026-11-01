import React, { useCallback, useEffect } from 'react';
import {
  ActivityIndicator,
  FlatList,
  RefreshControl,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../../src/context/AuthContext';
import { useOutfitGenerator } from '../../src/context/OutfitContext';
import { AuthenticatedImage } from '../../src/components/AuthenticatedImage';
import { OutfitItemDto, OutfitWearHistoryDto } from '../../src/types/outfit.types';
import {
  formatOccasion,
  formatRole,
  getScoreColor,
} from '../../src/utils/outfitFormatting';

function formatWornDate(dateStr: string): string {
  try {
    const [year, month, day] = dateStr.split('-').map(Number);
    const date = new Date(year, month - 1, day);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const diffTime = today.getTime() - date.getTime();
    const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    if (diffDays > 1 && diffDays < 7) return `${diffDays} days ago`;

    return date.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  } catch {
    return dateStr;
  }
}

export default function OutfitHistoryScreen(): React.JSX.Element {
  const router = useRouter();
  const { isAuthenticated, user } = useAuth();
  const {
    wearHistory,
    isHistoryLoading,
    historyError,
    fetchWearHistory,
  } = useOutfitGenerator();

  useEffect(() => {
    if (isAuthenticated) {
      void fetchWearHistory();
    }
  }, [isAuthenticated, fetchWearHistory]);

  const onRefresh = useCallback(async () => {
    await fetchWearHistory();
  }, [fetchWearHistory]);

  // Auth Guard
  if (!isAuthenticated || !user) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerCard}>
          <Text style={styles.emptyIcon}>🔒</Text>
          <Text style={styles.emptyTitle}>Sign In Required</Text>
          <Text style={styles.emptySubtitle}>
            Please sign in to view your logged outfit history and wear records.
          </Text>
          <TouchableOpacity
            style={[styles.button, styles.primaryButton]}
            onPress={() => router.replace('/')}
            testID="history-sign-in-btn"
          >
            <Text style={styles.primaryButtonText}>Sign In</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

function getCategoryPlaceholderIcon(category: string | null | undefined, role: string): string {
  const cat = category || role;
  switch (cat?.toUpperCase()) {
    case 'TOP':
      return '👕';
    case 'BOTTOM':
      return '👖';
    case 'FOOTWEAR':
      return '👟';
    case 'OUTERWEAR':
      return '🧥';
    case 'ACCESSORY':
      return '🕶️';
    default:
      return '👔';
  }
}

  const renderWearItem = ({ item }: { item: OutfitWearHistoryDto }) => {
    const hasNumericScore = item.suitabilityScore !== null && item.suitabilityScore !== undefined;
    const scoreColor = hasNumericScore
      ? getScoreColor(item.suitabilityScore as number)
      : '#94A3B8';

    return (
      <View style={styles.wearCard} testID={`wear-log-card-${item.logId}`}>
        {/* Card Header: Worn Date & Occasion */}
        <View style={styles.cardHeader}>
          <View style={styles.dateBadge}>
            <Text style={styles.dateIcon}>📅</Text>
            <Text style={styles.dateText}>{formatWornDate(item.wornDate)}</Text>
          </View>

          <View style={styles.headerRight}>
            {item.occasion && (
              <View style={styles.occasionPill}>
                <Text style={styles.occasionText}>{formatOccasion(item.occasion)}</Text>
              </View>
            )}

            {hasNumericScore ? (
              <View style={[styles.scoreBadge, { borderColor: scoreColor }]}>
                <Text style={[styles.scoreText, { color: scoreColor }]}>
                  {item.suitabilityScore}
                </Text>
              </View>
            ) : (
              <View style={[styles.scoreBadge, styles.customScoreBadge]}>
                <Text style={styles.customScoreText}>
                  {item.scoreStatus === 'CUSTOM' ? 'Custom' : 'Unscored'}
                </Text>
              </View>
            )}
          </View>
        </View>

        {/* Outfit Name if available */}
        {item.outfitName ? (
          <View style={styles.outfitNameContainer}>
            <Text style={styles.outfitNameText} numberOfLines={1}>
              {item.outfitName}
            </Text>
          </View>
        ) : null}

        {/* User Notes if present */}
        {item.notes && (
          <View style={styles.notesContainer}>
            <Text style={styles.notesLabel}>Notes:</Text>
            <Text style={styles.notesText}>{item.notes}</Text>
          </View>
        )}

        {/* Garment Thumbnails Row */}
        <View style={styles.garmentsRow}>
          {item.items.map((garment: OutfitItemDto, idx: number) => {
            const garmentKey = garment.id ? `${garment.id}-${idx}` : `snapshot-${garment.role}-${idx}`;
            return (
              <View key={garmentKey} style={styles.garmentBox}>
                <View style={styles.thumbnailContainer}>
                  {garment.downloadUrl ? (
                    <AuthenticatedImage
                      uri={garment.downloadUrl}
                      style={styles.thumbnail}
                      testID={`history-garment-img-${garment.id || idx}`}
                      accessibilityLabel={garment.name}
                    />
                  ) : (
                    <View
                      style={styles.thumbnailPlaceholder}
                      testID={`history-garment-placeholder-${garment.id || idx}`}
                    >
                      <Text style={styles.placeholderIcon}>
                        {getCategoryPlaceholderIcon(garment.category, garment.role)}
                      </Text>
                    </View>
                  )}
                  <View style={styles.roleTag}>
                    <Text style={styles.roleTagText}>{formatRole(garment.role)}</Text>
                  </View>
                </View>
                <Text style={styles.garmentName} numberOfLines={1}>
                  {garment.name}
                </Text>
              </View>
            );
          })}
        </View>

        {/* Styling Rationale if available */}
        {item.stylingRationale ? (
          <View style={styles.rationaleContainer}>
            <Text style={styles.rationaleText} numberOfLines={2}>
              "{item.stylingRationale}"
            </Text>
          </View>
        ) : null}
      </View>
    );
  };

  const renderEmptyComponent = () => {
    if (isHistoryLoading) {
      return (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#F59E0B" />
          <Text style={styles.loadingText}>Loading outfit wear history...</Text>
        </View>
      );
    }

    if (historyError) {
      return (
        <View style={styles.emptyCard} testID="history-error-card">
          <Text style={styles.emptyIcon}>⚠️</Text>
          <Text style={styles.emptyTitle}>Unable to Load History</Text>
          <Text style={styles.emptySubtitle}>{historyError}</Text>
          <TouchableOpacity
            style={[styles.button, styles.primaryButton]}
            onPress={() => void fetchWearHistory()}
            testID="history-retry-btn"
          >
            <Text style={styles.primaryButtonText}>Try Again</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return (
      <View style={styles.emptyCard} testID="history-empty-card">
        <Text style={styles.emptyIcon}>👔</Text>
        <Text style={styles.emptyTitle}>No Worn Outfits Yet</Text>
        <Text style={styles.emptySubtitle}>
          When you wear an outfit from your curated recommendations, tap "Mark as Worn
          Today" to track your style journey.
        </Text>
        <Text style={styles.emptyTip}>
          Recent wears apply repetition penalties to help keep your daily look fresh.
        </Text>
        <TouchableOpacity
          style={[styles.button, styles.primaryButton]}
          onPress={() => router.push('/outfits')}
          testID="history-generate-nav-btn"
        >
          <Text style={styles.primaryButtonText}>✨ Generate Outfits</Text>
        </TouchableOpacity>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Screen Header */}
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Back to Curated Outfits"
          testID="history-back-btn"
        >
          <Text style={styles.backButtonText}>←</Text>
        </TouchableOpacity>
        <View style={styles.headerTitleContainer}>
          <Text style={styles.headerTitle}>Outfit History</Text>
          <Text style={styles.headerSubtitle}>
            {wearHistory.length} {wearHistory.length === 1 ? 'outfit' : 'outfits'} logged
          </Text>
        </View>
      </View>

      <FlatList
        data={wearHistory}
        keyExtractor={(item: OutfitWearHistoryDto) => item.logId}
        renderItem={renderWearItem}
        ListEmptyComponent={renderEmptyComponent}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={isHistoryLoading}
            onRefresh={onRefresh}
            tintColor="#F59E0B"
            colors={['#F59E0B']}
          />
        }
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
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1E293B',
  },
  backButton: {
    padding: 8,
    marginRight: 8,
  },
  backButtonText: {
    color: '#F8FAFC',
    fontSize: 20,
    fontWeight: '700',
  },
  headerTitleContainer: {
    flex: 1,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  headerSubtitle: {
    fontSize: 12,
    color: '#94A3B8',
    marginTop: 2,
  },
  listContent: {
    padding: 16,
    paddingBottom: 40,
    flexGrow: 1,
  },
  wearCard: {
    backgroundColor: '#1E293B',
    borderRadius: 14,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#334155',
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  dateBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  dateIcon: {
    fontSize: 14,
  },
  dateText: {
    color: '#F8FAFC',
    fontSize: 14,
    fontWeight: '700',
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  occasionPill: {
    backgroundColor: '#0F172A',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  occasionText: {
    color: '#CBD5E1',
    fontSize: 11,
    fontWeight: '600',
  },
  scoreBadge: {
    backgroundColor: '#0F172A',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    borderWidth: 1,
  },
  customScoreBadge: {
    borderColor: '#64748B',
    borderStyle: 'dashed',
  },
  scoreText: {
    fontSize: 12,
    fontWeight: '700',
  },
  customScoreText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#94A3B8',
  },
  outfitNameContainer: {
    marginBottom: 8,
  },
  outfitNameText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#F1F5F9',
  },
  notesContainer: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
    borderLeftWidth: 3,
    borderLeftColor: '#3B82F6',
  },
  notesLabel: {
    fontSize: 10,
    fontWeight: '700',
    color: '#64748B',
    textTransform: 'uppercase',
    marginBottom: 2,
  },
  notesText: {
    fontSize: 12,
    color: '#E2E8F0',
    lineHeight: 16,
  },
  garmentsRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 10,
  },
  garmentBox: {
    flex: 1,
    maxWidth: 90,
  },
  thumbnailContainer: {
    width: '100%',
    aspectRatio: 1,
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: '#0F172A',
    borderWidth: 1,
    borderColor: '#334155',
    position: 'relative',
  },
  thumbnailPlaceholder: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#1E293B',
  },
  placeholderIcon: {
    fontSize: 24,
  },
  thumbnail: {
    width: '100%',
    height: '100%',
  },
  roleTag: {
    position: 'absolute',
    bottom: 2,
    left: 2,
    backgroundColor: 'rgba(15, 23, 42, 0.85)',
    paddingHorizontal: 4,
    paddingVertical: 1,
    borderRadius: 3,
  },
  roleTagText: {
    color: '#F8FAFC',
    fontSize: 8,
    fontWeight: '700',
  },
  garmentName: {
    fontSize: 11,
    color: '#CBD5E1',
    marginTop: 4,
    fontWeight: '500',
  },
  rationaleContainer: {
    borderTopWidth: 1,
    borderTopColor: '#334155',
    paddingTop: 8,
    marginTop: 2,
  },
  rationaleText: {
    fontSize: 12,
    color: '#94A3B8',
    fontStyle: 'italic',
    lineHeight: 16,
  },
  loadingContainer: {
    paddingVertical: 60,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  loadingText: {
    color: '#94A3B8',
    fontSize: 13,
  },
  emptyCard: {
    paddingVertical: 60,
    paddingHorizontal: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 13,
    color: '#94A3B8',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 12,
  },
  emptyTip: {
    fontSize: 12,
    color: '#F59E0B',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 24,
  },
  centerCard: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  button: {
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButton: {
    backgroundColor: '#F59E0B',
  },
  primaryButtonText: {
    color: '#0F172A',
    fontSize: 14,
    fontWeight: '700',
  },
});
