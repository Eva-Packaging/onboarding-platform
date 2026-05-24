'use server';

import { withApi } from '@next-feature/client/server';
import { z } from 'zod';
import api from '../config/client';
import { getOnboardingStatusSchema } from '../types/schema';

export interface OnboardingStepTarget {
  provider: string;
  targetType: string;
  externalKey: string;
}

export interface OnboardingStep {
  type: string;
  state: string;
  target?: OnboardingStepTarget;
  attemptCount?: number;
  lastErrorCode?: string | null;
  startedAt?: string;
  completedAt?: string;
}


export type GetOnboardingStatusRequest = z.infer<typeof getOnboardingStatusSchema>;

export interface GetOnboardingStatusResponse {
  requestId: string;
  userId: string;
  state: string;
  correlationId: string;
  startedAt: string;
  steps: OnboardingStep[];
}

export const getOnboardingStatus = withApi(
  async (request: GetOnboardingStatusRequest) => {
    const parsed = getOnboardingStatusSchema.safeParse(request);

    if (!parsed.success) {
      throw parsed.error;
    }

    return api.get<GetOnboardingStatusResponse>(
      `/api/v1/onboarding/${parsed.data.requestId}`
    );
  },
  {},
);