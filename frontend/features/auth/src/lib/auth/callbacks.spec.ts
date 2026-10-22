import type { JWT } from 'next-auth/jwt';
import type { Session } from 'next-auth';
import {
  authorized as authorizedCallback,
  jwt as jwtCallback,
  redirect as redirectCallback,
  session as sessionCallback,
} from './callbacks';

jest.mock('@feature/onboarding/server', () => ({
  registerUser: jest.fn(),
  exchangeToken: jest.fn(),
}));

import { exchangeToken, registerUser } from '@feature/onboarding/server';

const mockRegisterUser = registerUser as jest.MockedFunction<
  typeof registerUser
>;
const mockExchangeToken = exchangeToken as jest.MockedFunction<
  typeof exchangeToken
>;

// Callbacks are typed against NextAuth's full param unions; these helpers
// accept just the fields the callbacks read.
async function jwt(params: {
  token: JWT;
  account: Record<string, unknown> | null;
  profile?: Record<string, unknown>;
}): Promise<JWT> {
  return (await jwtCallback({ user: {}, ...params } as never)) as JWT;
}

async function session(params: {
  session: Session;
  token: JWT;
}): Promise<Session> {
  return (await sessionCallback(params as never)) as Session;
}

const baseToken: JWT = { sub: 'user-123', iat: 0, exp: 0, jti: '' };

function makeGithubAccount(accessToken = 'gho_test') {
  return {
    provider: 'github',
    type: 'oauth' as const,
    providerAccountId: '12345678',
    access_token: accessToken,
  };
}

function makeGithubProfile(overrides: Record<string, unknown> = {}) {
  return {
    id: 12345678,
    login: 'student-dev',
    name: 'Student Dev',
    email: 'student@example.com',
    avatar_url: 'https://avatars.githubusercontent.com/u/12345678',
    ...overrides,
  };
}

function makeRegistrationSuccess() {
  return {
    success: true,
    data: {
      userId: 'user-uuid',
      onboardingRequestId: 'onboarding-uuid',
      status: 'IN_PROGRESS',
      correlationId: 'correlation-uuid',
      steps: [],
    },
  };
}

function makeTokenSuccess(token = 'backend.jwt', expiresIn = 86400) {
  return { success: true, data: { token, expiresIn } };
}

beforeEach(() => {
  mockRegisterUser.mockReset();
  mockExchangeToken.mockReset();
});

describe('jwt — github identity mapping', () => {
  it('maps github identity fields on sign-in', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwt({
      token: { ...baseToken },
      account: makeGithubAccount('gho_abc'),
      profile: makeGithubProfile(),
    });

    expect(result.githubId).toBe('12345678');
    expect(result.githubLogin).toBe('student-dev');
    expect(result.githubAccessToken).toBe('gho_abc');
  });

  it('converts numeric github id to string', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile({ id: 99999, login: 'another-user' }),
    });

    expect(typeof result.githubId).toBe('string');
    expect(result.githubId).toBe('99999');
  });

  it('does not set github fields when provider is not github', async () => {
    const result = await jwt({
      token: { ...baseToken },
      account: {
        provider: 'credentials',
        type: 'credentials',
        providerAccountId: 'cred',
      },
      profile: undefined,
    });

    expect(result.githubId).toBeUndefined();
    expect(mockRegisterUser).not.toHaveBeenCalled();
    expect(mockExchangeToken).not.toHaveBeenCalled();
  });

  it('does not modify token when account is null', async () => {
    const token = {
      ...baseToken,
      githubId: 'existing-id',
      githubLogin: 'existing-login',
    };

    const result = await jwt({ token, account: null, profile: undefined });

    expect(result.githubId).toBe('existing-id');
    expect(mockRegisterUser).not.toHaveBeenCalled();
  });
});

describe('jwt — registration', () => {
  it('calls registerUser on first sign-in and stores onboardingRequestId and correlationId', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile(),
    });

    expect(mockRegisterUser).toHaveBeenCalledWith({
      githubUserId: '12345678',
      githubLogin: 'student-dev',
      primaryEmail: 'student@example.com',
      displayName: 'Student Dev',
      avatarUrl: 'https://avatars.githubusercontent.com/u/12345678',
    });
    expect(result.onboardingRequestId).toBe('onboarding-uuid');
    expect(result.correlationId).toBe('correlation-uuid');
    expect(result.registrationError).toBeUndefined();
  });

  it('falls back to login as displayName when github name is null', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile({ name: null }),
    });

    expect(mockRegisterUser).toHaveBeenCalledWith(
      expect.objectContaining({ displayName: 'student-dev' }),
    );
  });

  it('omits primaryEmail when github email is null', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile({ email: null }),
    });

    expect(mockRegisterUser).toHaveBeenCalledWith(
      expect.objectContaining({ primaryEmail: undefined }),
    );
  });

  it('does not call registerUser when onboardingRequestId is already set', async () => {
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);
    const token = {
      ...baseToken,
      onboardingRequestId: 'existing-onboarding-uuid',
    };

    const result = await jwt({
      token,
      account: makeGithubAccount(),
      profile: makeGithubProfile(),
    });

    expect(mockRegisterUser).not.toHaveBeenCalled();
    expect(result.onboardingRequestId).toBe('existing-onboarding-uuid');
  });

  it('sets registrationError when the API returns a non-success response', async () => {
    mockRegisterUser.mockResolvedValue({
      success: false,
      data: null,
      error: {},
    } as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile(),
    });

    expect(result.registrationError).toBe(true);
    expect(result.onboardingRequestId).toBeUndefined();
  });

  it('sets registrationError when registerUser throws', async () => {
    mockRegisterUser.mockRejectedValue(new Error('Network error'));
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile(),
    });

    expect(result.registrationError).toBe(true);
  });

  it('still issues a backend token when registration fails', async () => {
    mockRegisterUser.mockRejectedValue(new Error('Network error'));
    mockExchangeToken.mockResolvedValue(makeTokenSuccess('my.jwt') as never);

    const result = await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile(),
    });

    expect(result.backendToken).toBe('my.jwt');
  });
});

describe('jwt — backend token issuance', () => {
  it('calls exchangeToken with the github access token on sign-in', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    await jwt({
      token: { ...baseToken },
      account: makeGithubAccount('gho_abc'),
      profile: makeGithubProfile(),
    });

    expect(mockExchangeToken).toHaveBeenCalledWith('gho_abc');
  });

  it('stores backendToken and backendTokenExpiresAt on sign-in', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(
      makeTokenSuccess('my.jwt', 3600) as never,
    );

    const before = Date.now();
    const result = await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile(),
    });
    const after = Date.now();

    expect(result.backendToken).toBe('my.jwt');
    expect(result.backendTokenExpiresAt).toBeGreaterThanOrEqual(
      before + 3600 * 1000,
    );
    expect(result.backendTokenExpiresAt).toBeLessThanOrEqual(
      after + 3600 * 1000,
    );
  });

  it('does not set backendToken when exchangeToken returns failure', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue({
      success: false,
      data: null,
    } as never);

    const result = await jwt({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile(),
    });

    expect(result.backendToken).toBeUndefined();
  });

  it('does not call exchangeToken when the github account has no access token', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);

    const result = await jwt({
      token: { ...baseToken },
      account: { ...makeGithubAccount(), access_token: undefined },
      profile: makeGithubProfile(),
    });

    expect(mockExchangeToken).not.toHaveBeenCalled();
    expect(result.backendToken).toBeUndefined();
  });
});

describe('jwt — backend token refresh', () => {
  it('refreshes backendToken when within 5 minutes of expiry', async () => {
    mockExchangeToken.mockResolvedValue(
      makeTokenSuccess('refreshed.jwt') as never,
    );
    const expiresAt = Date.now() + 4 * 60 * 1000; // 4 min from now — inside threshold

    const result = await jwt({
      token: {
        ...baseToken,
        githubAccessToken: 'gho_test',
        backendToken: 'old.jwt',
        backendTokenExpiresAt: expiresAt,
      },
      account: null,
      profile: undefined,
    });

    expect(mockExchangeToken).toHaveBeenCalledWith('gho_test');
    expect(result.backendToken).toBe('refreshed.jwt');
  });

  it('does not refresh backendToken when well outside expiry threshold', async () => {
    const expiresAt = Date.now() + 60 * 60 * 1000; // 1 hour from now — outside threshold

    const result = await jwt({
      token: {
        ...baseToken,
        githubAccessToken: 'gho_test',
        backendToken: 'valid.jwt',
        backendTokenExpiresAt: expiresAt,
      },
      account: null,
      profile: undefined,
    });

    expect(mockExchangeToken).not.toHaveBeenCalled();
    expect(result.backendToken).toBe('valid.jwt');
  });

  it('does not attempt refresh when githubAccessToken is missing', async () => {
    const expiresAt = Date.now() + 60 * 1000; // near expiry but no access token

    const result = await jwt({
      token: {
        ...baseToken,
        backendToken: 'old.jwt',
        backendTokenExpiresAt: expiresAt,
      },
      account: null,
      profile: undefined,
    });

    expect(mockExchangeToken).not.toHaveBeenCalled();
    expect(result.backendToken).toBe('old.jwt');
  });

  it('does not attempt refresh when backendTokenExpiresAt is missing', async () => {
    await jwt({
      token: {
        ...baseToken,
        githubAccessToken: 'gho_test',
        backendToken: 'some.jwt',
      },
      account: null,
      profile: undefined,
    });

    expect(mockExchangeToken).not.toHaveBeenCalled();
  });

  it('keeps existing backendToken when refresh call fails', async () => {
    mockExchangeToken.mockResolvedValue({
      success: false,
      data: null,
    } as never);
    const expiresAt = Date.now() + 60 * 1000;

    const result = await jwt({
      token: {
        ...baseToken,
        githubAccessToken: 'gho_test',
        backendToken: 'old.jwt',
        backendTokenExpiresAt: expiresAt,
      },
      account: null,
      profile: undefined,
    });

    expect(result.backendToken).toBe('old.jwt');
  });
});

describe('session', () => {
  function makeSession(extra: Record<string, unknown> = {}) {
    return {
      user: {
        name: 'Test User',
        email: 'test@example.com',
        image: null,
        ...extra,
      },
      expires: '2099-01-01T00:00:00.000Z',
    } as unknown as Session;
  }

  it('propagates all token fields to session user', async () => {
    const token = {
      ...baseToken,
      githubId: '12345678',
      githubLogin: 'student-dev',
      githubAccessToken: 'gho_abc',
      onboardingRequestId: 'onboarding-uuid',
      correlationId: 'correlation-uuid',
    };

    const result = await session({ session: makeSession(), token });

    expect(result.user.githubId).toBe('12345678');
    expect(result.user.githubLogin).toBe('student-dev');
    expect(result.user.githubAccessToken).toBe('gho_abc');
    expect(result.user.onboardingRequestId).toBe('onboarding-uuid');
    expect(result.user.correlationId).toBe('correlation-uuid');
  });

  it('propagates backendToken to session user', async () => {
    const token = { ...baseToken, backendToken: 'signed.jwt.value' };

    const result = await session({ session: makeSession(), token });

    expect(result.user.backendToken).toBe('signed.jwt.value');
  });

  it('propagates registrationError flag to session user', async () => {
    const token = { ...baseToken, registrationError: true };

    const result = await session({ session: makeSession(), token });

    expect(result.user.registrationError).toBe(true);
  });

  it('leaves onboarding fields undefined when token has none', async () => {
    const result = await session({
      session: makeSession(),
      token: { ...baseToken },
    });

    expect(result.user.onboardingRequestId).toBeUndefined();
    expect(result.user.correlationId).toBeUndefined();
    expect(result.user.registrationError).toBeUndefined();
    expect(result.user.backendToken).toBeUndefined();
  });

  it('returns the session unchanged when session.user is falsy', async () => {
    const empty = { expires: '2099-01-01T00:00:00.000Z' } as Session;

    const result = await session({ session: empty, token: { ...baseToken } });

    expect(result).toEqual(empty);
  });
});

describe('redirect', () => {
  const baseUrl = 'http://localhost:3000';

  function redirect(url: string) {
    return redirectCallback({ url, baseUrl });
  }

  it('resolves relative callback URLs against baseUrl', async () => {
    await expect(redirect('/onboarding')).resolves.toBe(
      'http://localhost:3000/onboarding',
    );
  });

  it('allows absolute URLs on the same origin', async () => {
    await expect(redirect('http://localhost:3000/dashboard')).resolves.toBe(
      'http://localhost:3000/dashboard',
    );
  });

  it('falls back to baseUrl for a different origin', async () => {
    await expect(redirect('https://evil.example.com/phish')).resolves.toBe(
      baseUrl,
    );
  });
});

describe('authorized', () => {
  function authorized(auth: unknown) {
    return authorizedCallback({ auth, request: {} } as never);
  }

  it('returns true when the session has a user', () => {
    expect(authorized({ user: { name: 'Test User' } })).toBe(true);
  });

  it('returns false when the session has no user', () => {
    expect(authorized({ expires: '2099-01-01T00:00:00.000Z' })).toBe(false);
  });

  it('returns false when there is no session', () => {
    expect(authorized(null)).toBe(false);
  });
});
