import type { NextAuthConfig } from 'next-auth';
import type { GitHubProfile } from "next-auth/providers/github";
import { REFRESH_THRESHOLD_MS } from '../constants';
import { exchangeToken, registerUser } from "@feature/onboarding/server";
import type { JWT } from "next-auth/jwt";

type Callbacks = NonNullable<NextAuthConfig['callbacks']>;

export const jwt: Callbacks['jwt'] = async ({ token, account, profile }) => {
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
    token = await refreshBackendToken(token);
    return token;
  }

  // On subsequent session reads, proactively refresh before expiry
  if (
    token.githubAccessToken &&
    token.backendTokenExpiresAt &&
    Date.now() > token.backendTokenExpiresAt - REFRESH_THRESHOLD_MS
  ) {
    token = await refreshBackendToken(token);
  }
  return token;
};
async function refreshBackendToken(token: JWT): Promise<JWT> {
  if (!token.githubAccessToken) return;

  const result = await exchangeToken(token.githubAccessToken);
  if (result.success && result.data) {
    token.backendToken = result.data.token;
    token.backendTokenExpiresAt = Date.now() + result.data.expiresIn * 1000;
  }
  return token;
}

export const session: Callbacks['session'] = async ({ session, token }) => {
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
};

export const redirect: Callbacks['redirect'] = async ({ url, baseUrl }) => {
  // Allows relative callback URLs
  if (url.startsWith('/')) return `${baseUrl}${url}`;
  // Allows callback URLs on the same origin
  if (new URL(url).origin === baseUrl) return url;
  return baseUrl;
};

export const authorized: Callbacks['authorized'] = ({ auth }) => {
  return !!auth?.user;
};
