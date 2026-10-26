import React from 'react';
import {
  ActivityIndicator,
  Image,
  Linking,
  Platform,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../../src/context/AuthContext';
import { useWardrobeEntry } from '../../src/hooks/useWardrobeEntry';
import {
  VALID_CATEGORIES,
  VALID_COLORS,
  VALID_FORMALITIES,
  VALID_PATTERNS,
  VALID_SEASONS,
} from '../../src/utils/wardrobeValidation';


export default function WardrobeAddScreen(): React.JSX.Element {
  const router = useRouter();
  const { isAuthenticated, user } = useAuth();
  const {
    selectedImage,
    isProcessing,
    metadata,
    validationErrors,
    uploadStep,
    catalogedItem,
    errorMessage,
    errorCode,
    handlePickImage,
    handleRemoveImage,
    updateMetadata,
    handleSubmitCatalogItem,
    resetForm,
  } = useWardrobeEntry();

  const isSubmitting =
    uploadStep === 'requesting_intent' ||
    uploadStep === 'uploading_binary' ||
    uploadStep === 'cataloging';

  // Unauthenticated guard
  if (!isAuthenticated || !user) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerCard}>
          <Text style={styles.title}>Wardrobe Entry</Text>
          <Text style={styles.subtitle}>
            You must be signed in to add items to your digital wardrobe.
          </Text>
          <TouchableOpacity
            style={[styles.button, styles.primaryButton]}
            onPress={() => router.replace('/')}
          >
            <Text style={styles.primaryButtonText}>Return to Sign In</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Completed Catalog State
  if (uploadStep === 'completed' && catalogedItem) {
    return (
      <SafeAreaView style={styles.container}>
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <View style={styles.successCard}>
            <Text style={styles.successIcon}>✓</Text>
            <Text style={styles.successTitle}>Item Cataloged Successfully!</Text>
            <Text style={styles.successSubtitle}>
              Your clothing item has been verified and permanently added to your digital wardrobe.
            </Text>

            <View style={styles.catalogedDetails}>
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Name:</Text>
                <Text style={styles.detailValue}>{catalogedItem.name}</Text>
              </View>
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Category:</Text>
                <Text style={styles.detailValue}>{catalogedItem.category}</Text>
              </View>
              {catalogedItem.subcategory && (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Subcategory:</Text>
                  <Text style={styles.detailValue}>{catalogedItem.subcategory}</Text>
                </View>
              )}
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Primary Color:</Text>
                <Text style={styles.detailValue}>{catalogedItem.primaryColor}</Text>
              </View>
              {catalogedItem.secondaryColor && (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Secondary Color:</Text>
                  <Text style={styles.detailValue}>{catalogedItem.secondaryColor}</Text>
                </View>
              )}
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Formality:</Text>
                <Text style={styles.detailValue}>{catalogedItem.formalityLevel}</Text>
              </View>
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Season:</Text>
                <Text style={styles.detailValue}>{catalogedItem.season}</Text>
              </View>
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Pattern:</Text>
                <Text style={styles.detailValue}>{catalogedItem.pattern}</Text>
              </View>
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Status:</Text>
                <Text style={styles.detailValue}>{catalogedItem.status}</Text>
              </View>
            </View>

            <View style={styles.actionButtonGroup}>
              <TouchableOpacity
                style={[styles.button, styles.primaryButton]}
                onPress={resetForm}
              >
                <Text style={styles.primaryButtonText}>+ Add Another Item</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.button, styles.secondaryButton]}
                onPress={() => router.replace('/')}
              >
                <Text style={styles.secondaryButtonText}>Return to Home</Text>
              </TouchableOpacity>
            </View>
          </View>
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>Add Wardrobe Item</Text>
          <Text style={styles.subtitle}>
            Capture or choose a clothing photo, add details, and catalog it into your wardrobe.
          </Text>
        </View>

        {/* Error Banner */}
        {errorMessage && (
          <View style={styles.errorBanner}>
            <Text style={styles.errorBannerText}>{errorMessage}</Text>
            {errorCode === 'PERMISSION_PERMANENTLY_DENIED' && Platform.OS !== 'web' && (
              <TouchableOpacity
                style={styles.settingsLink}
                onPress={() => Linking.openSettings()}
              >
                <Text style={styles.settingsLinkText}>Open Device Settings</Text>
              </TouchableOpacity>
            )}
          </View>
        )}

        {/* Partial Failure Notice */}
        {uploadStep === 'partial_failure' && (
          <View style={styles.partialFailureBanner}>
            <Text style={styles.partialFailureTitle}>Upload Succeeded, Cataloging Incomplete</Text>
            <Text style={styles.partialFailureText}>
              Your photo bytes have been safely stored. Only the catalog confirmation step needs to be completed. You can adjust details below and tap "Retry Cataloging".
            </Text>
          </View>
        )}

        {/* Normalization Loading Indicator */}
        {isProcessing && (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#F59E0B" />
            <Text style={styles.loadingText}>Processing & normalizing image...</Text>
          </View>
        )}

        {/* Image Preview & Details Card */}
        {selectedImage && !isProcessing && (
          <View style={styles.card}>
            <Text style={styles.cardHeader}>Garment Photo</Text>
            <View style={styles.imageWrapper}>
              <Image
                source={{ uri: selectedImage.uri }}
                style={styles.previewImage}
                resizeMode="cover"
              />
            </View>

            {/* Metadata Badges */}
            <View style={styles.metadataContainer}>
              <View style={styles.metaRow}>
                <Text style={styles.metaLabel}>Dimensions:</Text>
                <Text style={styles.metaValue}>
                  {selectedImage.width} × {selectedImage.height} px
                </Text>
              </View>
              <View style={styles.metaRow}>
                <Text style={styles.metaLabel}>Size:</Text>
                <Text style={styles.metaValue}>
                  {(selectedImage.byteSize / 1024).toFixed(1)} KB
                </Text>
              </View>
            </View>

            {/* Replace / Remove Controls */}
            <View style={styles.actionRow}>
              <TouchableOpacity
                style={[styles.button, styles.secondaryButton, styles.flexButton]}
                onPress={() => handlePickImage('library')}
                disabled={isSubmitting}
              >
                <Text style={styles.secondaryButtonText}>Replace Photo</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.button, styles.dangerButton, styles.flexButton]}
                onPress={handleRemoveImage}
                disabled={isSubmitting}
              >
                <Text style={styles.dangerButtonText}>Remove</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {/* Empty State: Choose / Capture Actions */}
        {!selectedImage && !isProcessing && (
          <View style={styles.card}>
            <Text style={styles.cardHeader}>Select Clothing Photo</Text>
            <Text style={styles.cardBody}>
              Take a clear, well-lit photo of your garment or pick one from your camera roll. Photos must be at least 200 × 200 pixels.
            </Text>

            <TouchableOpacity
              style={[styles.button, styles.primaryButton]}
              onPress={() => handlePickImage('library')}
            >
              <Text style={styles.primaryButtonText}>Choose from Photo Library</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.button, styles.secondaryButton]}
              onPress={() => handlePickImage('camera')}
            >
              <Text style={styles.secondaryButtonText}>Capture with Camera</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Metadata Form (Active when image is selected) */}
        {selectedImage && !isProcessing && (
          <View style={styles.card}>
            <Text style={styles.cardHeader}>Garment Details</Text>

            {/* Item Name */}
            <View style={styles.fieldGroup}>
              <View style={styles.labelRow}>
                <Text style={styles.label}>Garment Name *</Text>
                <Text style={styles.charCount}>
                  {metadata.name.length}/100
                </Text>
              </View>
              <TextInput
                style={[
                  styles.input,
                  validationErrors.name && styles.inputError,
                ]}
                placeholder="e.g. Navy Oxford Button-Down"
                placeholderTextColor="#64748B"
                value={metadata.name}
                onChangeText={(text) => updateMetadata({ name: text })}
                maxLength={100}
                editable={!isSubmitting}
              />
              {validationErrors.name && (
                <Text style={styles.fieldError}>{validationErrors.name}</Text>
              )}
            </View>

            {/* Category */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Category *</Text>
              <View style={styles.chipsRow}>
                {VALID_CATEGORIES.map((cat) => {
                  const isSelected = metadata.category === cat;
                  return (
                    <TouchableOpacity
                      key={cat}
                      style={[
                        styles.chip,
                        isSelected && styles.chipSelected,
                      ]}
                      onPress={() => updateMetadata({ category: cat })}
                      disabled={isSubmitting}
                    >
                      <Text
                        style={[
                          styles.chipText,
                          isSelected && styles.chipTextSelected,
                        ]}
                      >
                        {cat}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
              {validationErrors.category && (
                <Text style={styles.fieldError}>{validationErrors.category}</Text>
              )}
            </View>

            {/* Subcategory */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Subcategory (Optional)</Text>
              <TextInput
                style={[
                  styles.input,
                  validationErrors.subcategory && styles.inputError,
                ]}
                placeholder="e.g. Button-Down, Chinos, Boots"
                placeholderTextColor="#64748B"
                value={metadata.subcategory}
                onChangeText={(text) => updateMetadata({ subcategory: text })}
                maxLength={50}
                editable={!isSubmitting}
              />
              {validationErrors.subcategory && (
                <Text style={styles.fieldError}>
                  {validationErrors.subcategory}
                </Text>
              )}
            </View>

            {/* Primary Color */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Primary Color *</Text>
              <View style={styles.chipsRow}>
                {VALID_COLORS.map((col) => {
                  const isSelected = metadata.primaryColor === col;
                  return (
                    <TouchableOpacity
                      key={col}
                      style={[
                        styles.chip,
                        isSelected && styles.chipSelected,
                      ]}
                      onPress={() => updateMetadata({ primaryColor: col })}
                      disabled={isSubmitting}
                    >
                      <Text
                        style={[
                          styles.chipText,
                          isSelected && styles.chipTextSelected,
                        ]}
                      >
                        {col}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
              {validationErrors.primaryColor && (
                <Text style={styles.fieldError}>
                  {validationErrors.primaryColor}
                </Text>
              )}
            </View>

            {/* Secondary Color */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Secondary Color (Optional)</Text>
              <View style={styles.chipsRow}>
                <TouchableOpacity
                  style={[
                    styles.chip,
                    metadata.secondaryColor === '' && styles.chipSelected,
                  ]}
                  onPress={() => updateMetadata({ secondaryColor: '' })}
                  disabled={isSubmitting}
                >
                  <Text
                    style={[
                      styles.chipText,
                      metadata.secondaryColor === '' && styles.chipTextSelected,
                    ]}
                  >
                    None
                  </Text>
                </TouchableOpacity>
                {VALID_COLORS.map((col) => {
                  const isSelected = metadata.secondaryColor === col;
                  return (
                    <TouchableOpacity
                      key={col}
                      style={[
                        styles.chip,
                        isSelected && styles.chipSelected,
                      ]}
                      onPress={() => updateMetadata({ secondaryColor: col })}
                      disabled={isSubmitting}
                    >
                      <Text
                        style={[
                          styles.chipText,
                          isSelected && styles.chipTextSelected,
                        ]}
                      >
                        {col}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
              {validationErrors.secondaryColor && (
                <Text style={styles.fieldError}>
                  {validationErrors.secondaryColor}
                </Text>
              )}
            </View>

            {/* Formality Level */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Formality Level</Text>
              <View style={styles.chipsRow}>
                {VALID_FORMALITIES.map((formality) => {
                  const isSelected = metadata.formalityLevel === formality;
                  return (
                    <TouchableOpacity
                      key={formality}
                      style={[
                        styles.chip,
                        isSelected && styles.chipSelected,
                      ]}
                      onPress={() => updateMetadata({ formalityLevel: formality })}
                      disabled={isSubmitting}
                    >
                      <Text
                        style={[
                          styles.chipText,
                          isSelected && styles.chipTextSelected,
                        ]}
                      >
                        {formality.replace('_', ' ')}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* Season */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Seasonality</Text>
              <View style={styles.chipsRow}>
                {VALID_SEASONS.map((season) => {
                  const isSelected = metadata.season === season;
                  return (
                    <TouchableOpacity
                      key={season}
                      style={[
                        styles.chip,
                        isSelected && styles.chipSelected,
                      ]}
                      onPress={() => updateMetadata({ season })}
                      disabled={isSubmitting}
                    >
                      <Text
                        style={[
                          styles.chipText,
                          isSelected && styles.chipTextSelected,
                        ]}
                      >
                        {season.replace('_', ' ')}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* Pattern */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Pattern</Text>
              <View style={styles.chipsRow}>
                {VALID_PATTERNS.map((pat) => {
                  const isSelected = metadata.pattern === pat;
                  return (
                    <TouchableOpacity
                      key={pat}
                      style={[
                        styles.chip,
                        isSelected && styles.chipSelected,
                      ]}
                      onPress={() => updateMetadata({ pattern: pat })}
                      disabled={isSubmitting}
                    >
                      <Text
                        style={[
                          styles.chipText,
                          isSelected && styles.chipTextSelected,
                        ]}
                      >
                        {pat}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* Submit Action */}
            <View style={styles.submitContainer}>
              <TouchableOpacity
                style={[
                  styles.button,
                  styles.primaryButton,
                  isSubmitting && styles.disabledButton,
                ]}
                onPress={handleSubmitCatalogItem}
                disabled={isSubmitting}
              >
                {isSubmitting ? (
                  <View style={styles.submittingRow}>
                    <ActivityIndicator size="small" color="#0F172A" />
                    <Text style={styles.primaryButtonText}>
                      {uploadStep === 'requesting_intent' && 'Authorizing Upload...'}
                      {uploadStep === 'uploading_binary' && 'Uploading Image Bytes...'}
                      {uploadStep === 'cataloging' && 'Cataloging Item...'}
                    </Text>
                  </View>
                ) : (
                  <Text style={styles.primaryButtonText}>
                    {uploadStep === 'partial_failure'
                      ? 'Retry Cataloging'
                      : 'Save to Digital Wardrobe'}
                  </Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        )}
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
    paddingBottom: 48,
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
  header: {
    marginBottom: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 6,
  },
  subtitle: {
    fontSize: 14,
    color: '#94A3B8',
    lineHeight: 20,
  },
  errorBanner: {
    backgroundColor: '#7F1D1D',
    borderColor: '#DC2626',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  errorBannerText: {
    color: '#FEE2E2',
    fontSize: 14,
    lineHeight: 18,
  },
  settingsLink: {
    marginTop: 8,
  },
  settingsLinkText: {
    color: '#FBBF24',
    fontSize: 14,
    fontWeight: '600',
    textDecorationLine: 'underline',
  },
  partialFailureBanner: {
    backgroundColor: '#78350F',
    borderColor: '#D97706',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  partialFailureTitle: {
    color: '#FEF3C7',
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 4,
  },
  partialFailureText: {
    color: '#FDE68A',
    fontSize: 13,
    lineHeight: 18,
  },
  loadingContainer: {
    padding: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingText: {
    marginTop: 10,
    color: '#F59E0B',
    fontSize: 14,
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#334155',
  },
  cardHeader: {
    fontSize: 16,
    fontWeight: '600',
    color: '#F8FAFC',
    marginBottom: 12,
  },
  cardBody: {
    fontSize: 14,
    color: '#94A3B8',
    lineHeight: 20,
    marginBottom: 16,
  },
  imageWrapper: {
    width: '100%',
    height: 240,
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: '#0F172A',
    marginBottom: 12,
  },
  previewImage: {
    width: '100%',
    height: '100%',
  },
  metadataContainer: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 12,
    marginBottom: 12,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 3,
  },
  metaLabel: {
    fontSize: 13,
    color: '#94A3B8',
  },
  metaValue: {
    fontSize: 13,
    color: '#E2E8F0',
    fontWeight: '500',
  },
  actionRow: {
    flexDirection: 'row',
    gap: 10,
  },
  flexButton: {
    flex: 1,
  },
  fieldGroup: {
    marginBottom: 16,
  },
  labelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#E2E8F0',
    marginBottom: 6,
  },
  charCount: {
    fontSize: 12,
    color: '#64748B',
  },
  input: {
    backgroundColor: '#0F172A',
    borderColor: '#334155',
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: '#F8FAFC',
    fontSize: 14,
  },
  inputError: {
    borderColor: '#EF4444',
  },
  fieldError: {
    color: '#EF4444',
    fontSize: 12,
    marginTop: 4,
  },
  chipsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    backgroundColor: '#0F172A',
    borderColor: '#334155',
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  chipSelected: {
    backgroundColor: '#F59E0B',
    borderColor: '#F59E0B',
  },
  chipText: {
    fontSize: 12,
    color: '#94A3B8',
    fontWeight: '500',
  },
  chipTextSelected: {
    color: '#0F172A',
    fontWeight: '700',
  },
  submitContainer: {
    marginTop: 8,
  },
  submittingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  button: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  primaryButton: {
    backgroundColor: '#F59E0B',
  },
  primaryButtonText: {
    color: '#0F172A',
    fontSize: 15,
    fontWeight: '600',
  },
  secondaryButton: {
    backgroundColor: '#334155',
  },
  secondaryButtonText: {
    color: '#F8FAFC',
    fontSize: 15,
    fontWeight: '500',
  },
  dangerButton: {
    backgroundColor: '#7F1D1D',
  },
  dangerButtonText: {
    color: '#FEE2E2',
    fontSize: 15,
    fontWeight: '500',
  },
  disabledButton: {
    opacity: 0.6,
  },
  successCard: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#10B981',
    marginTop: 20,
  },
  successIcon: {
    fontSize: 48,
    color: '#10B981',
    marginBottom: 8,
  },
  successTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 6,
    textAlign: 'center',
  },
  successSubtitle: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 20,
  },
  catalogedDetails: {
    width: '100%',
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 16,
    marginBottom: 20,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 4,
    borderBottomWidth: 1,
    borderBottomColor: '#1E293B',
  },
  detailLabel: {
    fontSize: 13,
    color: '#94A3B8',
  },
  detailValue: {
    fontSize: 13,
    color: '#E2E8F0',
    fontWeight: '600',
  },
  actionButtonGroup: {
    width: '100%',
    gap: 8,
  },
});
