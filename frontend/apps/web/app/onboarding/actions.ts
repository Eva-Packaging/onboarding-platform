'use server';

import { auth } from '@feature/auth/server';
import { getOnboardingStatus, retryOnboardingSteps } from '@feature/onboarding/server';
import type { GetOnboardingStatusResponse } from '@feature/onboarding';

export type OnboardingStatusResult =
  | { ok: true; status: GetOnboardingStatusResponse }
  | { ok: false; message: string };

/**
 * Loads the onboarding status for the signed-in user's own request. The request ID
 * comes from the session (set at registration), never from the client.
 */
export async function fetchOnboardingStatus(): Promise<OnboardingStatusResult> {
  const session = await auth();
  const requestId = session?.user?.onboardingRequestId;

  if (!requestId) {
    return { ok: false, message: 'No onboarding request found for this account.' };
  }

  const result = await getOnboardingStatus({ requestId });
  if (!result.success) {
    return {
      ok: false,
      message: result.error?.message ?? 'Unable to load onboarding status.',
    };
  }

  return { message: "", ok: true, status: result.data };
}

export type RetryStepsResult = { ok: true } | { ok: false; message: string };

/** Requeues the given failed steps on the signed-in user's onboarding request. */
export async function retryFailedSteps(steps: string[]): Promise<RetryStepsResult> {
  const session = await auth();
  const requestId = session?.user?.onboardingRequestId;

  if (!requestId) {
    return { ok: false, message: 'No onboarding request found for this account.' };
  }

  const result = await retryOnboardingSteps({ requestId, steps });
  if (!result.success) {
    return {
      ok: false,
      message: result.error?.message ?? 'Unable to retry these steps.',
    };
  }

  return { ok: true };
}
