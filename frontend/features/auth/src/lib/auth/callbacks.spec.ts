import { jwtCallback, sessionCallback } from './callbacks';

jest.mock('../actions/registration', () => ({
  registerUser: jest.fn(),
}));

jest.mock('../actions/token', () => ({
  exchangeToken: jest.fn(),
}));

import { registerUser } from '../actions/registration';
import { exchangeToken } from '../actions/token';

const mockRegisterUser = registerUser as jest.MockedFunction<typeof registerUser>;
const mockExchangeToken = exchangeToken as jest.MockedFunction<typeof exchangeToken>;

const baseToken = { sub: 'user-123', iat: 0, exp: 0, jti: '' };

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

describe('jwtCallback — github identity mapping', () => {
  it('maps github identity fields on sign-in', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount('gho_abc'),
      profile: makeGithubProfile() as never,
    });

    expect(result.githubId).toBe('12345678');
    expect(result.githubLogin).toBe('student-dev');
    expect(result.githubAccessToken).toBe('gho_abc');
  });

  it('converts numeric github id to string', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile({ id: 99999, login: 'another-user' }) as never,
    });

    expect(typeof result.githubId).toBe('string');
    expect(result.githubId).toBe('99999');
  });

  it('does not set github fields when provider is not github', async () => {
    const result = await jwtCallback({
      token: { ...baseToken },
      account: { provider: 'credentials', type: 'credentials', providerAccountId: 'cred' },
      profile: undefined,
    });

    expect(result.githubId).toBeUndefined();
    expect(mockRegisterUser).not.toHaveBeenCalled();
    expect(mockExchangeToken).not.toHaveBeenCalled();
  });

  it('does not modify token when account is null', async () => {
    const token = { ...baseToken, githubId: 'existing-id', githubLogin: 'existing-login' };

    const result = await jwtCallback({ token, account: null, profile: undefined });

    expect(result.githubId).toBe('existing-id');
    expect(mockRegisterUser).not.toHaveBeenCalled();
  });
});

describe('jwtCallback — registration', () => {
  it('calls registerUser on first sign-in and stores onboardingRequestId and correlationId', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile() as never,
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

    await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile({ name: null }) as never,
    });

    expect(mockRegisterUser).toHaveBeenCalledWith(
      expect.objectContaining({ displayName: 'student-dev' })
    );
  });

  it('does not call registerUser when onboardingRequestId is already set', async () => {
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);
    const token = { ...baseToken, onboardingRequestId: 'existing-onboarding-uuid' };

    const result = await jwtCallback({
      token,
      account: makeGithubAccount(),
      profile: makeGithubProfile() as never,
    });

    expect(mockRegisterUser).not.toHaveBeenCalled();
    expect(result.onboardingRequestId).toBe('existing-onboarding-uuid');
  });

  it('sets registrationError when the API returns a non-success response', async () => {
    mockRegisterUser.mockResolvedValue({ success: false, data: null, error: {} } as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile() as never,
    });

    expect(result.registrationError).toBe(true);
    expect(result.onboardingRequestId).toBeUndefined();
  });

  it('sets registrationError when registerUser throws', async () => {
    mockRegisterUser.mockRejectedValue(new Error('Network error'));
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile() as never,
    });

    expect(result.registrationError).toBe(true);
  });
});

describe('jwtCallback — backend token issuance', () => {
  it('calls exchangeToken with the github access token on sign-in', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess() as never);

    await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount('gho_abc'),
      profile: makeGithubProfile() as never,
    });

    expect(mockExchangeToken).toHaveBeenCalledWith('gho_abc');
  });

  it('stores backendToken and backendTokenExpiresAt on sign-in', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue(makeTokenSuccess('my.jwt', 3600) as never);

    const before = Date.now();
    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile() as never,
    });
    const after = Date.now();

    expect(result.backendToken).toBe('my.jwt');
    expect(result.backendTokenExpiresAt).toBeGreaterThanOrEqual(before + 3600 * 1000);
    expect(result.backendTokenExpiresAt).toBeLessThanOrEqual(after + 3600 * 1000);
  });

  it('does not set backendToken when exchangeToken returns failure', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);
    mockExchangeToken.mockResolvedValue({ success: false, data: null } as never);

    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile() as never,
    });

    expect(result.backendToken).toBeUndefined();
  });
});

describe('jwtCallback — backend token refresh', () => {
  it('refreshes backendToken when within 5 minutes of expiry', async () => {
    mockExchangeToken.mockResolvedValue(makeTokenSuccess('refreshed.jwt') as never);
    const expiresAt = Date.now() + 4 * 60 * 1000; // 4 min from now — inside threshold

    const result = await jwtCallback({
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

    const result = await jwtCallback({
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

    const result = await jwtCallback({
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
    const result = await jwtCallback({
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
    mockExchangeToken.mockResolvedValue({ success: false, data: null } as never);
    const expiresAt = Date.now() + 60 * 1000;

    const result = await jwtCallback({
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

describe('sessionCallback', () => {
  function makeSession(extra: Record<string, unknown> = {}) {
    return {
      user: { name: 'Test User', email: 'test@example.com', image: null, ...extra },
      expires: '2099-01-01T00:00:00.000Z',
    };
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

    const result = await sessionCallback({ session: makeSession() as never, token });

    expect(result.user.githubId).toBe('12345678');
    expect(result.user.githubLogin).toBe('student-dev');
    expect(result.user.githubAccessToken).toBe('gho_abc');
    expect(result.user.onboardingRequestId).toBe('onboarding-uuid');
    expect(result.user.correlationId).toBe('correlation-uuid');
  });

  it('propagates backendToken to session user', async () => {
    const token = { ...baseToken, backendToken: 'signed.jwt.value' };

    const result = await sessionCallback({ session: makeSession() as never, token });

    expect(result.user.backendToken).toBe('signed.jwt.value');
  });

  it('propagates registrationError flag to session user', async () => {
    const token = { ...baseToken, registrationError: true };

    const result = await sessionCallback({ session: makeSession() as never, token });

    expect(result.user.registrationError).toBe(true);
  });

  it('leaves onboarding fields undefined when token has none', async () => {
    const result = await sessionCallback({
      session: makeSession() as never,
      token: { ...baseToken },
    });

    expect(result.user.onboardingRequestId).toBeUndefined();
    expect(result.user.correlationId).toBeUndefined();
    expect(result.user.registrationError).toBeUndefined();
    expect(result.user.backendToken).toBeUndefined();
  });

  it('returns the session unchanged when session.user is falsy', async () => {
    const session = { expires: '2099-01-01T00:00:00.000Z' } as never;

    const result = await sessionCallback({ session, token: { ...baseToken } });

    expect(result).toEqual(session);
  });
});
