import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import OnboardingPoller, {
  shouldStopPolling,
  shouldShowCorrelationBadge,
  TERMINAL_STATES,
} from '../../components/onboarding/OnboardingPoller';

jest.mock('@feature/base/server', () => ({
  getOnboardingStatus: jest.fn(),
}));

import { getOnboardingStatus } from '@feature/base/server';

const mockGetOnboardingStatus = getOnboardingStatus as jest.MockedFunction<
  typeof getOnboardingStatus
>;

const REQUEST_ID = '4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd';
const CORRELATION_ID = 'cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77';

function makeApiResponse(state: string) {
  return {
    success: true,
    data: {
      requestId: REQUEST_ID,
      userId: 'user-uuid',
      state,
      correlationId: CORRELATION_ID,
      startedAt: '2026-05-22T22:05:00Z',
      steps: [],
    },
  };
}

function wrapper({ children }: { children: React.ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  mockGetOnboardingStatus.mockReset();
});

describe('shouldStopPolling', () => {
  it.each([...TERMINAL_STATES])('returns true for terminal state %s', (state) => {
    expect(shouldStopPolling(state)).toBe(true);
  });

  it('returns false for IN_PROGRESS', () => {
    expect(shouldStopPolling('IN_PROGRESS')).toBe(false);
  });

  it('returns false for REQUESTED', () => {
    expect(shouldStopPolling('REQUESTED')).toBe(false);
  });

  it('returns false for undefined', () => {
    expect(shouldStopPolling(undefined)).toBe(false);
  });
});

describe('OnboardingPoller — no requestId', () => {
  it('renders the not-started message when requestId is null', () => {
    render(<OnboardingPoller requestId={null} />, { wrapper });

    expect(screen.getByText(/Onboarding Not Started/i)).toBeInTheDocument();
    expect(mockGetOnboardingStatus).not.toHaveBeenCalled();
  });

  it('shows the correlationId when provided and requestId is null', () => {
    render(<OnboardingPoller requestId={null} correlationId={CORRELATION_ID} />, { wrapper });

    expect(screen.getByText(CORRELATION_ID)).toBeInTheDocument();
  });
});

describe('OnboardingPoller — with requestId', () => {
  it('shows a loading state while the query is in flight', () => {
    mockGetOnboardingStatus.mockImplementation(() => new Promise(() => {}));

    render(<OnboardingPoller requestId={REQUEST_ID} />, { wrapper });

    expect(screen.getByText(/Loading your onboarding status/i)).toBeInTheDocument();
  });

  it('renders the status banner when data is returned', async () => {
    mockGetOnboardingStatus.mockResolvedValue(makeApiResponse('IN_PROGRESS') as never);

    render(<OnboardingPoller requestId={REQUEST_ID} />, { wrapper });

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('Setting up your account');
    });
  });

  it('renders the COMPLETED banner for a completed onboarding', async () => {
    mockGetOnboardingStatus.mockResolvedValue(makeApiResponse('COMPLETED') as never);

    render(<OnboardingPoller requestId={REQUEST_ID} />, { wrapper });

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent('Onboarding complete');
    });
  });

  it('renders the error state when the query fails', async () => {
    mockGetOnboardingStatus.mockRejectedValue(new Error('Network error'));

    render(<OnboardingPoller requestId={REQUEST_ID} />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText(/Unable to load onboarding status/i)).toBeInTheDocument();
    });
  });

  it('shows the correlationId in the error state when provided', async () => {
    mockGetOnboardingStatus.mockRejectedValue(new Error('Network error'));

    render(
      <OnboardingPoller requestId={REQUEST_ID} correlationId={CORRELATION_ID} />,
      { wrapper }
    );

    await waitFor(() => {
      expect(screen.getByText(CORRELATION_ID)).toBeInTheDocument();
    });
  });

  it('shows the correlation badge when the request has FAILED state', async () => {
    mockGetOnboardingStatus.mockResolvedValue(makeApiResponse('FAILED') as never);

    render(<OnboardingPoller requestId={REQUEST_ID} />, { wrapper });

    await waitFor(() => {
      expect(screen.getByRole('link', { name: /contact support/i })).toBeInTheDocument();
    });
  });

  it('does not show the correlation badge for IN_PROGRESS state with no action-required steps', async () => {
    mockGetOnboardingStatus.mockResolvedValue(makeApiResponse('IN_PROGRESS') as never);

    render(<OnboardingPoller requestId={REQUEST_ID} />, { wrapper });

    await waitFor(() => {
      expect(screen.getByRole('status')).toBeInTheDocument();
    });
    expect(screen.queryByRole('link', { name: /contact support/i })).not.toBeInTheDocument();
  });
});

describe('shouldShowCorrelationBadge', () => {
  function makeData(state: string, steps: { type: string; state: string }[] = []) {
    return {
      requestId: REQUEST_ID,
      userId: 'user-uuid',
      state,
      correlationId: CORRELATION_ID,
      startedAt: '2026-05-22T22:05:00Z',
      steps,
    };
  }

  it('returns true for FAILED overall state', () => {
    expect(shouldShowCorrelationBadge(makeData('FAILED'))).toBe(true);
  });

  it('returns true for PARTIAL_SUCCESS overall state', () => {
    expect(shouldShowCorrelationBadge(makeData('PARTIAL_SUCCESS'))).toBe(true);
  });

  it('returns true for CANCELLED overall state', () => {
    expect(shouldShowCorrelationBadge(makeData('CANCELLED'))).toBe(true);
  });

  it('returns true when a step has PENDING_EXTERNAL_ACCEPTANCE state', () => {
    const data = makeData('IN_PROGRESS', [
      { type: 'GITHUB_TEAM_PROVISIONING', state: 'PENDING_EXTERNAL_ACCEPTANCE' },
    ]);
    expect(shouldShowCorrelationBadge(data)).toBe(true);
  });

  it('returns true when a step has ACTION_REQUIRED state', () => {
    const data = makeData('IN_PROGRESS', [
      { type: 'IDENTITY_CORRELATION', state: 'ACTION_REQUIRED' },
    ]);
    expect(shouldShowCorrelationBadge(data)).toBe(true);
  });

  it('returns false for COMPLETED state with no action steps', () => {
    expect(shouldShowCorrelationBadge(makeData('COMPLETED'))).toBe(false);
  });

  it('returns false for IN_PROGRESS with no action steps', () => {
    const data = makeData('IN_PROGRESS', [
      { type: 'IDENTITY_CORRELATION', state: 'SUCCEEDED' },
      { type: 'GITHUB_TEAM_PROVISIONING', state: 'PROCESSING' },
    ]);
    expect(shouldShowCorrelationBadge(data)).toBe(false);
  });

  it('returns false when data is undefined', () => {
    expect(shouldShowCorrelationBadge(undefined)).toBe(false);
  });
});