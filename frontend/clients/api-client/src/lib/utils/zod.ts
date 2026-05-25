import { HttpStatusCode } from 'axios';
import { ZodError } from 'zod';
import { ApiError } from '../error';

/**
 * Create ApiError from Zod validation error
 *
 * [handle-zod-error]
 * next-feature@0.1.1-beta.5
 * January 11th 2026, 9:40:46 pm
 */
export function handleZodError(zodError: ZodError) {
  const errors: Record<string, string> = {};

  zodError.errors.forEach((error) => {
    error.path.forEach((path) => {
      errors[path] = error.message;
    });
  });

  return ApiError.builder()
    .originalError(zodError)
    .status(HttpStatusCode.BadRequest)
    .message('Validation Error')
    .errors(errors)
    .build();
}
