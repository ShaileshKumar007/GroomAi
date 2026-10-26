import React from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { AuthProvider } from '../src/context/AuthContext';

export default function RootLayout(): React.JSX.Element {
  return (
    <AuthProvider>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          headerStyle: {
            backgroundColor: '#0F172A',
          },
          headerTintColor: '#F8FAFC',
          headerTitleStyle: {
            fontWeight: '600',
          },
          contentStyle: {
            backgroundColor: '#0F172A',
          },
        }}
      >
        <Stack.Screen
          name="index"
          options={{
            title: 'Groom.ai',
            headerShown: false,
          }}
        />
        <Stack.Screen
          name="wardrobe/index"
          options={{
            title: 'Digital Wardrobe',
            headerShown: true,
          }}
        />
        <Stack.Screen
          name="wardrobe/add"
          options={{
            title: 'Add Clothing Item',
            headerShown: true,
          }}
        />
        <Stack.Screen
          name="wardrobe/[id]"
          options={{
            title: 'Garment Details',
            headerShown: true,
          }}
        />
      </Stack>
    </AuthProvider>
  );
}
