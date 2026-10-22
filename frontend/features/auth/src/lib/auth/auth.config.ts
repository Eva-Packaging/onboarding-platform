import type { NextAuthConfig } from 'next-auth';
import { authorized, jwt, redirect, session } from './callbacks';

const VERCEL_DEPLOYMENT = !!process.env.VERCEL_URL;

// Edge-safe config (no providers) — usable in middleware for route protection.
export const authConfig: NextAuthConfig = {
  pages: {
    signIn: `/register`,
    verifyRequest: `/auth`,
    error: '/auth', // Error code passed in query string as ?error=
    newUser: '/onboarding',
  },
  callbacks: {
    authorized,
    jwt,
    session,
    redirect,
  },
  session: { strategy: 'jwt' },
  cookies: {
    csrfToken: {
      name: 'authjs.csrf-token',
      options: {
        httpOnly: true,
        sameSite: 'lax',
        path: '/',
        secure: VERCEL_DEPLOYMENT,
      },
    },
  },
  providers: [],
};
