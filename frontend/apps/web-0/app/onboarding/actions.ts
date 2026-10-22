'use server';

import {
  getOnboardingStatus as getOnboardingStatusAction,
} from '@feature/onboarding/server';
import type { GetOnboardingStatusRequest } from '@feature/onboarding';

// @feature/onboarding/server is server-only; expose it to client components
// (OnboardingPoller) as a server action.
export async function getOnboardingStatus(request: GetOnboardingStatusRequest) {
  return getOnboardingStatusAction(request);
}
