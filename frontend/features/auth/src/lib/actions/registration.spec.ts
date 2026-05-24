
import api from '../config/client';
import { registerUser, type RegisterUserResponse } from './registration';
import { registerUserSchema } from '../types/schema';


jest.mock('@next-feature/client/server', () => ({
  withApi: (fn: (...args: unknown[]) => unknown) => fn,
  withForm: (fn: (...args: unknown[]) => unknown) => fn,
}));
jest.mock('../config/client', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

const mockPost = api.post as jest.MockedFunction<typeof api.post>;

const validPayload = {
  githubUserId: '12345678',
  githubLogin: 'student-dev',
  displayName: 'Student Dev',
};

const mockResponse: RegisterUserResponse = {
  userId: 'user-uuid',
  onboardingRequestId: 'onboarding-uuid',
  status: 'IN_PROGRESS',
  correlationId: 'correlation-uuid',
  steps: [{ type: 'IDENTITY_CORRELATION', state: 'PENDING' }],
};

beforeEach(() => {
  mockPost.mockReset();
});

describe('registerUserSchema', () => {
  it('accepts a minimal valid payload', () => {
    expect(registerUserSchema.safeParse(validPayload).success).toBe(true);
  });

  it('accepts a full payload with optional fields', () => {
    const result = registerUserSchema.safeParse({
      ...validPayload,
      primaryEmail: 'student@example.com',
      avatarUrl: 'https://avatars.githubusercontent.com/u/12345678',
      roleKeys: ['STUDENT'],
    });
    expect(result.success).toBe(true);
  });

  it('rejects missing githubUserId', () => {
    const { githubUserId: _, ...rest } = validPayload;
    expect(registerUserSchema.safeParse(rest).success).toBe(false);
  });

  it('rejects missing githubLogin', () => {
    const { githubLogin: _, ...rest } = validPayload;
    expect(registerUserSchema.safeParse(rest).success).toBe(false);
  });

  it('rejects missing displayName', () => {
    const { displayName: _, ...rest } = validPayload;
    expect(registerUserSchema.safeParse(rest).success).toBe(false);
  });

  it('rejects an invalid email format', () => {
    expect(
      registerUserSchema.safeParse({ ...validPayload, primaryEmail: 'not-an-email' }).success
    ).toBe(false);
  });

  it('rejects an invalid avatar URL', () => {
    expect(
      registerUserSchema.safeParse({ ...validPayload, avatarUrl: 'not-a-url' }).success
    ).toBe(false);
  });
});

describe('registerUser', () => {
  it('posts to /api/v1/users/registrations and returns the response', async () => {
    mockPost.mockResolvedValue(mockResponse);

    const result = await registerUser(validPayload);

    expect(mockPost).toHaveBeenCalledWith('/api/v1/users/registrations', validPayload);
    expect(result).toEqual(mockResponse);
  });

  it('passes optional fields through to the API', async () => {
    mockPost.mockResolvedValue(mockResponse);

    const payload = {
      ...validPayload,
      primaryEmail: 'student@example.com',
      avatarUrl: 'https://avatars.githubusercontent.com/u/12345678',
      roleKeys: ['STUDENT'],
    };

    await registerUser(payload);

    expect(mockPost).toHaveBeenCalledWith('/api/v1/users/registrations', payload);
  });

  it('throws when schema validation fails', async () => {
    await expect(registerUser({ githubUserId: '', githubLogin: '', displayName: '' })).rejects.toThrow();
    expect(mockPost).not.toHaveBeenCalled();
  });

  it('propagates errors from the API client', async () => {
    mockPost.mockRejectedValue(new Error('Network error'));

    await expect(registerUser(validPayload)).rejects.toThrow('Network error');
  });
});