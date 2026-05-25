import type { Account, Profile, User } from 'next-auth';
import type { JWT } from 'next-auth/jwt';
import type { GitHubProfile } from 'next-auth/providers/github';
import { registerUser } from '../actions/registration';
import { exchangeToken } from '../actions/token';
import type { Session } from '../types/next-auth';

const REFRESH_THRESHOLD_MS = 5 * 60 * 1000;

export async function jwtCallback({
  token,
  account,
  profile,
}: {
  user: User;
  token: JWT;
  account: Account | null;
  profile?: Profile;
}): Promise<JWT> {
  if (account?.provider === 'github' && profile) {
    const gh = profile as unknown as GitHubProfile;
    token.githubId = String(gh.id);
    token.githubLogin = gh.login;
    token.githubAccessToken = account.access_token;

    if (!token.onboardingRequestId) {
      try {
        const result = await registerUser({
          githubUserId: token.githubId,
          githubLogin: token.githubLogin,
          primaryEmail: gh.email ?? undefined,
          displayName: gh.name ?? gh.login,
          avatarUrl: gh.avatar_url,
        });
        if (result.success && result.data) {
          token.onboardingRequestId = result.data.onboardingRequestId;
          token.correlationId = result.data.correlationId;
        } else {
          token.registrationError = true;
        }
      } catch {
        token.registrationError = true;
      }
    }

    // Issue backend JWT immediately after GitHub OAuth completes
    await refreshBackendToken(token);
    return token;
  }

  // On subsequent session reads, proactively refresh before expiry
  if (
    token.githubAccessToken &&
    token.backendTokenExpiresAt &&
    Date.now() > token.backendTokenExpiresAt - REFRESH_THRESHOLD_MS
  ) {
    await refreshBackendToken(token);
  }

  return token;
}

async function refreshBackendToken(token: JWT): Promise<void> {
  if (!token.githubAccessToken) return;

  const result = await exchangeToken(token.githubAccessToken);
  if (result.success && result.data) {
    token.backendToken = result.data.token;
    token.backendTokenExpiresAt = Date.now() + result.data.expiresIn * 1000;
  }
}

export async function sessionCallback({
  session,
  token,
}: {
  session: Session;
  token: JWT;
}): Promise<Session> {
  if (session.user) {
    session.user.githubId = token.githubId;
    session.user.githubLogin = token.githubLogin;
    session.user.githubAccessToken = token.githubAccessToken;
    session.user.onboardingRequestId = token.onboardingRequestId;
    session.user.correlationId = token.correlationId;
    session.user.registrationError = token.registrationError;
    session.user.backendToken = token.backendToken;
  }
  return session;
}
