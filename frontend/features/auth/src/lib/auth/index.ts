import NextAuth, { NextAuthResult } from 'next-auth';
import CredentialsProvider from 'next-auth/providers/credentials';
import Discord from 'next-auth/providers/discord';
import Facebook from 'next-auth/providers/facebook';
import GitHub from 'next-auth/providers/github';
import Google from 'next-auth/providers/google';
import { authConfig } from './auth.config';

const nextAuth: NextAuthResult = NextAuth({
  ...authConfig,
  providers: [
    // Zero-config OAuth providers — reads AUTH_<PROVIDER>_ID / AUTH_<PROVIDER>_SECRET
    // from the environment. Remove any providers you don't need.
    GitHub,
    Google,
    Discord,
    Facebook,
    CredentialsProvider({
      name: 'Credentials',
      credentials: {
        email: { label: 'Email', type: 'text' },
        password: { label: 'Password', type: 'password' },
      },
      async authorize(credentials) {
        // TODO: verify credentials against your user store and return a User, or null
        return null;
      },
    }),
  ],
});

export const auth: NextAuthResult['auth'] = nextAuth.auth;
export const handlers: NextAuthResult['handlers'] = nextAuth.handlers;
export const signIn: NextAuthResult['signIn'] = nextAuth.signIn;
export const signOut: NextAuthResult['signOut'] = nextAuth.signOut;