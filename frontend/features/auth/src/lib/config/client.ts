import { ApiClient, type ApiResponse, ApiError } from '@next-feature/client';
import { AUTH_API_URL } from './env';
// import { logger } from '@next-feature/logging/server';

// const log = logger.child({ module: 'auth-client' });

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
  baseURL: AUTH_API_URL,
  onAuthenticated: async (config) => {
    // log.info(`${config.method.toUpperCase()} ${config.url} ${config.data ?? ""}`)
  },
});

const { name, version } = require('../../../package.json');

apiClient.axios.defaults.headers.common['User-Agent'] = `${name}:${version}`;
apiClient.axios.defaults.headers.common['Content-Type'] = 'application/json';

// Re-export commonly used utilities
export { ApiError, type ApiResponse };

// Export configured API client for use in server actions
export default apiClient;
