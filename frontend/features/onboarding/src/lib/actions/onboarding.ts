import 'server-only';

import { withApi } from '@next-feature/client/server';
import api from '../config/client';
import {
  getOnboardingStatusSchema,
  retryOnboardingStepsSchema,
  type GetOnboardingStatusRequest,
  type GetOnboardingStatusResponse,
  type RetryOnboardingStepsRequest,
  type RetryOnboardingStepsResponse,
} from '../types/onboarding';


/**
 * [get-onboarding-status]
 * next-feature@0.1.4-beta.1
 * October 21st 2026, 3:41:16 pm
 */
export const getOnboardingStatus = withApi(
  async (request: GetOnboardingStatusRequest) => {
    const parsed = getOnboardingStatusSchema.safeParse(request);

    if (!parsed.success) {
      throw parsed.error;
    }

    return api.get<GetOnboardingStatusResponse>(
      `/api/v1/onboarding/${parsed.data.requestId}`,
    );
  },
  { fallbackData: {
      requestId: '',
      userId: '',
      state: '',
      correlationId: '',
      startedAt: '',
      steps: [],
    }},
);

/**
 * [retry-onboarding-steps]
 * Requeues FAILED or MANUAL_REVIEW steps back to PENDING.
 */
export const retryOnboardingSteps = withApi(
  async (request: RetryOnboardingStepsRequest) => {
    const parsed = retryOnboardingStepsSchema.safeParse(request);

    if (!parsed.success) {
      throw parsed.error;
    }

    const { requestId, ...body } = parsed.data;
    return api.post<RetryOnboardingStepsResponse>(
      `/api/v1/onboarding/${requestId}/retry`,
      body,
    );
  },
);
