import { jwtCallback, sessionCallback } from './callbacks';

jest.mock('../actions/registration', () => ({
  registerUser: jest.fn(),
}));

import { registerUser } from '../actions/registration';

const mockRegisterUser = registerUser as jest.MockedFunction<typeof registerUser>;

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

beforeEach(() => {
  mockRegisterUser.mockReset();
});

describe('jwtCallback — github identity mapping', () => {
  it('maps github identity fields on sign-in', async () => {
    mockRegisterUser.mockResolvedValue(makeRegistrationSuccess() as never);

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

    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: makeGithubProfile() as never,
    });

    expect(result.registrationError).toBe(true);
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
  });

  it('returns the session unchanged when session.user is falsy', async () => {
    const session = { expires: '2099-01-01T00:00:00.000Z' } as never;

    const result = await sessionCallback({ session, token: { ...baseToken } });

    expect(result).toEqual(session);
  });
});