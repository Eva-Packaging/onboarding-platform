import { AxiosError } from 'axios';
import { ZodError } from 'zod';
import { ApiError, ApiErrorBuilder } from '../error';
import { handleAxiosError } from './axios';
import { handleZodError } from './zod';

/**
 * Extract user-friendly error message from ApiError
 *
 * [get-error-message]
 * next-feature@0.0.11-beta
 * November 4th 2025, 11:47:45 am
 *
 */
export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    // Use Spring Boot ProblemDetail information
    if (error.problemDetail.detail) {
      return error.problemDetail.detail;
    }
    if (error.problemDetail.title) {
      return error.problemDetail.title;
    }
    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'An unexpected error occurred';
}

/**
 * Check if error is a specific HTTP status
 */
export function isHttpStatus(error: unknown, status: number): boolean {
  return error instanceof ApiError && error.status === status;
}

/**
 * Handle common API errors
 */
function handleApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }

  if (error instanceof AxiosError) {
    return handleAxiosError(error);
  }

  if (error instanceof ZodError) {
    return handleZodError(error);
  }

  if (error instanceof Error) {
    return new ApiErrorBuilder(error).build();
  }

  const unresolvedError = new Error('Unknown api error');
  return new ApiErrorBuilder(unresolvedError)
    .instance('handleApiError')
    .build();
}

export default handleApiError;
