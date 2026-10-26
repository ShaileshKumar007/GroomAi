import React, { useState, useEffect } from 'react';
import {
  ActivityIndicator,
  Image,
  ImageResizeMode,
  ImageStyle,
  StyleProp,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { getStoredTokens } from '../utils/storage';

export interface AuthenticatedImageProps {
  readonly uri: string;
  readonly style?: StyleProp<ImageStyle>;
  readonly resizeMode?: ImageResizeMode;
  readonly accessibilityLabel?: string;
  readonly testID?: string;
}

/**
 * Image component that handles authenticated streaming for local storage endpoints
 * (injecting Authorization Bearer token into Image headers) while safely omitting
 * authorization credentials when loading from external S3 pre-signed URLs.
 * Displays a placeholder during loading to prevent layout shifts, and a fallback on error.
 */
export function AuthenticatedImage({
  uri,
  style,
  resizeMode = 'cover',
  accessibilityLabel,
  testID,
}: AuthenticatedImageProps): React.JSX.Element {
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [hasError, setHasError] = useState<boolean>(false);
  const [resolvedSource, setResolvedSource] = useState<{
    uri: string;
    headers?: Record<string, string>;
  } | null>(null);

  useEffect(() => {
    let isMounted = true;
    setIsLoading(true);
    setHasError(false);

    if (!uri || uri.trim().length === 0) {
      setHasError(true);
      setIsLoading(false);
      return;
    }

    async function prepareImageSource() {
      // Determine if the URL targets the local backend storage endpoint
      const isLocalStorage =
        uri.includes('/storage/files/') || uri.includes('/api/v1/storage/');

      if (isLocalStorage) {
        try {
          const tokens = await getStoredTokens();
          if (isMounted) {
            if (tokens?.accessToken) {
              setResolvedSource({
                uri,
                headers: {
                  Authorization: `Bearer ${tokens.accessToken}`,
                },
              });
            } else {
              setResolvedSource({ uri });
            }
          }
        } catch {
          if (isMounted) {
            setResolvedSource({ uri });
          }
        }
      } else {
        // External cloud storage (e.g. S3 Pre-signed): never pass Bearer tokens
        if (isMounted) {
          setResolvedSource({ uri });
        }
      }
    }

    void prepareImageSource();

    return () => {
      isMounted = false;
    };
  }, [uri]);

  if (hasError || !resolvedSource) {
    return (
      <View style={[styles.fallbackContainer, style]} testID={`${testID ?? 'auth-image'}-fallback`}>
        <Text style={styles.fallbackIcon}>👔</Text>
        <Text style={styles.fallbackText}>Image unavailable</Text>
      </View>
    );
  }

  return (
    <View style={[styles.container, style]}>
      <Image
        source={resolvedSource}
        style={[styles.image, style]}
        resizeMode={resizeMode}
        accessibilityLabel={accessibilityLabel}
        testID={testID ?? 'auth-image'}
        onLoadStart={() => setIsLoading(true)}
        onLoadEnd={() => setIsLoading(false)}
        onError={() => {
          setIsLoading(false);
          setHasError(true);
        }}
      />
      {isLoading && (
        <View style={styles.loadingOverlay} testID={`${testID ?? 'auth-image'}-loading`}>
          <ActivityIndicator size="small" color="#F59E0B" />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    overflow: 'hidden',
    backgroundColor: '#0F172A',
    position: 'relative',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(15, 23, 42, 0.7)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  fallbackContainer: {
    backgroundColor: '#1E293B',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  fallbackIcon: {
    fontSize: 24,
    marginBottom: 4,
  },
  fallbackText: {
    color: '#94A3B8',
    fontSize: 11,
    textAlign: 'center',
  },
});
