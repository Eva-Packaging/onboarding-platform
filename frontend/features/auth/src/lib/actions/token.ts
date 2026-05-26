'use server';

import { withApi } from '@next-feature/client/server';
import api from '../config/client';

export interface TokenResponse {
  token: string;
  expiresIn: number;
}

export const exchangeToken = withApi(async (githubAccessToken: string) => {
  return api.post<TokenResponse>('/api/v1/users/auth/token', { githubAccessToken });
}, {});
