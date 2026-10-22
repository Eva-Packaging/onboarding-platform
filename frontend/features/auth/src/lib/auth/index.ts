import NextAuth, { NextAuthResult } from 'next-auth';
import GitHub from 'next-auth/providers/github';
import { authConfig } from './auth.config';

const nextAuth: NextAuthResult = NextAuth({
  ...authConfig,
  providers: [
    // Zero-config OAuth providers — reads AUTH_<PROVIDER>_ID / AUTH_<PROVIDER>_SECRET
    // from the environment. Remove any providers you don't need.
    // GitHub returns `iss` on the callback (RFC 9207); without a matching issuer
    // Auth.js validates against its "https://authjs.dev" fallback and rejects it.
    GitHub({ issuer: 'https://github.com/login/oauth' }),
  ],
});

export const auth: NextAuthResult['auth'] = nextAuth.auth;
export const handlers: NextAuthResult['handlers'] = nextAuth.handlers;
export const signIn: NextAuthResult['signIn'] = nextAuth.signIn;
export const signOut: NextAuthResult['signOut'] = nextAuth.signOut;
