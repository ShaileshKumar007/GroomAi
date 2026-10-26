import { useState, useEffect, useCallback, useRef } from 'react';
import { apiGetWardrobeItems } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { formatWardrobeError } from '../utils/wardrobeWorkflow';
import {
  Category,
  WardrobeItemResponse,
  WardrobeListParams,
} from '../types/wardrobe.types';

export interface UseWardrobeCatalogResult {
  readonly items: readonly WardrobeItemResponse[];
  readonly isLoading: boolean;
  readonly isRefreshing: boolean;
  readonly errorMessage: string | null;
  readonly selectedCategory: Category | 'ALL';
  readonly totalElements: number;
  selectCategory: (category: Category | 'ALL') => void;
  refreshCatalog: () => Promise<void>;
  retryLoading: () => Promise<void>;
  clearError: () => void;
}

export function useWardrobeCatalog(): UseWardrobeCatalogResult {
  const { isAuthenticated, user } = useAuth();
  const [items, setItems] = useState<readonly WardrobeItemResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<Category | 'ALL'>('ALL');
  const [totalElements, setTotalElements] = useState<number>(0);

  const activeUserIdRef = useRef<string | null>(null);
  const isFetchingRef = useRef<boolean>(false);

  // Clear state if session is revoked or user logs out
  useEffect(() => {
    if (!isAuthenticated || !user) {
      setItems([]);
      setTotalElements(0);
      setErrorMessage(null);
      setIsLoading(false);
      activeUserIdRef.current = null;
    } else if (activeUserIdRef.current !== user.id) {
      // Switched users: clear previous user's catalog immediately
      setItems([]);
      setTotalElements(0);
      setErrorMessage(null);
      activeUserIdRef.current = user.id;
    }
  }, [isAuthenticated, user]);

  const fetchItems = useCallback(
    async (categoryFilter: Category | 'ALL', isPullToRefresh = false) => {
      if (!isAuthenticated || !user) {
        setIsLoading(false);
        setIsRefreshing(false);
        return;
      }

      if (isFetchingRef.current) {
        return; // Prevent duplicate concurrent requests
      }

      isFetchingRef.current = true;
      if (isPullToRefresh) {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }
      setErrorMessage(null);

      try {
        const params: WardrobeListParams = {
          page: 0,
          size: 50,
          status: 'ACTIVE',
          ...(categoryFilter !== 'ALL' ? { category: categoryFilter } : {}),
        };

        const response = await apiGetWardrobeItems(params);
        setItems(response.content);
        setTotalElements(response.totalElements);
      } catch (err) {
        const formatted = formatWardrobeError(err);
        setErrorMessage(formatted.message);
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
        isFetchingRef.current = false;
      }
    },
    [isAuthenticated, user]
  );

  // Initial load or category change
  useEffect(() => {
    if (isAuthenticated && user) {
      void fetchItems(selectedCategory, false);
    }
  }, [isAuthenticated, user, selectedCategory, fetchItems]);

  const selectCategory = useCallback((category: Category | 'ALL') => {
    setSelectedCategory(category);
  }, []);

  const refreshCatalog = useCallback(async () => {
    await fetchItems(selectedCategory, true);
  }, [selectedCategory, fetchItems]);

  const retryLoading = useCallback(async () => {
    await fetchItems(selectedCategory, false);
  }, [selectedCategory, fetchItems]);

  const clearError = useCallback(() => {
    setErrorMessage(null);
  }, []);

  return {
    items,
    isLoading,
    isRefreshing,
    errorMessage,
    selectedCategory,
    totalElements,
    selectCategory,
    refreshCatalog,
    retryLoading,
    clearError,
  };
}
