import type { Account, Profile } from 'next-auth';
import type { JWT } from 'next-auth/jwt';
import type { GitHubProfile } from 'next-auth/providers/github';
import type { Session } from '../types/next-auth';

export async function jwtCallback({
  token,
  account,
  profile,
}: {
  token: JWT;
  account: Account | null;
  profile?: Profile;
}): Promise<JWT> {
  if (account?.provider === 'github' && profile) {
    const gh = profile as unknown as GitHubProfile;
    token.githubId = String(gh.id);
    token.githubLogin = gh.login;
    token.githubAccessToken = account.access_token;
  }
  return token;
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
  }
  return session;
}