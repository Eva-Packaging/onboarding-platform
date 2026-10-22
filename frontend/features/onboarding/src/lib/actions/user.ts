import 'server-only';

import { withApi } from '@next-feature/client/server';
import { unauthenticated as api } from '../config/client';
import {
  registerUserSchema,
  type RegisterUserRequest,
  type RegisterUserResponse,
  type ExchangeTokenResponse,
} from '../types/user';

/**
 * [register-user]
 * next-feature@0.1.4-beta.1
 * October 21st 2026, 4:10:27 pm
 */
export const registerUser = withApi(async (options?: RegisterUserRequest) => {
  const parsed = registerUserSchema.safeParse(options);

  if (!parsed.success) {
    throw parsed.error;
  }

  const endpoint = '/api/v1/users/registrations';
  return await api.post<RegisterUserResponse>(endpoint, parsed.data);
}, {});

/**
 * [exchange-token]
 * next-feature@0.1.4-beta.1
 * October 21st 2026, 6:55:11 pm
 */
export const exchangeToken = withApi(async (githubAccessToken: string) => {
  return await api.post<ExchangeTokenResponse>('/api/v1/users/auth/token', {
    githubAccessToken,
  });
}, {});
