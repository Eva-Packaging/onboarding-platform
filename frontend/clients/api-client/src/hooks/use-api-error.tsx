'use client';

import { useState, useCallback } from 'react';
import { ApiError } from '../lib/error';
import { getErrorMessage } from '../lib/utils/error';

interface UseApiErrorResult {
  error: ApiError | null;
  setError: (error: ApiError | null) => void;
  clearError: () => void;
  handleError: (error: unknown) => void;
  errorMessage: string | null;
}

export function useApiError(): UseApiErrorResult {
  const [error, setError] = useState<ApiError | null>(null);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const handleError = useCallback((err: unknown) => {
    if (err instanceof ApiError) {
      setError(err);
    } else {
      console.error('Non-API error:', err);
    }
  }, []);

  const errorMessage = error ? getErrorMessage(error) : null;

  return {
    error,
    setError,
    clearError,
    handleError,
    errorMessage,
  };
}
