import { jwtCallback, sessionCallback } from './callbacks';

const baseToken = { sub: 'user-123', iat: 0, exp: 0, jti: '' };

function makeGithubAccount(accessToken = 'gho_test') {
  return {
    provider: 'github',
    type: 'oauth' as const,
    providerAccountId: '12345678',
    access_token: accessToken,
  };
}

describe('jwtCallback', () => {
  it('maps github identity fields on github sign-in', async () => {
    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount('gho_abc'),
      profile: { id: 12345678, login: 'student-dev' } as never,
    });

    expect(result.githubId).toBe('12345678');
    expect(result.githubLogin).toBe('student-dev');
    expect(result.githubAccessToken).toBe('gho_abc');
  });

  it('converts numeric github id to string', async () => {
    const result = await jwtCallback({
      token: { ...baseToken },
      account: makeGithubAccount(),
      profile: { id: 99999, login: 'another-user' } as never,
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
    expect(result.githubLogin).toBeUndefined();
    expect(result.githubAccessToken).toBeUndefined();
  });

  it('does not modify token when account is null', async () => {
    const token = { ...baseToken, githubId: 'existing-id', githubLogin: 'existing-login' };

    const result = await jwtCallback({ token, account: null, profile: undefined });

    expect(result.githubId).toBe('existing-id');
    expect(result.githubLogin).toBe('existing-login');
  });
});

describe('sessionCallback', () => {
  function makeSession(extra: Record<string, unknown> = {}) {
    return {
      user: { name: 'Test User', email: 'test@example.com', image: null, ...extra },
      expires: '2099-01-01T00:00:00.000Z',
    };
  }

  it('propagates github fields from token to session user', async () => {
    const token = {
      ...baseToken,
      githubId: '12345678',
      githubLogin: 'student-dev',
      githubAccessToken: 'gho_abc',
    };

    const result = await sessionCallback({ session: makeSession() as never, token });

    expect(result.user.githubId).toBe('12345678');
    expect(result.user.githubLogin).toBe('student-dev');
    expect(result.user.githubAccessToken).toBe('gho_abc');
  });

  it('leaves github fields undefined when token has no github claims', async () => {
    const result = await sessionCallback({
      session: makeSession() as never,
      token: { ...baseToken },
    });

    expect(result.user.githubId).toBeUndefined();
    expect(result.user.githubLogin).toBeUndefined();
    expect(result.user.githubAccessToken).toBeUndefined();
  });

  it('returns the session unchanged when session.user is falsy', async () => {
    const session = { expires: '2099-01-01T00:00:00.000Z' } as never;
    const token = { ...baseToken, githubId: '12345678' };

    const result = await sessionCallback({ session, token });

    expect(result).toEqual(session);
  });
});