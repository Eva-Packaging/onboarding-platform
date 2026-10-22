import NextAuth, { NextAuthResult } from 'next-auth';
import GitHub from 'next-auth/providers/github';
import { authConfig } from './auth.config';

const nextAuth: NextAuthResult = NextAuth({
  ...authConfig,
  providers: [
    // Zero-config OAuth providers — reads AUTH_<PROVIDER>_ID / AUTH_<PROVIDER>_SECRET
    // from the environment. Remove any providers you don't need.
    GitHub
  ],
});

export const auth: NextAuthResult['auth'] = nextAuth.auth;
export const handlers: NextAuthResult['handlers'] = nextAuth.handlers;
export const signIn: NextAuthResult['signIn'] = nextAuth.signIn;
export const signOut: NextAuthResult['signOut'] = nextAuth.signOut;
