'use server';

import { withApi } from '@next-feature/client/server';
import { z } from 'zod';
import api from '../config/client';

export const registerUserSchema = z.object({
  githubUserId: z.string().min(1),
  githubLogin: z.string().min(1),
  primaryEmail: z.string().email().optional(),
  displayName: z.string().min(1),
  avatarUrl: z.string().url().optional(),
  roleKeys: z.array(z.string()).optional(),
});

export type RegisterUserRequest = z.infer<typeof registerUserSchema>;

export interface StepSummary {
  type: string;
  state: string;
}

export interface RegisterUserResponse {
  userId: string;
  onboardingRequestId: string;
  status: string;
  correlationId: string;
  steps: StepSummary[];
}

export const registerUser = withApi(async (request: RegisterUserRequest) => {
  const parsed = registerUserSchema.safeParse(request);

  if (!parsed.success) {
    throw parsed.error;
  }

  return api.post<RegisterUserResponse>('/api/v1/registrations', parsed.data);
}, {});