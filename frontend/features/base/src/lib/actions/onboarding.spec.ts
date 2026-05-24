jest.mock('@next-feature/client/server', () => ({
  withApi: (fn: (...args: unknown[]) => unknown) => fn,
  withForm: (fn: (...args: unknown[]) => unknown) => fn,
}));

jest.mock('../config/client', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));

import api from '../config/client';
import {
  getOnboardingStatus,
  getOnboardingStatusSchema,
  type GetOnboardingStatusResponse,
} from './onboarding';

const mockGet = api.get as jest.MockedFunction<typeof api.get>;

const validRequestId = '4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd';

const mockResponse: GetOnboardingStatusResponse = {
  requestId: validRequestId,
  userId: '5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8',
  state: 'IN_PROGRESS',
  correlationId: 'cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77',
  startedAt: '2026-05-22T22:05:00Z',
  steps: [
    { type: 'IDENTITY_CORRELATION', state: 'SUCCEEDED' },
    { type: 'GITHUB_TEAM_PROVISIONING', state: 'PENDING_EXTERNAL_ACCEPTANCE' },
  ],
};

beforeEach(() => {
  mockGet.mockReset();
});

describe('getOnboardingStatusSchema', () => {
  it('accepts a valid UUID requestId', () => {
    expect(getOnboardingStatusSchema.safeParse({ requestId: validRequestId }).success).toBe(true);
  });

  it('rejects a non-UUID requestId', () => {
    expect(getOnboardingStatusSchema.safeParse({ requestId: 'not-a-uuid' }).success).toBe(false);
  });

  it('rejects a missing requestId', () => {
    expect(getOnboardingStatusSchema.safeParse({}).success).toBe(false);
  });
});

describe('getOnboardingStatus', () => {
  it('calls the correct endpoint and returns the response', async () => {
    mockGet.mockResolvedValue(mockResponse);

    const result = await getOnboardingStatus({ requestId: validRequestId });

    expect(mockGet).toHaveBeenCalledWith(`/api/v1/onboarding/${validRequestId}`);
    expect(result).toEqual(mockResponse);
  });

  it('throws when requestId is not a valid UUID', async () => {
    await expect(getOnboardingStatus({ requestId: 'bad-id' })).rejects.toThrow();
    expect(mockGet).not.toHaveBeenCalled();
  });

  it('propagates errors from the API client', async () => {
    mockGet.mockRejectedValue(new Error('Network error'));

    await expect(getOnboardingStatus({ requestId: validRequestId })).rejects.toThrow(
      'Network error'
    );
  });
});