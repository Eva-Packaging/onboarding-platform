import type { NextAuthConfig } from 'next-auth';

type Callbacks = NonNullable<NextAuthConfig['callbacks']>;

export const jwt: Callbacks['jwt'] = async ({ token, user }) => {
  if (user) {
    token.role = user.role;
    token.jwtToken = user.jwtToken;
    token.refreshToken = user.refreshToken;
    token.expiration = user.expiration;
  }
  return token;
};

export const session: Callbacks['session'] = async ({ session, token }) => {
  if (token && session.user) {
    session.user.id = token.sub as string;
    session.user.role = token.role as string;
    session.user.jwtToken = token.jwtToken as string;
    session.user.refreshToken = token.refreshToken as string;
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
