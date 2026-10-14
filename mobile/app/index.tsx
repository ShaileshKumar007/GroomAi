import React from 'react';
import { StyleSheet, Text, View, SafeAreaView } from 'react-native';

export default function LaunchScreen(): React.JSX.Element {
  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.badge}>Phase 0 Scaffolding</Text>
          <Text style={styles.title}>Groom.ai</Text>
          <Text style={styles.subtitle}>
            Your AI-Powered Personal Grooming Assistant
          </Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>System Baseline</Text>
          <View style={styles.row}>
            <Text style={styles.label}>Backend Architecture:</Text>
            <Text style={styles.value}>Spring Boot 3 Modular Monolith</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>Mobile Stack:</Text>
            <Text style={styles.value}>React Native + Expo Router (TS)</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>Primary Database:</Text>
            <Text style={styles.value}>PostgreSQL 16 + Flyway</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>API Base:</Text>
            <Text style={styles.value}>/api/v1 (REST / JSON)</Text>
          </View>
        </View>

        <View style={styles.footer}>
          <Text style={styles.footerText}>Ready for Phase 1 Authentication</Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  container: {
    flex: 1,
    paddingHorizontal: 24,
    justifyContent: 'space-between',
    paddingVertical: 32,
  },
  header: {
    alignItems: 'center',
    marginTop: 40,
  },
  badge: {
    color: '#38BDF8',
    backgroundColor: 'rgba(56, 189, 248, 0.1)',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 16,
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 16,
    overflow: 'hidden',
  },
  title: {
    fontSize: 36,
    fontWeight: '700',
    color: '#F8FAFC',
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 16,
    color: '#94A3B8',
    textAlign: 'center',
    marginTop: 8,
    lineHeight: 24,
    maxWidth: 280,
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    borderColor: '#334155',
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#F8FAFC',
    marginBottom: 16,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  label: {
    fontSize: 14,
    color: '#94A3B8',
  },
  value: {
    fontSize: 14,
    color: '#E2E8F0',
    fontWeight: '500',
  },
  footer: {
    alignItems: 'center',
    marginBottom: 16,
  },
  footerText: {
    fontSize: 14,
    color: '#64748B',
  },
});
