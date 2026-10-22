import 'server-only';

import { ApiClient, type ApiResponse, ApiError } from '@next-feature/client';
import { ONBOARDING_API_URL } from './env';
// import { logger } from '@next-feature/logging/server';

// const log = logger.child({ module: 'onboarding-client' });

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
  baseURL: ONBOARDING_API_URL,
  onAuthenticated: async (config) => {
    // log.info(`${config.method.toUpperCase()} ${config.url} ${config.data ?? ""}`)
  },
});

const { name, version } = require('../../../package.json');

apiClient.axios.defaults.headers.common['User-Agent'] = `${name}:${version}`;
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
// Re-export commonly used utilities
export { ApiError, type ApiResponse };

// Export configured API client for use in server actions
export default apiClient;
