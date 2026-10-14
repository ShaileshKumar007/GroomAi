import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import {
  apiGetMe,
  apiLogin,
  apiLogout,
  apiRegister,
  fetchHealth,
  setSessionExpiredListener,
} from '../api/client';
import { LoginPayload, RegisterPayload, UserSummary } from '../types/auth.types';
import { getStoredTokens } from '../utils/storage';

interface AuthContextValue {
  readonly user: UserSummary | null;
  readonly isAuthenticated: boolean;
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly healthStatus: 'UP' | 'DOWN' | 'CHECKING';
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  checkHealth: () => Promise<void>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [healthStatus, setHealthStatus] = useState<'UP' | 'DOWN' | 'CHECKING'>('CHECKING');

  const checkHealth = useCallback(async () => {
    try {
      setHealthStatus('CHECKING');
      const health = await fetchHealth();
      setHealthStatus(health.status === 'UP' ? 'UP' : 'DOWN');
    } catch {
      setHealthStatus('DOWN');
    }
  }, []);

  const restoreSession = useCallback(async () => {
    try {
      setIsLoading(true);
      const tokens = await getStoredTokens();
      if (tokens) {
        const userData = await apiGetMe();
        setUser(userData);
      }
    } catch {
      // Invalid/expired session cleared by client
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    checkHealth();
    restoreSession();

    // Register callback for automatic logout when refresh fails or session is revoked
    setSessionExpiredListener(() => {
      setUser(null);
      setError('Your session has expired. Please log in again.');
    });

    return () => {
      setSessionExpiredListener(null);
    };
  }, [checkHealth, restoreSession]);

  const login = async (payload: LoginPayload): Promise<void> => {
    try {
      setIsLoading(true);
      setError(null);
      const auth = await apiLogin(payload);
      setUser({
        id: auth.userId,
        email: auth.email,
        displayName: auth.displayName,
        createdAt: new Date().toISOString(),
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Login failed.';
      setError(message);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (payload: RegisterPayload): Promise<void> => {
    try {
      setIsLoading(true);
      setError(null);
      const auth = await apiRegister(payload);
      setUser({
        id: auth.userId,
        email: auth.email,
        displayName: auth.displayName,
        createdAt: new Date().toISOString(),
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Registration failed.';
      setError(message);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async (): Promise<void> => {
    try {
      setIsLoading(true);
      await apiLogout();
    } finally {
      setUser(null);
      setIsLoading(false);
    }
  };

  const refreshUser = async (): Promise<void> => {
    try {
      const userData = await apiGetMe();
      setUser(userData);
    } catch {
      setUser(null);
    }
  };

  const clearError = (): void => {
    setError(null);
  };

  const value: AuthContextValue = {
    user,
    isAuthenticated: !!user,
    isLoading,
    error,
    healthStatus,
    login,
    register,
    logout,
    refreshUser,
    checkHealth,
    clearError,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
