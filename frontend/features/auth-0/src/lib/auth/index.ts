import NextAuth from 'next-auth';
import Github from 'next-auth/providers/github';
import { GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET } from '../config/env';
import { authConfig } from './auth.config';
import { jwtCallback, sessionCallback } from './callbacks';

export const { auth, handlers, signIn, signOut } = NextAuth({
  ...authConfig,
  providers: [
    Github({
      clientId: GITHUB_CLIENT_ID || '',
      clientSecret: GITHUB_CLIENT_SECRET || '',
    }),
  ],
  callbacks: {
    jwt: jwtCallback,
    session: sessionCallback,
    async redirect({ url, baseUrl }) {
      if (url.startsWith('/')) return `${baseUrl}${url}`;
      else if (new URL(url).origin === baseUrl) return url;
      return baseUrl;
    },
  },
});
