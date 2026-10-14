import React, { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useAuth } from '../src/context/AuthContext';
import {
  validateDisplayName,
  validateEmail,
  validatePassword,
} from '../src/utils/validation';

export default function HomeScreen(): React.JSX.Element {
  const {
    user,
    isAuthenticated,
    isLoading,
    error,
    healthStatus,
    login,
    register,
    logout,
    refreshUser,
    checkHealth,
    clearError,
  } = useAuth();

  const [mode, setMode] = useState<'LOGIN' | 'REGISTER'>('LOGIN');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{
    email?: string;
    password?: string;
    displayName?: string;
  }>({});
  const [submitting, setSubmitting] = useState(false);

  const switchMode = (newMode: 'LOGIN' | 'REGISTER') => {
    setMode(newMode);
    setFieldErrors({});
    clearError();
  };

  const handleAuthSubmit = async () => {
    clearError();
    const emailErr = validateEmail(email);
    const passErr = validatePassword(password);
    const nameErr = mode === 'REGISTER' ? validateDisplayName(displayName) : null;

    if (emailErr || passErr || nameErr) {
      setFieldErrors({
        email: emailErr || undefined,
        password: passErr || undefined,
        displayName: nameErr || undefined,
      });
      return;
    }

    setFieldErrors({});
    setSubmitting(true);

    try {
      if (mode === 'LOGIN') {
        await login({ email, password });
      } else {
        await register({
          email,
          password,
          displayName: displayName.trim() || undefined,
        });
      }
    } catch {
      // Error is caught and surfaced via AuthContext
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading && !user) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color="#F59E0B" />
          <Text style={styles.loadingText}>Restoring session...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.keyboardView}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {/* Header & Connectivity Status */}
          <View style={styles.header}>
            <Text style={styles.brandTitle}>Groom.ai</Text>
            <Text style={styles.brandSubtitle}>Personal AI Grooming Assistant</Text>

            <TouchableOpacity
              style={styles.healthPill}
              onPress={checkHealth}
              activeOpacity={0.7}
            >
              <View
                style={[
                  styles.statusDot,
                  healthStatus === 'UP'
                    ? styles.statusUp
                    : healthStatus === 'CHECKING'
                    ? styles.statusChecking
                    : styles.statusDown,
                ]}
              />
              <Text style={styles.healthText}>
                Backend: {healthStatus === 'UP' ? 'Connected' : healthStatus === 'CHECKING' ? 'Checking...' : 'Offline'} (tap to ping)
              </Text>
            </TouchableOpacity>
          </View>

          {/* Error Banner */}
          {error && (
            <View style={styles.errorBanner}>
              <Text style={styles.errorBannerText}>{error}</Text>
            </View>
          )}

          {/* Authenticated Dashboard View */}
          {isAuthenticated && user ? (
            <View style={styles.card}>
              <Text style={styles.cardHeader}>Active Session</Text>
              <Text style={styles.welcomeText}>
                Welcome back,{' '}
                <Text style={styles.userName}>{user.displayName || 'Groomer'}</Text>!
              </Text>

              <View style={styles.detailBox}>
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>User ID:</Text>
                  <Text style={styles.detailValue} numberOfLines={1} ellipsizeMode="middle">
                    {user.id}
                  </Text>
                </View>
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Email:</Text>
                  <Text style={styles.detailValue}>{user.email}</Text>
                </View>
                {user.displayName && (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Name:</Text>
                    <Text style={styles.detailValue}>{user.displayName}</Text>
                  </View>
                )}
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Member Since:</Text>
                  <Text style={styles.detailValue}>
                    {new Date(user.createdAt).toLocaleDateString()}
                  </Text>
                </View>
              </View>

              <View style={styles.actionButtonGroup}>
                <TouchableOpacity
                  style={[styles.button, styles.secondaryButton]}
                  onPress={refreshUser}
                  disabled={submitting}
                >
                  <Text style={styles.secondaryButtonText}>Verify /auth/me</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.button, styles.logoutButton]}
                  onPress={logout}
                  disabled={submitting}
                >
                  <Text style={styles.logoutButtonText}>Sign Out</Text>
                </TouchableOpacity>
              </View>
            </View>
          ) : (
            /* Unauthenticated Login / Register Form */
            <View style={styles.card}>
              <View style={styles.tabContainer}>
                <TouchableOpacity
                  style={[styles.tab, mode === 'LOGIN' && styles.activeTab]}
                  onPress={() => switchMode('LOGIN')}
                >
                  <Text
                    style={[styles.tabText, mode === 'LOGIN' && styles.activeTabText]}
                  >
                    Sign In
                  </Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.tab, mode === 'REGISTER' && styles.activeTab]}
                  onPress={() => switchMode('REGISTER')}
                >
                  <Text
                    style={[styles.tabText, mode === 'REGISTER' && styles.activeTabText]}
                  >
                    Create Account
                  </Text>
                </TouchableOpacity>
              </View>

              {mode === 'REGISTER' && (
                <View style={styles.inputGroup}>
                  <Text style={styles.inputLabel}>Display Name (Optional)</Text>
                  <TextInput
                    style={styles.input}
                    placeholder="e.g. Alex Sharma"
                    placeholderTextColor="#64748B"
                    value={displayName}
                    onChangeText={setDisplayName}
                    autoCapitalize="words"
                  />
                  {fieldErrors.displayName && (
                    <Text style={styles.fieldErrorText}>{fieldErrors.displayName}</Text>
                  )}
                </View>
              )}

              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Email Address</Text>
                <TextInput
                  style={styles.input}
                  placeholder="user@example.com"
                  placeholderTextColor="#64748B"
                  value={email}
                  onChangeText={setEmail}
                  autoCapitalize="none"
                  keyboardType="email-address"
                  autoComplete="email"
                />
                {fieldErrors.email && (
                  <Text style={styles.fieldErrorText}>{fieldErrors.email}</Text>
                )}
              </View>

              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Password</Text>
                <TextInput
                  style={styles.input}
                  placeholder="Min 8 chars (A-Z, a-z, 0-9, symbol)"
                  placeholderTextColor="#64748B"
                  value={password}
                  onChangeText={setPassword}
                  secureTextEntry
                  autoCapitalize="none"
                />
                {fieldErrors.password && (
                  <Text style={styles.fieldErrorText}>{fieldErrors.password}</Text>
                )}
              </View>

              <TouchableOpacity
                style={[styles.button, styles.primaryButton]}
                onPress={handleAuthSubmit}
                disabled={submitting}
              >
                {submitting ? (
                  <ActivityIndicator color="#0F172A" />
                ) : (
                  <Text style={styles.primaryButtonText}>
                    {mode === 'LOGIN' ? 'Sign In' : 'Create Account'}
                  </Text>
                )}
              </TouchableOpacity>
            </View>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  keyboardView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: 20,
    paddingTop: 30,
    paddingBottom: 40,
    justifyContent: 'center',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    color: '#94A3B8',
    fontSize: 14,
  },
  header: {
    alignItems: 'center',
    marginBottom: 24,
  },
  brandTitle: {
    fontSize: 32,
    fontWeight: '700',
    color: '#F8FAFC',
    letterSpacing: -0.5,
  },
  brandSubtitle: {
    fontSize: 14,
    color: '#94A3B8',
    marginTop: 4,
  },
  healthPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1E293B',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 20,
    marginTop: 14,
    borderWidth: 1,
    borderColor: '#334155',
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  statusUp: {
    backgroundColor: '#10B981',
  },
  statusChecking: {
    backgroundColor: '#F59E0B',
  },
  statusDown: {
    backgroundColor: '#EF4444',
  },
  healthText: {
    color: '#CBD5E1',
    fontSize: 12,
    fontWeight: '500',
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
    fontSize: 13,
    textAlign: 'center',
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 24,
    borderWidth: 1,
    borderColor: '#334155',
  },
  cardHeader: {
    fontSize: 18,
    fontWeight: '600',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  welcomeText: {
    fontSize: 15,
    color: '#94A3B8',
    marginBottom: 20,
  },
  userName: {
    color: '#F59E0B',
    fontWeight: '600',
  },
  detailBox: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 16,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#334155',
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 6,
  },
  detailLabel: {
    color: '#64748B',
    fontSize: 13,
    fontWeight: '500',
  },
  detailValue: {
    color: '#E2E8F0',
    fontSize: 13,
    fontWeight: '600',
    maxWidth: '65%',
  },
  actionButtonGroup: {
    gap: 12,
  },
  tabContainer: {
    flexDirection: 'row',
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 4,
    marginBottom: 20,
  },
  tab: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 6,
  },
  activeTab: {
    backgroundColor: '#334155',
  },
  tabText: {
    color: '#94A3B8',
    fontSize: 14,
    fontWeight: '600',
  },
  activeTabText: {
    color: '#F8FAFC',
  },
  inputGroup: {
    marginBottom: 16,
  },
  inputLabel: {
    color: '#CBD5E1',
    fontSize: 13,
    fontWeight: '500',
    marginBottom: 6,
  },
  input: {
    backgroundColor: '#0F172A',
    borderWidth: 1,
    borderColor: '#334155',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: '#F8FAFC',
    fontSize: 14,
  },
  fieldErrorText: {
    color: '#F87171',
    fontSize: 12,
    marginTop: 4,
  },
  button: {
    paddingVertical: 14,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
  },
  primaryButton: {
    backgroundColor: '#F59E0B',
  },
  primaryButtonText: {
    color: '#0F172A',
    fontSize: 15,
    fontWeight: '700',
  },
  secondaryButton: {
    backgroundColor: '#334155',
  },
  secondaryButtonText: {
    color: '#F8FAFC',
    fontSize: 14,
    fontWeight: '600',
  },
  logoutButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#EF4444',
  },
  logoutButtonText: {
    color: '#EF4444',
    fontSize: 14,
    fontWeight: '600',
  },
});
