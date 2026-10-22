import { z } from 'zod';

/**
 * [get-onboarding-status]
 * next-feature@0.1.4-beta.1
 * October 21st 2026, 3:41:16 pm
 */
export const getOnboardingStatusSchema = z.object({
  requestId: z.string().uuid(),
});
export type GetOnboardingStatusRequest = z.infer<
  typeof getOnboardingStatusSchema
>;
export type GetOnboardingStatusResponse = {
  requestId: string;
  userId: string;
  state: string;
  correlationId: string;
  startedAt: string;
  steps: OnboardingStep[];
};
export interface OnboardingStep {
  type: string;
  state: string;
  target?: OnboardingStepTarget;
  attemptCount?: number;
  lastErrorCode?: string | null;
  startedAt?: string;
  completedAt?: string;
}
export interface OnboardingStepTarget {
  provider: string;
  targetType: string;
  externalKey: string;
}
