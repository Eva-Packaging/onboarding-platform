import { unauthenticated as api } from '../config/client';
import { exchangeToken, registerUser } from './user';
import {
  registerUserSchema,
  type ExchangeTokenResponse,
  type RegisterUserResponse,
} from '../types/user';

jest.mock('server-only', () => ({}));
jest.mock('@next-feature/client/server', () => ({
  withApi: (fn: (...args: unknown[]) => unknown) => fn,
  withForm: (fn: (...args: unknown[]) => unknown) => fn,
}));
jest.mock('../config/client', () => ({
  __esModule: true,
  unauthenticated: { post: jest.fn() },
}));

const mockPost = api.post as jest.MockedFunction<typeof api.post>;

const validPayload = {
  githubUserId: '12345678',
  githubLogin: 'student-dev',
  displayName: 'Student Dev',
};

const fullPayload = {
  ...validPayload,
  primaryEmail: 'student@example.com',
  avatarUrl: 'https://avatars.githubusercontent.com/u/12345678',
  roleKeys: ['STUDENT'],
};

const mockRegisterResponse: RegisterUserResponse = {
  userId: 'user-uuid',
  onboardingRequestId: 'onboarding-uuid',
  status: 'IN_PROGRESS',
  correlationId: 'correlation-uuid',
  steps: [{ type: 'IDENTITY_CORRELATION', state: 'PENDING' }],
};

const mockTokenResponse: ExchangeTokenResponse = {
  token: 'backend-jwt',
  expiresIn: 3600,
};

beforeEach(() => {
  mockPost.mockReset();
});

describe('registerUserSchema', () => {
  it('accepts a minimal valid payload', () => {
    expect(registerUserSchema.safeParse(validPayload).success).toBe(true);
  });

  it('accepts a full payload with optional fields', () => {
    expect(registerUserSchema.safeParse(fullPayload).success).toBe(true);
  });

  it.each(['githubUserId', 'githubLogin', 'displayName'] as const)(
    'rejects missing %s',
    (field) => {
      const { [field]: _, ...rest } = validPayload;
      expect(registerUserSchema.safeParse(rest).success).toBe(false);
    },
  );

  it.each(['githubUserId', 'githubLogin', 'displayName'] as const)(
    'rejects empty %s',
    (field) => {
      expect(
        registerUserSchema.safeParse({ ...validPayload, [field]: '' }).success,
      ).toBe(false);
    },
  );

  it('rejects an invalid email format', () => {
    expect(
      registerUserSchema.safeParse({
        ...validPayload,
        primaryEmail: 'not-an-email',
      }).success,
    ).toBe(false);
  });

  it('rejects an invalid avatar URL', () => {
    expect(
      registerUserSchema.safeParse({ ...validPayload, avatarUrl: 'not-a-url' })
        .success,
    ).toBe(false);
  });

  it('rejects non-string roleKeys', () => {
    expect(
      registerUserSchema.safeParse({ ...validPayload, roleKeys: [1] }).success,
    ).toBe(false);
  });
});

describe('registerUser', () => {
  it('posts to /api/v1/users/registrations and returns the response', async () => {
    mockPost.mockResolvedValue(mockRegisterResponse);

    const result = await registerUser(validPayload);

    expect(mockPost).toHaveBeenCalledWith(
      '/api/v1/users/registrations',
      validPayload,
    );
    expect(result).toEqual(mockRegisterResponse);
  });

  it('passes optional fields through to the API', async () => {
    mockPost.mockResolvedValue(mockRegisterResponse);

    await registerUser(fullPayload);

    expect(mockPost).toHaveBeenCalledWith(
      '/api/v1/users/registrations',
      fullPayload,
    );
  });

  it('strips unknown fields before posting', async () => {
    mockPost.mockResolvedValue(mockRegisterResponse);

    await registerUser({ ...validPayload, extra: 'nope' } as never);

    expect(mockPost).toHaveBeenCalledWith(
      '/api/v1/users/registrations',
      validPayload,
    );
  });

  it('throws when schema validation fails', async () => {
    await expect(
      registerUser({ githubUserId: '', githubLogin: '', displayName: '' }),
    ).rejects.toThrow();
    expect(mockPost).not.toHaveBeenCalled();
  });

  it('throws when called without a payload', async () => {
    await expect(registerUser()).rejects.toThrow();
    expect(mockPost).not.toHaveBeenCalled();
  });

  it('propagates errors from the API client', async () => {
    mockPost.mockRejectedValue(new Error('Network error'));

    await expect(registerUser(validPayload)).rejects.toThrow('Network error');
  });
});

describe('exchangeToken', () => {
  it('posts the GitHub access token to /api/v1/users/auth/token', async () => {
    mockPost.mockResolvedValue(mockTokenResponse);

    const result = await exchangeToken('gho_abc123');

    expect(mockPost).toHaveBeenCalledWith('/api/v1/users/auth/token', {
      githubAccessToken: 'gho_abc123',
    });
    expect(result).toEqual(mockTokenResponse);
  });

  it('propagates errors from the API client', async () => {
    mockPost.mockRejectedValue(new Error('Unauthorized'));

    await expect(exchangeToken('gho_bad')).rejects.toThrow('Unauthorized');
  });
});
