import { InternalAxiosRequestConfig } from 'axios';

/**
 * Configuration options for the API client
 *
 * [api-client-config]
 * next-feature@0.1.1-beta.5
 * January 11th 2026, 9:00:22 pm
 */
export interface ApiClientConfig {
  baseURL?: string;
  timeout?: number;
  enableRefreshToken?: boolean;
  maxRetries?: number;
  retryDelay?: number;
  /**
   * Paths that should skip the refresh token logic on 401.
   * Useful for login/register endpoints that expect 401 as a valid response.
   */
  skipRefreshPaths?: RegExp[];
  onAuthenticated?: (
    config: InternalAxiosRequestConfig,
  ) => void | Promise<void>;
  onUnauthorized?: () => void | Promise<void>;
  onRefreshTokenExpired?: () => void | Promise<void>;
  onRefreshToken?: (
    originalRequest: InternalAxiosRequestConfig,
  ) => string | Promise<string>;
}
