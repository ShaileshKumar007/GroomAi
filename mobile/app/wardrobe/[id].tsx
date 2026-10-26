import React, { useEffect, useState, useCallback } from 'react';
import {
  ActivityIndicator,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { apiGetWardrobeItemById } from '../../src/api/client';
import { AuthenticatedImage } from '../../src/components/AuthenticatedImage';
import { WardrobeItemResponse } from '../../src/types/wardrobe.types';
import { formatWardrobeError } from '../../src/utils/wardrobeWorkflow';

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

export default function WardrobeItemDetailScreen(): React.JSX.Element {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();

  const [item, setItem] = useState<WardrobeItemResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchItem = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const data = await apiGetWardrobeItemById(id);
      setItem(data);
    } catch (err) {
      const formatted = formatWardrobeError(err);
      setErrorMessage(formatted.message);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void fetchItem();
  }, [fetchItem]);

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color="#F59E0B" />
          <Text style={styles.loadingText}>Loading garment details...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (errorMessage || !item) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerCard}>
          <Text style={styles.errorTitle}>Garment Not Found</Text>
          <Text style={styles.errorMessage}>
            {errorMessage || 'The requested clothing item could not be retrieved.'}
          </Text>
          <View style={styles.actionRow}>
            <TouchableOpacity
              style={[styles.button, styles.primaryButton]}
              onPress={fetchItem}
            >
              <Text style={styles.primaryButtonText}>Retry</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, styles.secondaryButton]}
              onPress={() => router.back()}
            >
              <Text style={styles.secondaryButtonText}>Back to Wardrobe</Text>
            </TouchableOpacity>
          </View>
        </View>
      </SafeAreaView>
    );
  }

  const primaryColorHex = COLOR_HEX_MAP[item.primaryColor] || '#94A3B8';
  const secondaryColorHex = item.secondaryColor
    ? COLOR_HEX_MAP[item.secondaryColor] || '#94A3B8'
    : null;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Garment Image */}
        <View style={styles.imageCard}>
          <AuthenticatedImage
            uri={item.downloadUrl}
            style={styles.detailImage}
            resizeMode="cover"
            accessibilityLabel={item.name}
          />
        </View>

        {/* Header Title & Status */}
        <View style={styles.titleCard}>
          <View style={styles.titleRow}>
            <Text style={styles.garmentName}>{item.name}</Text>
            <View style={styles.statusBadge}>
              <Text style={styles.statusBadgeText}>{item.status}</Text>
            </View>
          </View>
          <Text style={styles.categorySubhead}>
            {item.category}
            {item.subcategory ? ` • ${item.subcategory}` : ''}
          </Text>
        </View>

        {/* Metadata Properties Grid */}
        <View style={styles.detailsCard}>
          <Text style={styles.sectionHeader}>Garment Attributes</Text>

          <View style={styles.gridRow}>
            <View style={styles.propertyItem}>
              <Text style={styles.propertyLabel}>Primary Color</Text>
              <View style={styles.colorPill}>
                <View
                  style={[styles.colorDot, { backgroundColor: primaryColorHex }]}
                />
                <Text style={styles.propertyValue}>
                  {formatEnumLabel(item.primaryColor)}
                </Text>
              </View>
            </View>

            <View style={styles.propertyItem}>
              <Text style={styles.propertyLabel}>Secondary Color</Text>
              {secondaryColorHex && item.secondaryColor ? (
                <View style={styles.colorPill}>
                  <View
                    style={[styles.colorDot, { backgroundColor: secondaryColorHex }]}
                  />
                  <Text style={styles.propertyValue}>
                    {formatEnumLabel(item.secondaryColor)}
                  </Text>
                </View>
              ) : (
                <Text style={styles.propertyValueMuted}>None</Text>
              )}
            </View>
          </View>

          <View style={styles.gridRow}>
            <View style={styles.propertyItem}>
              <Text style={styles.propertyLabel}>Formality</Text>
              <Text style={styles.propertyValue}>
                {formatEnumLabel(item.formalityLevel)}
              </Text>
            </View>

            <View style={styles.propertyItem}>
              <Text style={styles.propertyLabel}>Seasonality</Text>
              <Text style={styles.propertyValue}>
                {formatEnumLabel(item.season)}
              </Text>
            </View>
          </View>

          <View style={styles.gridRow}>
            <View style={styles.propertyItem}>
              <Text style={styles.propertyLabel}>Pattern</Text>
              <Text style={styles.propertyValue}>
                {formatEnumLabel(item.pattern)}
              </Text>
            </View>

            <View style={styles.propertyItem}>
              <Text style={styles.propertyLabel}>Cataloged On</Text>
              <Text style={styles.propertyValue}>
                {new Date(item.createdAt).toLocaleDateString()}
              </Text>
            </View>
          </View>
        </View>

        {/* Back Navigation Button */}
        <TouchableOpacity
          style={[styles.button, styles.secondaryButton, styles.backButton]}
          onPress={() => router.back()}
        >
          <Text style={styles.secondaryButtonText}>← Return to Wardrobe</Text>
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
    padding: 16,
    paddingBottom: 40,
  },
  centerContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  loadingText: {
    marginTop: 12,
    color: '#F59E0B',
    fontSize: 14,
  },
  centerCard: {
    margin: 20,
    padding: 24,
    backgroundColor: '#1E293B',
    borderRadius: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#334155',
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  errorMessage: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 20,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
  },
  imageCard: {
    width: '100%',
    height: 320,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: '#1E293B',
    borderWidth: 1,
    borderColor: '#334155',
    marginBottom: 16,
  },
  detailImage: {
    width: '100%',
    height: '100%',
  },
  titleCard: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
    marginBottom: 16,
  },
  titleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 10,
    marginBottom: 6,
  },
  garmentName: {
    flex: 1,
    fontSize: 20,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  statusBadge: {
    backgroundColor: '#0F766E',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  statusBadgeText: {
    color: '#CCFBF1',
    fontSize: 11,
    fontWeight: '700',
  },
  categorySubhead: {
    fontSize: 14,
    color: '#F59E0B',
    fontWeight: '600',
  },
  detailsCard: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
    marginBottom: 20,
  },
  sectionHeader: {
    fontSize: 15,
    fontWeight: '700',
    color: '#E2E8F0',
    marginBottom: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
    paddingBottom: 8,
  },
  gridRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 14,
  },
  propertyItem: {
    flex: 1,
  },
  propertyLabel: {
    fontSize: 12,
    color: '#64748B',
    marginBottom: 4,
    fontWeight: '500',
  },
  propertyValue: {
    fontSize: 14,
    color: '#F8FAFC',
    fontWeight: '600',
  },
  propertyValueMuted: {
    fontSize: 14,
    color: '#64748B',
    fontStyle: 'italic',
  },
  colorPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  colorDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
  button: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButton: {
    backgroundColor: '#F59E0B',
  },
  primaryButtonText: {
    color: '#0F172A',
    fontSize: 14,
    fontWeight: '600',
  },
  secondaryButton: {
    backgroundColor: '#334155',
  },
  secondaryButtonText: {
    color: '#F8FAFC',
    fontSize: 14,
    fontWeight: '500',
  },
  backButton: {
    width: '100%',
  },
});
