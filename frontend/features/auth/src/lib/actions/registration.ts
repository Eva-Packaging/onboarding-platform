'use server';

import { withApi } from '@next-feature/client/server';
import { z } from 'zod';
import api from '../config/client';
import { registerUserSchema } from '../types/schema';

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