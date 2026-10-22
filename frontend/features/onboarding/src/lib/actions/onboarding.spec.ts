import api from '../config/client';
import { getOnboardingStatus, retryOnboardingSteps } from './onboarding';
import {
  getOnboardingStatusSchema,
  type GetOnboardingStatusResponse,
} from '../types/onboarding';

jest.mock('server-only', () => ({}));
jest.mock('@next-feature/client/server', () => ({
  withApi: (fn: (...args: unknown[]) => unknown) => fn,
  withForm: (fn: (...args: unknown[]) => unknown) => fn,
}));
jest.mock('../config/client', () => ({
  __esModule: true,
  default: { get: jest.fn(), post: jest.fn() },
}));

const mockGet = api.get as jest.MockedFunction<typeof api.get>;
const mockPost = api.post as jest.MockedFunction<typeof api.post>;

const validRequestId = '4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd';

const mockResponse: GetOnboardingStatusResponse = {
  requestId: validRequestId,
  userId: '5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8',
  state: 'IN_PROGRESS',
  correlationId: 'cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77',
  startedAt: '2026-05-22T22:05:00Z',
  steps: [
    { type: 'IDENTITY_CORRELATION', state: 'SUCCEEDED' },
    {
      type: 'GITHUB_TEAM_PROVISIONING',
      state: 'PENDING_EXTERNAL_ACCEPTANCE',
      target: {
        provider: 'GITHUB',
        targetType: 'TEAM',
        externalKey: 'cohort-2026',
      },
      attemptCount: 1,
      lastErrorCode: null,
      startedAt: '2026-05-22T22:05:01Z',
    },
  ],
};

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
});

describe('getOnboardingStatusSchema', () => {
  it('accepts a valid UUID requestId', () => {
    expect(
      getOnboardingStatusSchema.safeParse({ requestId: validRequestId })
        .success,
    ).toBe(true);
  });

  it('rejects a non-UUID requestId', () => {
    expect(
      getOnboardingStatusSchema.safeParse({ requestId: 'not-a-uuid' }).success,
    ).toBe(false);
  });

  it('rejects a missing requestId', () => {
    expect(getOnboardingStatusSchema.safeParse({}).success).toBe(false);
  });
});

describe('getOnboardingStatus', () => {
  it('calls /api/v1/onboarding/{requestId} and returns the response', async () => {
    mockGet.mockResolvedValue(mockResponse);

    const result = await getOnboardingStatus({ requestId: validRequestId });

    expect(mockGet).toHaveBeenCalledWith(
      `/api/v1/onboarding/${validRequestId}`,
    );
    expect(result).toEqual(mockResponse);
  });

  it('throws when requestId is not a valid UUID', async () => {
    await expect(
      getOnboardingStatus({ requestId: 'bad-id' }),
    ).rejects.toThrow();
    expect(mockGet).not.toHaveBeenCalled();
  });

  it('throws when requestId is missing', async () => {
    await expect(getOnboardingStatus({} as never)).rejects.toThrow();
    expect(mockGet).not.toHaveBeenCalled();
  });

  it('propagates errors from the API client', async () => {
    mockGet.mockRejectedValue(new Error('Network error'));

    await expect(
      getOnboardingStatus({ requestId: validRequestId }),
    ).rejects.toThrow('Network error');
  });
});

describe('retryOnboardingSteps', () => {
  it('posts the steps to /api/v1/onboarding/{requestId}/retry', async () => {
    const response = {
      requestId: validRequestId,
      state: 'IN_PROGRESS',
      requeuedSteps: ['JIRA_GROUP_PROVISIONING'],
      correlationId: mockResponse.correlationId,
    };
    mockPost.mockResolvedValue(response);

    const result = await retryOnboardingSteps(
      { requestId: validRequestId, steps: ['JIRA_GROUP_PROVISIONING'] },
    );

    expect(mockPost).toHaveBeenCalledWith(
      `/api/v1/onboarding/${validRequestId}/retry`,
      { steps: ['JIRA_GROUP_PROVISIONING'] },
    );
    expect(result).toEqual(response);
  });

  it('throws when no steps are given', async () => {
    await expect(
      retryOnboardingSteps({ requestId: validRequestId, steps: [] }),
    ).rejects.toThrow();
    expect(mockPost).not.toHaveBeenCalled();
  });
});
