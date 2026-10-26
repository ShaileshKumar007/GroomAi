import React from 'react';
import {
  ActivityIndicator,
  FlatList,
  RefreshControl,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../../src/context/AuthContext';
import { useWardrobeCatalog } from '../../src/hooks/useWardrobeCatalog';
import { AuthenticatedImage } from '../../src/components/AuthenticatedImage';
import { Category, WardrobeItemResponse } from '../../src/types/wardrobe.types';
import { VALID_CATEGORIES } from '../../src/utils/wardrobeValidation';

const ALL_CATEGORY_FILTERS: readonly (Category | 'ALL')[] = [
  'ALL',
  ...VALID_CATEGORIES,
];

// Color mapping for color dots
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

export default function WardrobeCatalogScreen(): React.JSX.Element {
  const router = useRouter();
  const { isAuthenticated, user } = useAuth();
  const {
    items,
    isLoading,
    isRefreshing,
    errorMessage,
    selectedCategory,
    totalElements,
    selectCategory,
    refreshCatalog,
    retryLoading,
  } = useWardrobeCatalog();

  // Unauthenticated guard
  if (!isAuthenticated || !user) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerCard}>
          <Text style={styles.title}>Digital Wardrobe</Text>
          <Text style={styles.subtitle}>
            Please sign in to view and manage your digital wardrobe.
          </Text>
          <TouchableOpacity
            style={[styles.button, styles.primaryButton]}
            onPress={() => router.replace('/')}
          >
            <Text style={styles.primaryButtonText}>Sign In</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const renderItem = ({ item }: { item: WardrobeItemResponse }) => {
    const colorHex = COLOR_HEX_MAP[item.primaryColor] || '#94A3B8';

    return (
      <TouchableOpacity
        style={styles.itemCard}
        onPress={() => router.push(`/wardrobe/${item.id}`)}
        activeOpacity={0.8}
        accessibilityLabel={`View details for ${item.name}`}
      >
        <View style={styles.imageWrapper}>
          <AuthenticatedImage
            uri={item.downloadUrl}
            style={styles.itemImage}
            resizeMode="cover"
            accessibilityLabel={item.name}
          />
          <View style={styles.categoryBadge}>
            <Text style={styles.categoryBadgeText}>{item.category}</Text>
          </View>
        </View>

        <View style={styles.itemContent}>
          <Text style={styles.itemName} numberOfLines={1} ellipsizeMode="tail">
            {item.name}
          </Text>

          <View style={styles.itemMetaRow}>
            <View style={styles.colorPill}>
              <View style={[styles.colorDot, { backgroundColor: colorHex }]} />
              <Text style={styles.colorText}>{item.primaryColor}</Text>
            </View>
            {item.subcategory && (
              <Text style={styles.subcategoryText} numberOfLines={1}>
                {item.subcategory}
              </Text>
            )}
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Category Filter Bar */}
      <View style={styles.filterBarContainer}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterScrollContent}
        >
          {ALL_CATEGORY_FILTERS.map((cat) => {
            const isSelected = selectedCategory === cat;
            return (
              <TouchableOpacity
                key={cat}
                style={[styles.filterChip, isSelected && styles.filterChipSelected]}
                onPress={() => selectCategory(cat)}
                accessibilityLabel={`Filter by ${cat}`}
              >
                <Text
                  style={[
                    styles.filterChipText,
                    isSelected && styles.filterChipTextSelected,
                  ]}
                >
                  {cat}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      </View>

      {/* Error Banner */}
      {errorMessage && (
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>{errorMessage}</Text>
          <TouchableOpacity style={styles.retryButton} onPress={retryLoading}>
            <Text style={styles.retryButtonText}>Retry</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Main List / Grid */}
      {isLoading && items.length === 0 ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#F59E0B" />
          <Text style={styles.loadingText}>Loading your wardrobe...</Text>
        </View>
      ) : items.length === 0 ? (
        <ScrollView
          contentContainerStyle={styles.emptyContainer}
          refreshControl={
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={refreshCatalog}
              tintColor="#F59E0B"
            />
          }
        >
          <Text style={styles.emptyIcon}>🧥</Text>
          <Text style={styles.emptyTitle}>
            {selectedCategory === 'ALL'
              ? 'Your wardrobe is empty'
              : `No ${selectedCategory.toLowerCase()} items found`}
          </Text>
          <Text style={styles.emptySubtitle}>
            {selectedCategory === 'ALL'
              ? 'Catalog your first piece of clothing to start building your personal AI wardrobe.'
              : 'Try selecting another category or add a new garment to this category.'}
          </Text>
          <TouchableOpacity
            style={[styles.button, styles.primaryButton, styles.emptyButton]}
            onPress={() => router.push('/wardrobe/add')}
          >
            <Text style={styles.primaryButtonText}>+ Add Garment</Text>
          </TouchableOpacity>
        </ScrollView>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.id}
          renderItem={renderItem}
          numColumns={2}
          contentContainerStyle={styles.gridContent}
          columnWrapperStyle={styles.columnWrapper}
          refreshControl={
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={refreshCatalog}
              tintColor="#F59E0B"
            />
          }
          ListHeaderComponent={
            <View style={styles.listHeader}>
              <Text style={styles.listHeaderCount}>
                {totalElements} {totalElements === 1 ? 'item' : 'items'}
              </Text>
              <TouchableOpacity
                style={styles.addInlineButton}
                onPress={() => router.push('/wardrobe/add')}
              >
                <Text style={styles.addInlineButtonText}>+ Add Item</Text>
              </TouchableOpacity>
            </View>
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
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
  title: {
    fontSize: 22,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    marginBottom: 16,
  },
  filterBarContainer: {
    backgroundColor: '#1E293B',
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
    paddingVertical: 10,
  },
  filterScrollContent: {
    paddingHorizontal: 16,
    gap: 8,
  },
  filterChip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
    backgroundColor: '#0F172A',
    borderWidth: 1,
    borderColor: '#334155',
  },
  filterChipSelected: {
    backgroundColor: '#F59E0B',
    borderColor: '#F59E0B',
  },
  filterChipText: {
    fontSize: 13,
    color: '#94A3B8',
    fontWeight: '600',
  },
  filterChipTextSelected: {
    color: '#0F172A',
    fontWeight: '700',
  },
  listHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
    paddingHorizontal: 4,
  },
  listHeaderCount: {
    fontSize: 14,
    fontWeight: '600',
    color: '#94A3B8',
  },
  addInlineButton: {
    backgroundColor: '#F59E0B',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  addInlineButtonText: {
    color: '#0F172A',
    fontSize: 13,
    fontWeight: '700',
  },
  gridContent: {
    padding: 12,
    paddingBottom: 32,
  },
  columnWrapper: {
    gap: 12,
    marginBottom: 12,
  },
  itemCard: {
    flex: 1,
    backgroundColor: '#1E293B',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#334155',
    overflow: 'hidden',
  },
  imageWrapper: {
    width: '100%',
    height: 160,
    backgroundColor: '#0F172A',
    position: 'relative',
  },
  itemImage: {
    width: '100%',
    height: '100%',
  },
  categoryBadge: {
    position: 'absolute',
    top: 8,
    left: 8,
    backgroundColor: 'rgba(15, 23, 42, 0.85)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 4,
  },
  categoryBadgeText: {
    color: '#E2E8F0',
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  itemContent: {
    padding: 10,
  },
  itemName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#F8FAFC',
    marginBottom: 6,
  },
  itemMetaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  colorPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  colorDot: {
    width: 9,
    height: 9,
    borderRadius: 5,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
  colorText: {
    fontSize: 11,
    color: '#94A3B8',
    fontWeight: '500',
  },
  subcategoryText: {
    fontSize: 11,
    color: '#64748B',
    maxWidth: '50%',
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  loadingText: {
    color: '#F59E0B',
    fontSize: 14,
    marginTop: 12,
  },
  emptyContainer: {
    flexGrow: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  emptyIcon: {
    fontSize: 56,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
    textAlign: 'center',
  },
  emptySubtitle: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 20,
  },
  emptyButton: {
    minWidth: 180,
  },
  errorContainer: {
    backgroundColor: '#7F1D1D',
    borderColor: '#DC2626',
    borderWidth: 1,
    margin: 16,
    padding: 12,
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  errorText: {
    color: '#FEE2E2',
    fontSize: 13,
    flex: 1,
    marginRight: 8,
  },
  retryButton: {
    backgroundColor: '#DC2626',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
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
    fontSize: 15,
    fontWeight: '600',
  },
});
