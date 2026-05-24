'use client';

import { useQuery } from '@tanstack/react-query';
import { getOnboardingStatus } from '@feature/base/server';
import type { GetOnboardingStatusResponse } from '@feature/base/server';
import OnboardingStatusBanner from './OnboardingStatusBanner';
import StepList from './StepList';

export const TERMINAL_STATES = new Set([
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'PARTIAL_SUCCESS',
]);

export function shouldStopPolling(state: string | undefined): boolean {
  return TERMINAL_STATES.has(state ?? '');
}

interface Props {
  requestId: string | null;
  correlationId?: string;
}

export default function OnboardingPoller({ requestId, correlationId }: Props) {
  const { data, isLoading, isError } = useQuery<GetOnboardingStatusResponse>({
    queryKey: ['onboarding-status', requestId],
    queryFn: async () => {
      const response = await getOnboardingStatus({ requestId: requestId! });
      return response.data;
    },
    enabled: !!requestId,
    refetchInterval: (query) => {
      return shouldStopPolling(query.state.data?.state) ? false : 3000;
    },
  });

  if (!requestId) {
    return (
      <div className="p-6">
        <h2 className="text-xl font-semibold text-gray-800">Onboarding Not Started</h2>
        <p className="mt-2 text-gray-600">
          Your registration may still be processing. Please refresh the page or contact support.
        </p>
        {correlationId && (
          <p className="mt-4 text-sm text-gray-500">
            Reference ID: <span className="font-mono">{correlationId}</span>
          </p>
        )}
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="p-6">
        <p className="text-gray-600">Loading your onboarding status…</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-6">
        <p className="text-red-600">
          Unable to load onboarding status. Please refresh the page.
        </p>
        {correlationId && (
          <p className="mt-2 text-sm text-gray-500">
            Reference ID: <span className="font-mono">{correlationId}</span>
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl p-6">
      <OnboardingStatusBanner state={data?.state} />
      <StepList steps={data?.steps ?? []} />
    </div>
  );
}