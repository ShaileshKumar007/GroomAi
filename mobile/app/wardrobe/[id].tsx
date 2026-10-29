import React, { useEffect, useState, useCallback } from 'react';
import {
  ActivityIndicator,
  Modal,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import {
  apiGetWardrobeItemById,
  apiUpdateWardrobeItem,
  apiDeleteWardrobeItem,
} from '../../src/api/client';
import { AuthenticatedImage } from '../../src/components/AuthenticatedImage';
import {
  Category,
  Color,
  UpdateWardrobeItemPayload,
  WardrobeItemFormMetadata,
  WardrobeItemResponse,
} from '../../src/types/wardrobe.types';
import {
  VALID_CATEGORIES,
  VALID_COLORS,
  VALID_FORMALITIES,
  VALID_PATTERNS,
  VALID_SEASONS,
  validateWardrobeItemMetadata,
  WardrobeValidationErrors,
} from '../../src/utils/wardrobeValidation';
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

  // Edit State
  const [isEditing, setIsEditing] = useState<boolean>(false);
  const [editForm, setEditForm] = useState<WardrobeItemFormMetadata>({
    name: '',
    category: '',
    subcategory: '',
    primaryColor: '',
    secondaryColor: '',
    formalityLevel: 'CASUAL',
    season: 'ALL_SEASON',
    pattern: 'SOLID',
  });
  const [validationErrors, setValidationErrors] = useState<WardrobeValidationErrors>({});
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [editErrorMessage, setEditErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Delete State
  const [isDeleteModalVisible, setIsDeleteModalVisible] = useState<boolean>(false);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);
  const [deleteErrorMessage, setDeleteErrorMessage] = useState<string | null>(null);

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

  // Edit Handlers
  const handleStartEditing = () => {
    if (!item) return;
    setEditForm({
      name: item.name,
      category: item.category,
      subcategory: item.subcategory ?? '',
      primaryColor: item.primaryColor,
      secondaryColor: item.secondaryColor ?? '',
      formalityLevel: item.formalityLevel,
      season: item.season,
      pattern: item.pattern,
    });
    setValidationErrors({});
    setEditErrorMessage(null);
    setSuccessMessage(null);
    setIsEditing(true);
  };

  const handleCancelEditing = () => {
    setIsEditing(false);
    setValidationErrors({});
    setEditErrorMessage(null);
  };

  const updateEditField = (updates: Partial<WardrobeItemFormMetadata>) => {
    setEditForm((prev) => ({ ...prev, ...updates }));
    const updatedKey = Object.keys(updates)[0] as keyof WardrobeItemFormMetadata | undefined;
    if (updatedKey && validationErrors[updatedKey]) {
      setValidationErrors((prev) => {
        const next = { ...prev };
        delete next[updatedKey];
        return next;
      });
    }
  };

  const handleSaveEdit = async () => {
    if (!item || !id || isSaving) return;

    const validationResult = validateWardrobeItemMetadata(editForm);
    if (!validationResult.isValid) {
      setValidationErrors(validationResult.errors);
      return;
    }

    setIsSaving(true);
    setEditErrorMessage(null);

    try {
      const payload: UpdateWardrobeItemPayload = {
        name: editForm.name.trim(),
        category: editForm.category as Category,
        subcategory: editForm.subcategory.trim() ? editForm.subcategory.trim() : null,
        primaryColor: editForm.primaryColor as Color,
        secondaryColor: (editForm.secondaryColor as Color) || null,
        formalityLevel: editForm.formalityLevel,
        season: editForm.season,
        pattern: editForm.pattern,
      };

      const updated = await apiUpdateWardrobeItem(id, payload);
      setItem(updated);
      setIsEditing(false);
      setSuccessMessage('Garment details updated successfully.');
    } catch (err) {
      const formatted = formatWardrobeError(err);
      setEditErrorMessage(formatted.message);
    } finally {
      setIsSaving(false);
    }
  };

  // Delete Handlers
  const handleOpenDeleteModal = () => {
    setDeleteErrorMessage(null);
    setIsDeleteModalVisible(true);
  };

  const handleCloseDeleteModal = () => {
    if (isDeleting) return;
    setIsDeleteModalVisible(false);
    setDeleteErrorMessage(null);
  };

  const handleConfirmDelete = async () => {
    if (!id || isDeleting) return;

    setIsDeleting(true);
    setDeleteErrorMessage(null);

    try {
      await apiDeleteWardrobeItem(id);
      setIsDeleteModalVisible(false);
      router.replace('/wardrobe');
    } catch (err) {
      const formatted = formatWardrobeError(err);
      setDeleteErrorMessage(formatted.message);
    } finally {
      setIsDeleting(false);
    }
  };

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
        {/* Success Banner */}
        {successMessage && !isEditing && (
          <View style={styles.successBanner}>
            <Text style={styles.successBannerText}>✓ {successMessage}</Text>
          </View>
        )}

        {/* Garment Image */}
        <View style={styles.imageCard}>
          <AuthenticatedImage
            uri={item.downloadUrl}
            style={styles.detailImage}
            resizeMode="cover"
            accessibilityLabel={item.name}
          />
          {isEditing && (
            <View style={styles.editingBadge}>
              <Text style={styles.editingBadgeText}>Editing Details</Text>
            </View>
          )}
        </View>

        {/* MODE: EDITING */}
        {isEditing ? (
          <View style={styles.formCard}>
            <Text style={styles.formTitle}>Edit Garment Metadata</Text>

            {/* Error Banner */}
            {editErrorMessage && (
              <View style={styles.errorBanner}>
                <Text style={styles.errorBannerText}>{editErrorMessage}</Text>
              </View>
            )}

            {/* Garment Name */}
            <View style={styles.fieldGroup}>
              <View style={styles.labelRow}>
                <Text style={styles.label}>Garment Name *</Text>
                <Text style={styles.charCount}>{editForm.name.length}/100</Text>
              </View>
              <TextInput
                style={[styles.input, validationErrors.name && styles.inputError]}
                placeholder="e.g. Navy Oxford Button-Down"
                placeholderTextColor="#64748B"
                value={editForm.name}
                onChangeText={(text) => updateEditField({ name: text })}
                maxLength={100}
                editable={!isSaving}
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
                  const isSelected = editForm.category === cat;
                  return (
                    <TouchableOpacity
                      key={cat}
                      style={[styles.chip, isSelected && styles.chipSelected]}
                      onPress={() => updateEditField({ category: cat })}
                      disabled={isSaving}
                    >
                      <Text style={[styles.chipText, isSelected && styles.chipTextSelected]}>
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
                style={[styles.input, validationErrors.subcategory && styles.inputError]}
                placeholder="e.g. Button-Down, Chinos, Boots"
                placeholderTextColor="#64748B"
                value={editForm.subcategory}
                onChangeText={(text) => updateEditField({ subcategory: text })}
                maxLength={50}
                editable={!isSaving}
              />
              {validationErrors.subcategory && (
                <Text style={styles.fieldError}>{validationErrors.subcategory}</Text>
              )}
            </View>

            {/* Primary Color */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Primary Color *</Text>
              <View style={styles.chipsRow}>
                {VALID_COLORS.map((col) => {
                  const isSelected = editForm.primaryColor === col;
                  return (
                    <TouchableOpacity
                      key={col}
                      style={[styles.chip, isSelected && styles.chipSelected]}
                      onPress={() => updateEditField({ primaryColor: col })}
                      disabled={isSaving}
                    >
                      <Text style={[styles.chipText, isSelected && styles.chipTextSelected]}>
                        {col}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
              {validationErrors.primaryColor && (
                <Text style={styles.fieldError}>{validationErrors.primaryColor}</Text>
              )}
            </View>

            {/* Secondary Color */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Secondary Color (Optional)</Text>
              <View style={styles.chipsRow}>
                <TouchableOpacity
                  style={[styles.chip, editForm.secondaryColor === '' && styles.chipSelected]}
                  onPress={() => updateEditField({ secondaryColor: '' })}
                  disabled={isSaving}
                >
                  <Text
                    style={[
                      styles.chipText,
                      editForm.secondaryColor === '' && styles.chipTextSelected,
                    ]}
                  >
                    None
                  </Text>
                </TouchableOpacity>
                {VALID_COLORS.map((col) => {
                  const isSelected = editForm.secondaryColor === col;
                  return (
                    <TouchableOpacity
                      key={col}
                      style={[styles.chip, isSelected && styles.chipSelected]}
                      onPress={() => updateEditField({ secondaryColor: col })}
                      disabled={isSaving}
                    >
                      <Text style={[styles.chipText, isSelected && styles.chipTextSelected]}>
                        {col}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
              {validationErrors.secondaryColor && (
                <Text style={styles.fieldError}>{validationErrors.secondaryColor}</Text>
              )}
            </View>

            {/* Formality Level */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Formality Level</Text>
              <View style={styles.chipsRow}>
                {VALID_FORMALITIES.map((formality) => {
                  const isSelected = editForm.formalityLevel === formality;
                  return (
                    <TouchableOpacity
                      key={formality}
                      style={[styles.chip, isSelected && styles.chipSelected]}
                      onPress={() => updateEditField({ formalityLevel: formality })}
                      disabled={isSaving}
                    >
                      <Text style={[styles.chipText, isSelected && styles.chipTextSelected]}>
                        {formatEnumLabel(formality)}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* Season */}
            <View style={styles.fieldGroup}>
              <Text style={styles.label}>Season</Text>
              <View style={styles.chipsRow}>
                {VALID_SEASONS.map((season) => {
                  const isSelected = editForm.season === season;
                  return (
                    <TouchableOpacity
                      key={season}
                      style={[styles.chip, isSelected && styles.chipSelected]}
                      onPress={() => updateEditField({ season })}
                      disabled={isSaving}
                    >
                      <Text style={[styles.chipText, isSelected && styles.chipTextSelected]}>
                        {formatEnumLabel(season)}
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
                {VALID_PATTERNS.map((pattern) => {
                  const isSelected = editForm.pattern === pattern;
                  return (
                    <TouchableOpacity
                      key={pattern}
                      style={[styles.chip, isSelected && styles.chipSelected]}
                      onPress={() => updateEditField({ pattern })}
                      disabled={isSaving}
                    >
                      <Text style={[styles.chipText, isSelected && styles.chipTextSelected]}>
                        {formatEnumLabel(pattern)}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* Save / Cancel Action Buttons */}
            <View style={styles.editActionsRow}>
              <TouchableOpacity
                style={[styles.button, styles.primaryButton, styles.flexButton]}
                onPress={handleSaveEdit}
                disabled={isSaving}
              >
                {isSaving ? (
                  <ActivityIndicator size="small" color="#0F172A" />
                ) : (
                  <Text style={styles.primaryButtonText}>Save Changes</Text>
                )}
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.button, styles.secondaryButton, styles.flexButton]}
                onPress={handleCancelEditing}
                disabled={isSaving}
              >
                <Text style={styles.secondaryButtonText}>Cancel</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : (
          /* MODE: VIEWING */
          <>
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
                    <View style={[styles.colorDot, { backgroundColor: primaryColorHex }]} />
                    <Text style={styles.propertyValue}>
                      {formatEnumLabel(item.primaryColor)}
                    </Text>
                  </View>
                </View>

                <View style={styles.propertyItem}>
                  <Text style={styles.propertyLabel}>Secondary Color</Text>
                  {secondaryColorHex && item.secondaryColor ? (
                    <View style={styles.colorPill}>
                      <View style={[styles.colorDot, { backgroundColor: secondaryColorHex }]} />
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
                  <Text style={styles.propertyValue}>{formatEnumLabel(item.season)}</Text>
                </View>
              </View>

              <View style={styles.gridRow}>
                <View style={styles.propertyItem}>
                  <Text style={styles.propertyLabel}>Pattern</Text>
                  <Text style={styles.propertyValue}>{formatEnumLabel(item.pattern)}</Text>
                </View>

                <View style={styles.propertyItem}>
                  <Text style={styles.propertyLabel}>Cataloged On</Text>
                  <Text style={styles.propertyValue}>
                    {new Date(item.createdAt).toLocaleDateString()}
                  </Text>
                </View>
              </View>
            </View>

            {/* Item Management Actions (Edit & Delete) */}
            <View style={styles.managementCard}>
              <View style={styles.actionRow}>
                <TouchableOpacity
                  style={[styles.button, styles.editButton, styles.flexButton]}
                  onPress={handleStartEditing}
                >
                  <Text style={styles.editButtonText}>✏️ Edit Details</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.button, styles.dangerButton, styles.flexButton]}
                  onPress={handleOpenDeleteModal}
                >
                  <Text style={styles.dangerButtonText}>🗑️ Delete Garment</Text>
                </TouchableOpacity>
              </View>
            </View>

            {/* Back Navigation Button */}
            <TouchableOpacity
              style={[styles.button, styles.secondaryButton, styles.backButton]}
              onPress={() => router.back()}
            >
              <Text style={styles.secondaryButtonText}>← Return to Wardrobe</Text>
            </TouchableOpacity>
          </>
        )}

        {/* Delete Confirmation Modal */}
        <Modal
          visible={isDeleteModalVisible}
          transparent
          animationType="fade"
          onRequestClose={handleCloseDeleteModal}
        >
          <View style={styles.modalOverlay}>
            <View style={styles.modalCard}>
              <Text style={styles.modalTitle}>Delete Garment?</Text>
              <Text style={styles.modalBody}>
                Are you sure you want to delete &quot;{item.name}&quot;? This will permanently eradicate the image and remove it from your digital wardrobe. This action cannot be undone.
              </Text>

              {deleteErrorMessage && (
                <View style={styles.errorBanner}>
                  <Text style={styles.errorBannerText}>{deleteErrorMessage}</Text>
                </View>
              )}

              <View style={styles.modalActionsRow}>
                <TouchableOpacity
                  style={[styles.button, styles.dangerSolidButton, styles.flexButton]}
                  onPress={handleConfirmDelete}
                  disabled={isDeleting}
                >
                  {isDeleting ? (
                    <ActivityIndicator size="small" color="#FFFFFF" />
                  ) : (
                    <Text style={styles.dangerSolidButtonText}>Delete</Text>
                  )}
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.button, styles.secondaryButton, styles.flexButton]}
                  onPress={handleCloseDeleteModal}
                  disabled={isDeleting}
                >
                  <Text style={styles.secondaryButtonText}>Cancel</Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </Modal>
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
  successBanner: {
    backgroundColor: '#064E3B',
    borderColor: '#059669',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  successBannerText: {
    color: '#A7F3D0',
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
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
    color: '#FECACA',
    fontSize: 14,
    lineHeight: 20,
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
    position: 'relative',
  },
  detailImage: {
    width: '100%',
    height: '100%',
  },
  editingBadge: {
    position: 'absolute',
    top: 12,
    right: 12,
    backgroundColor: '#F59E0B',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 6,
  },
  editingBadgeText: {
    color: '#0F172A',
    fontSize: 12,
    fontWeight: '700',
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
    marginBottom: 16,
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
  managementCard: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
    marginBottom: 16,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
  },
  editActionsRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 12,
  },
  flexButton: {
    flex: 1,
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
  editButton: {
    backgroundColor: '#1E3A8A',
    borderWidth: 1,
    borderColor: '#3B82F6',
  },
  editButtonText: {
    color: '#93C5FD',
    fontSize: 14,
    fontWeight: '600',
  },
  dangerButton: {
    backgroundColor: '#450A0A',
    borderWidth: 1,
    borderColor: '#DC2626',
  },
  dangerButtonText: {
    color: '#FCA5A5',
    fontSize: 14,
    fontWeight: '600',
  },
  dangerSolidButton: {
    backgroundColor: '#DC2626',
  },
  dangerSolidButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  backButton: {
    width: '100%',
  },
  formCard: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
    marginBottom: 20,
  },
  formTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
    paddingBottom: 8,
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
    fontSize: 13,
    fontWeight: '600',
    color: '#E2E8F0',
    marginBottom: 6,
  },
  charCount: {
    fontSize: 11,
    color: '#64748B',
  },
  input: {
    backgroundColor: '#0F172A',
    borderWidth: 1,
    borderColor: '#334155',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: '#F8FAFC',
  },
  inputError: {
    borderColor: '#EF4444',
  },
  fieldError: {
    fontSize: 12,
    color: '#EF4444',
    marginTop: 4,
  },
  chipsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 16,
    backgroundColor: '#0F172A',
    borderWidth: 1,
    borderColor: '#334155',
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
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalCard: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 24,
    width: '100%',
    maxWidth: 400,
    borderWidth: 1,
    borderColor: '#334155',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 10,
  },
  modalBody: {
    fontSize: 14,
    color: '#94A3B8',
    lineHeight: 20,
    marginBottom: 20,
  },
  modalActionsRow: {
    flexDirection: 'row',
    gap: 12,
  },
});
