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
    console.log('[auth-client] Unauthorized');
  },
  onRefreshTokenExpired: async () => {
    console.log('[auth-client] Refresh token expired');
  },
  onAuthenticated: async (config) => {
    console.log(
      '[auth-client]',
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
