import type { DefaultSession, DefaultUser } from 'next-auth';

declare module 'next-auth' {
  interface Session extends DefaultSession {
    user: User;
  }

  interface User extends DefaultUser {
    githubId?: string;
    githubLogin?: string;
    githubAccessToken?: string;
    onboardingRequestId?: string;
    correlationId?: string;
    registrationError?: boolean;
    backendToken?: string;
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    githubAccessToken?: string;
    githubLogin?: string;
    githubId?: string;
    onboardingRequestId?: string;
    correlationId?: string;
    registrationError?: boolean;
    backendToken?: string;
    backendTokenExpiresAt?: number;
  }
}

export type { User, Session, JWT };
