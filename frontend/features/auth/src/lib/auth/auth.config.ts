import type { NextAuthConfig } from 'next-auth';

const VERCEL_DEPLOYMENT = !!process.env.VERCEL_URL;

export const authConfig: NextAuthConfig = {
  pages: {
    signIn: `/auth`,
    verifyRequest: `/auth`,
    error: '/auth', // Error code passed in query string as ?error=
    newUser: '/onboarding',
  },
  callbacks: {
    authorized({ auth, request: { nextUrl } }) {
      return !!auth?.user;
    },
  },
  session: { strategy: 'jwt' },
  cookies: {
    csrfToken: {
      name: 'authjs.csrf-token',
      options: {
        httpOnly: true,
        sameSite: 'lax',
        path: '/',
        // When working on localhost, the cookie domain must be omitted entirely (https://stackoverflow.com/a/1188145)
        domain: VERCEL_DEPLOYMENT
          ? `.${process.env.NEXT_PUBLIC_ROOT_DOMAIN}`
          : undefined,
        secure: VERCEL_DEPLOYMENT,
      },
    },
  },
  providers: [],
} satisfies NextAuthConfig;
