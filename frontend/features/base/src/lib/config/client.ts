import { ApiClient, type ApiResponse, ApiError } from '@next-feature/client';
import { BACKEND_API_URL } from './env';
const json = require('../../../package.json');

/**
 * Centralized API client configuration
 *
 * This file provides a single point to configure:
 * - Base API URL
 * - Request/response interceptors
 * - Default headers
 * - Authentication handling
 */
const apiClient = new ApiClient({
  baseURL: BACKEND_API_URL,
  enableRefreshToken: false,
  onUnauthorized: async () => {
    console.log('[base-client] Unauthorized');
  },
  onRefreshTokenExpired: async () => {
    console.log('[base-client] Refresh token expired');
  },
  onAuthenticated: async (config) => {
    console.log(
      '[base-client]',
      config.method.toUpperCase(),
      config.url,
      config.data ?? '',
    );
  },
  onRefreshToken: async (originalRequest) => {
    return '';
  },
  // timeout: 30000,
  // maxRetries: 3,
  // retryDelay: 1000
});

apiClient.axios.defaults.headers.common['User-Agent'] = [
  json.name,
  json.version,
].join(':');
apiClient.axios.defaults.headers.common['Content-Type'] = 'application/json';

// Capture the backend's X-Correlation-ID into sessionStorage so the browser
// logger (apps/web) can read it without a cross-package import.
// Guard prevents execution on the server (server actions share this module).
if (typeof window !== 'undefined') {
  apiClient.axios.interceptors.response.use((response) => {
    const correlationId = response.headers['x-correlation-id'];
    if (correlationId) {
      try {
        sessionStorage.setItem('correlation_id', correlationId);
      } catch {
        // ignore quota / private-browsing errors
      }
    }
    return response;
  });
}

/**
 * Example: Override request interceptor
 */
// apiClient.interceptors.request.use((config) => {
//   // Add custom headers, auth tokens, etc.
//   return config;
// });

/**
 * Example: Override response interceptor
 */
// apiClient.interceptors.response.use(
//   (response) => response,
//   (error) => {
//     // Handle errors globally
//     return Promise.reject(error);
//   }
// );

// Re-export commonly used utilities
export { ApiError, type ApiResponse };

// Export configured API client for use in server actions
export default apiClient;
