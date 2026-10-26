import { useState, useEffect, useCallback, useRef } from 'react';
import {
  apiCreateUploadIntent,
  apiUploadBinary,
  apiCreateWardrobeItem,
} from '../api/client';
import { cleanupNormalizedImage } from '../utils/imageNormalization';
import {
  pickAndNormalizeClothingImage,
  formatWardrobeError,
  executeWardrobeSubmissionWorkflow,
  WardrobeSubmissionError,
} from '../utils/wardrobeWorkflow';
import {
  validateWardrobeItemMetadata,
  WardrobeValidationErrors,
} from '../utils/wardrobeValidation';
import {
  NormalizedImage,
  UploadIntentResponse,
  WardrobeErrorType,
  WardrobeItemFormMetadata,
  WardrobeItemResponse,
  WardrobeUploadStep,
} from '../types/wardrobe.types';

export const INITIAL_METADATA: WardrobeItemFormMetadata = {
  name: '',
  category: '',
  subcategory: '',
  primaryColor: '',
  secondaryColor: '',
  pattern: 'SOLID',
  formalityLevel: 'CASUAL',
  season: 'ALL_SEASON',
};

export interface UseWardrobeEntryResult {
  readonly selectedImage: NormalizedImage | null;
  readonly isProcessing: boolean;
  readonly metadata: WardrobeItemFormMetadata;
  readonly validationErrors: WardrobeValidationErrors;
  readonly uploadIntent: UploadIntentResponse | null;
  readonly uploadStep: WardrobeUploadStep;
  readonly binaryUploaded: boolean;
  readonly catalogedItem: WardrobeItemResponse | null;
  readonly errorMessage: string | null;
  readonly errorCode: WardrobeErrorType | string | null;
  handlePickImage: (source: 'camera' | 'library') => Promise<void>;
  handleRemoveImage: () => Promise<void>;
  updateMetadata: (fields: Partial<WardrobeItemFormMetadata>) => void;
  handleSubmitCatalogItem: () => Promise<void>;
  resetForm: () => Promise<void>;
  clearError: () => void;
}

export function useWardrobeEntry(): UseWardrobeEntryResult {
  const [selectedImage, setSelectedImage] = useState<NormalizedImage | null>(null);
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [metadata, setMetadata] = useState<WardrobeItemFormMetadata>(INITIAL_METADATA);
  const [validationErrors, setValidationErrors] = useState<WardrobeValidationErrors>({});
  const [uploadIntent, setUploadIntent] = useState<UploadIntentResponse | null>(null);
  const [uploadStep, setUploadStep] = useState<WardrobeUploadStep>('idle');
  const [binaryUploaded, setBinaryUploaded] = useState<boolean>(false);
  const [catalogedItem, setCatalogedItem] = useState<WardrobeItemResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorCode, setErrorCode] = useState<WardrobeErrorType | string | null>(null);

  // Active in-flight ref to prevent unmount race conditions or double submissions
  const isSubmittingRef = useRef<boolean>(false);
  const selectedImageRef = useRef<NormalizedImage | null>(null);
  selectedImageRef.current = selectedImage;

  // Clean up cache file when unmounting (unless an upload is actively writing it)
  useEffect(() => {
    return () => {
      if (selectedImageRef.current?.uri && !isSubmittingRef.current) {
        cleanupNormalizedImage(selectedImageRef.current.uri);
      }
    };
  }, []);

  const clearError = useCallback(() => {
    setErrorMessage(null);
    setErrorCode(null);
  }, []);

  const updateMetadata = useCallback((fields: Partial<WardrobeItemFormMetadata>) => {
    setMetadata((prev) => ({ ...prev, ...fields }));
    // Clear validation error for updated fields
    setValidationErrors((prev) => {
      const updated = { ...prev };
      for (const key of Object.keys(fields) as (keyof WardrobeItemFormMetadata)[]) {
        delete updated[key];
      }
      return updated;
    });
  }, []);

  const handlePickImage = useCallback(
    async (source: 'camera' | 'library') => {
      if (
        uploadStep === 'requesting_intent' ||
        uploadStep === 'uploading_binary' ||
        uploadStep === 'cataloging'
      ) {
        return; // Disallow replacing image while submission is in flight
      }

      clearError();
      setIsProcessing(true);

      try {
        const normalized = await pickAndNormalizeClothingImage(source, selectedImage?.uri);
        if (normalized) {
          // Setting a new image completely invalidates previous upload intents and binary uploads
          setSelectedImage(normalized);
          setUploadIntent(null);
          setBinaryUploaded(false);
          setCatalogedItem(null);
          setUploadStep('idle');
        }
      } catch (err) {
        const formatted = formatWardrobeError(err);
        setErrorCode(formatted.type);
        setErrorMessage(formatted.message);
      } finally {
        setIsProcessing(false);
      }
    },
    [uploadStep, selectedImage, clearError]
  );

  const handleRemoveImage = useCallback(async () => {
    if (
      uploadStep === 'requesting_intent' ||
      uploadStep === 'uploading_binary' ||
      uploadStep === 'cataloging'
    ) {
      return; // Disallow removing image during in-flight upload
    }

    if (selectedImage?.uri) {
      await cleanupNormalizedImage(selectedImage.uri);
    }
    setSelectedImage(null);
    setUploadIntent(null);
    setBinaryUploaded(false);
    setCatalogedItem(null);
    setUploadStep('idle');
    clearError();
  }, [uploadStep, selectedImage, clearError]);

  const resetForm = useCallback(async () => {
    if (selectedImage?.uri) {
      await cleanupNormalizedImage(selectedImage.uri);
    }
    setSelectedImage(null);
    setMetadata(INITIAL_METADATA);
    setValidationErrors({});
    setUploadIntent(null);
    setBinaryUploaded(false);
    setCatalogedItem(null);
    setUploadStep('idle');
    clearError();
  }, [selectedImage, clearError]);

  const handleSubmitCatalogItem = useCallback(async () => {
    // Prevent duplicate taps and concurrent submissions
    if (
      isSubmittingRef.current ||
      uploadStep === 'requesting_intent' ||
      uploadStep === 'uploading_binary' ||
      uploadStep === 'cataloging' ||
      uploadStep === 'completed'
    ) {
      return;
    }

    if (!selectedImage) {
      setErrorCode('VALIDATION_ERROR');
      setErrorMessage('Please select or capture a clothing photo first.');
      return;
    }

    // Validate form metadata
    const validationResult = validateWardrobeItemMetadata(metadata);
    if (!validationResult.isValid) {
      setValidationErrors(validationResult.errors);
      setErrorCode('VALIDATION_ERROR');
      setErrorMessage('Please fix the validation errors before saving.');
      return;
    }

    isSubmittingRef.current = true;
    clearError();

    try {
      const result = await executeWardrobeSubmissionWorkflow({
        selectedImage,
        metadata,
        existingUploadIntent: uploadIntent,
        binaryAlreadyUploaded: binaryUploaded,
        createUploadIntentFn: apiCreateUploadIntent,
        uploadBinaryFn: apiUploadBinary,
        createWardrobeItemFn: apiCreateWardrobeItem,
        cleanupFn: cleanupNormalizedImage,
        onStepChange: (step) => setUploadStep(step),
      });

      setUploadIntent(result.uploadIntent);
      setBinaryUploaded(true);
      setCatalogedItem(result.catalogedItem);
      setUploadStep('completed');
    } catch (err) {
      if (err instanceof WardrobeSubmissionError) {
        if (err.uploadIntent) {
          setUploadIntent(err.uploadIntent);
        }
        if (err.binaryUploaded) {
          setBinaryUploaded(true);
          setUploadStep('partial_failure');
        } else {
          setUploadStep('idle');
        }
        setErrorCode(err.code);
        setErrorMessage(err.message);
      } else {
        const formatted = formatWardrobeError(err);
        setErrorCode(formatted.type);
        setErrorMessage(formatted.message);
        setUploadStep(binaryUploaded ? 'partial_failure' : 'idle');
      }
    } finally {
      isSubmittingRef.current = false;
    }
  }, [
    uploadStep,
    selectedImage,
    metadata,
    uploadIntent,
    binaryUploaded,
    clearError,
  ]);

  return {
    selectedImage,
    isProcessing,
    metadata,
    validationErrors,
    uploadIntent,
    uploadStep,
    binaryUploaded,
    catalogedItem,
    errorMessage,
    errorCode,
    handlePickImage,
    handleRemoveImage,
    updateMetadata,
    handleSubmitCatalogItem,
    resetForm,
    clearError,
  };
}
