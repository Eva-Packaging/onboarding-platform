import { ApiError } from '../error';
import type { ApiResponse } from '../types';

interface WithApiOptions<T> {
  fallbackData?: T;
  successMessage?: string;
}

export function withApi<F extends (...args: any[]) => Promise<any>>(
  fn: F,
  options?: WithApiOptions<Awaited<ReturnType<F>>>,
): (...args: Parameters<F>) => Promise<ApiResponse<Awaited<ReturnType<F>>>> {
  const opts = {
    fallbackData: {} as Awaited<ReturnType<F>>,
    successMessage: 'success',
    ...options,
  };

  return async (...args: Parameters<F>) => {
    try {
      const response = await fn(...args);
      return {
        success: true,
        message: opts.successMessage,
        data: response,
      };
    } catch (e) {
      const apiError = ApiError.of(e);
      return {
        data: opts.fallbackData,
        error: apiError,
        message: apiError.message,
        success: false,
      };
    }
  };
}

export function withForm<State>(
  fn: (formData: FormData) => Promise<ApiResponse<State>>,
) {
  return async (prevState: Awaited<ApiResponse<State>>, formData: FormData) => {
    const response = await fn(formData);
    return response.error || !response.success
      ? { ...response, data: prevState.data }
      : response;
  };
}
