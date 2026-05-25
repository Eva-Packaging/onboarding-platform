'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { getOnboardingStatus } from '@feature/base/server';
import type { GetOnboardingStatusResponse } from '@feature/base/server';
import OnboardingStatusBanner from './OnboardingStatusBanner';
import StepList from './StepList';
import CorrelationIdBadge from './CorrelationIdBadge';
import { logger } from '@feature/logging';

const log = logger.child({ module: 'OnboardingPoller' });

export const TERMINAL_STATES = new Set([
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'PARTIAL_SUCCESS',
]);

export function shouldStopPolling(state: string | undefined): boolean {
  return TERMINAL_STATES.has(state ?? '');
}

const ACTION_STEP_STATES = new Set(['PENDING_EXTERNAL_ACCEPTANCE', 'ACTION_REQUIRED']);
const CORRELATION_BADGE_STATES = new Set(['FAILED', 'PARTIAL_SUCCESS', 'CANCELLED']);

export function shouldShowCorrelationBadge(data: GetOnboardingStatusResponse | undefined): boolean {
  if (!data) return false;
  if (CORRELATION_BADGE_STATES.has(data.state)) return true;
  return data.steps.some((s) => ACTION_STEP_STATES.has(s.state));
}

interface Props {
  requestId: string | null;
  correlationId?: string;
}

export default function OnboardingPoller({ requestId, correlationId }: Props) {
  const router = useRouter();

  const { data, isLoading, isError } = useQuery<GetOnboardingStatusResponse>({
    queryKey: ['onboarding-status', requestId],
    queryFn: async () => {
      const response = await getOnboardingStatus({ requestId: requestId! });
      log.info({ response })
      return response.data;
    },
    enabled: !!requestId,
    refetchInterval: (query) => {
      return shouldStopPolling(query.state.data?.state) ? false : 30000;
    },
  });

  useEffect(() => {
    if (data?.state === 'COMPLETED') {
      router.push('/dashboard');
    } else if (data?.state === 'FAILED' || data?.state === 'CANCELLED') {
      router.push('/support');
    }
  }, [data?.state, router]);

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

  const resolvedCorrelationId = data?.correlationId ?? correlationId;

  return (
    <div className="mx-auto max-w-2xl p-6">
      <OnboardingStatusBanner state={data?.state} />
      <StepList steps={data?.steps ?? []} />
      {shouldShowCorrelationBadge(data) && resolvedCorrelationId && (
        <CorrelationIdBadge correlationId={resolvedCorrelationId} />
      )}
    </div>
  );
}
