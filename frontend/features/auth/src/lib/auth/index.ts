import type { Session, User } from '../types/next-auth';
import NextAuth from 'next-auth';
import type { JWT } from 'next-auth/jwt';
import CredentialsProvider from 'next-auth/providers/credentials';
import Github from 'next-auth/providers/github';
import { authConfig } from './auth.config';
import { GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET } from "../config/env";

export const { auth, handlers, signIn, signOut } = NextAuth({
  ...authConfig,
  providers: [
    Github({
      clientId: GITHUB_CLIENT_ID || '',
      clientSecret: GITHUB_CLIENT_SECRET || '',
    }),
    CredentialsProvider({
      name: 'Credentials',
      credentials: {
        email: { label: 'Email', type: 'text' },
        password: { label: 'Password', type: 'password' },
      },
      async authorize(credentials) {
        return null;
      },
    }),
  ],
  callbacks: {
    async jwt({ token, user }) {
      if (user) {
        // token.role = user.role;
        // token.jwtToken = user.jwtToken;
        // token.refreshToken = user.refreshToken;
        // token.expiration = user.expiration;
      }
      return token;
    },
    async session({
      session,
      token,
    }: {
      session: Session;
      token: JWT;
      user: User;
    }) {
      if (token && session.user) {
        // session.user.id = token.sub as string;
        // session.user.role = token.role as string;
        // session.user.jwtToken = token.jwtToken as string;
        // session.user.refreshToken = token.refreshToken as string;
      }
      return session;
    },

    async redirect({ url, baseUrl }) {
      // Allows relative callback URLs
      if (url.startsWith('/')) return `${baseUrl}${url}`;
      // Allows callback URLs on the same origin
      else if (new URL(url).origin === baseUrl) return url;
      return baseUrl;
    },
  },
});
