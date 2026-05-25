import axios, {
  AxiosError,
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios';
import type { ApiClientConfig } from './types/client';
import handleApiError from './utils/error';
import { BACKEND_API_URL } from './config/env';

/**
 * Pending request queue item
 */
interface PendingRequest {
  resolve: (token: string) => void;
  reject: (error: any) => void;
}

/**
 * Axios wrapper with JWT/Refresh token handling
 */
export class ApiClient {
  private readonly instance: AxiosInstance;
  private isRefreshing = false;
  private pendingRequests: PendingRequest[] = [];
  private config: ApiClientConfig;

  constructor(config: ApiClientConfig) {
    this.config = {
      baseURL: BACKEND_API_URL,
      timeout: 30000,
      enableRefreshToken: false,
      maxRetries: 1,
      retryDelay: 1000,
      skipRefreshPaths: [],
      onUnauthorized: async () => {},
      onRefreshTokenExpired: async () => {},
      onAuthenticated: async (config) => {},
      onRefreshToken: async (originalRequest) => {
        return '';
      },
      ...config,
    };

    this.instance = axios.create({
      baseURL: this.config.baseURL,
      timeout: this.config.timeout,
    });

    this.setupInterceptors();
  }

  /**
   * Setup request and response interceptors
   */
  private setupInterceptors(): void {
    // Request interceptor
    this.instance.interceptors.request.use(
      this.handleRequestFulfilled.bind(this),
      this.handleRequestRejected.bind(this),
    );

    // Response interceptor
    this.instance.interceptors.response.use(
      this.handleResponseFulfilled.bind(this),
      this.handleResponseRejected.bind(this),
    );
  }

  /**
   * Attach JWT token to request headers
   */
  private async handleRequestFulfilled(
    config: InternalAxiosRequestConfig,
  ): Promise<InternalAxiosRequestConfig> {
    try {
      await this.config.onAuthenticated(config);

      return config;
    } catch (error) {
      console.error('onAuthenticated error on request:', error);
      return config;
    }
  }

  /**
   * Handle request errors
   */
  private handleRequestRejected(error: any): Promise<never> {
    console.error('Request configuration error:', error);
    return Promise.reject(error);
  }

  /**
   * Pass through successful responses
   */
  private handleResponseFulfilled(response: AxiosResponse): AxiosResponse {
    return response;
  }

  /**
   * Check if a path should skip the refresh token logic
   */
  private shouldSkipRefresh(url: string | undefined): boolean {
    if (!url || !this.config.skipRefreshPaths?.length) {
      return false;
    }
    return this.config.skipRefreshPaths.some((pattern) => pattern.test(url));
  }

  /**
   * Handle response errors with retry logic and token refresh
   */
  private async handleResponseRejected(error: AxiosError): Promise<any> {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
      _retryCount?: number;
    };

    if (!originalRequest) {
      return Promise.reject(handleApiError(error));
    }

    // Handle 401 Unauthorized - attempt token refresh (skip for auth endpoints)
    if (
      error.response?.status === 401 &&
      this.config.enableRefreshToken &&
      !this.shouldSkipRefresh(originalRequest.url)
    ) {
      return this.handleUnauthorizedError(error, originalRequest);
    }

    // Handle network errors and 5xx errors with retry logic
    if (this.shouldRetry(error, originalRequest)) {
      return this.retryRequest(originalRequest);
    }

    // Create and reject with custom ApiError
    return Promise.reject(handleApiError(error));
  }

  /**
   * Handle 401 errors with token refresh
   */
  private async handleUnauthorizedError(
    error: AxiosError,
    originalRequest: InternalAxiosRequestConfig & { _retry?: boolean },
  ): Promise<any> {
    // Prevent infinite loops
    if (originalRequest._retry) {
      await this.config.onRefreshTokenExpired();
      return Promise.reject(handleApiError(error));
    }

    originalRequest._retry = true;

    // If already refreshing, queue the request
    if (this.isRefreshing) {
      return new Promise((resolve, reject) => {
        this.pendingRequests.push({ resolve, reject });
      })
        .then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return this.instance(originalRequest);
        })
        .catch((err) => {
          return Promise.reject(handleApiError(err));
        });
    }

    this.isRefreshing = true;

    try {
      const newToken = await this.refreshToken(originalRequest);

      // Update the original request with new token
      originalRequest.headers.Authorization = `Bearer ${newToken}`;

      // Resolve all pending requests with new token
      this.processPendingRequests(null, newToken);

      // Retry the original request
      return this.instance(originalRequest);
    } catch (refreshError) {
      // Reject all pending requests
      this.processPendingRequests(refreshError, null);

      if (this.config.onUnauthorized) {
        await this.config.onUnauthorized();
      }

      return Promise.reject(handleApiError(error));
    } finally {
      this.isRefreshing = false;
    }
  }

  /**
   * Refresh the JWT token using the refresh token
   */
  private async refreshToken(
    originalRequest: InternalAxiosRequestConfig<any> & { _retry?: boolean },
  ): Promise<string> {
    try {
      return this.config.onRefreshToken(originalRequest);
    } catch (error) {
      console.error('Token refresh failed:', error);
      throw error;
    }
  }

  /**
   * Process all pending requests after token refresh
   */
  private processPendingRequests(error: any, token: string | null): void {
    this.pendingRequests.forEach((request) => {
      if (error) {
        request.reject(error);
      } else if (token) {
        request.resolve(token);
      }
    });

    this.pendingRequests = [];
  }

  /**
   * Determine if request should be retried
   */
  private shouldRetry(
    error: AxiosError,
    config: InternalAxiosRequestConfig & { _retryCount?: number },
  ): boolean {
    const retryCount = config._retryCount || 0;

    // Don't retry if max retries exceeded
    if (retryCount >= this.config.maxRetries) {
      return false;
    }

    // Retry on network errors
    if (!error.response) {
      return true;
    }

    // Retry on 5xx server errors (except 501)
    const status = error.response.status;
    if (status >= 500 && status !== 501) {
      return true;
    }

    // Retry on 429 (Too Many Requests)
    if (status === 429) {
      return true;
    }

    return false;
  }

  /**
   * Retry failed request with exponential backoff
   */
  private async retryRequest(
    config: InternalAxiosRequestConfig & { _retryCount?: number },
  ): Promise<any> {
    config._retryCount = (config._retryCount || 0) + 1;

    const delay = this.config.retryDelay * Math.pow(2, config._retryCount - 1);

    await this.sleep(delay);

    console.log(
      `Retrying request (attempt ${config._retryCount}):`,
      config.url,
    );

    return this.instance(config);
  }

  /**
   * Sleep helper for retry delays
   */
  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /**
   * HTTP Methods with proper typing
   */

  async get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.instance.get<T>(url, config);
    return response.data;
  }

  async post<T = any>(
    url: string,
    data?: any,
    config?: AxiosRequestConfig,
  ): Promise<T> {
    const response = await this.instance.post<T>(url, data, config);
    return response.data;
  }

  async put<T = any>(
    url: string,
    data?: any,
    config?: AxiosRequestConfig,
  ): Promise<T> {
    const response = await this.instance.put<T>(url, data, config);
    return response.data;
  }

  async patch<T = any>(
    url: string,
    data?: any,
    config?: AxiosRequestConfig,
  ): Promise<T> {
    const response = await this.instance.patch<T>(url, data, config);
    return response.data;
  }

  async delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.instance.delete<T>(url, config);
    return response.data;
  }

  /**
   * Get the underlying Axios instance for advanced usage
   */
  get axios(): AxiosInstance {
    return this.instance;
  }
}
