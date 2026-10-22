import type { DefaultSession, DefaultUser, Session, User } from 'next-auth';
import type { DefaultJWT, JWT } from 'next-auth/jwt';

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
  interface JWT extends DefaultJWT {
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

export type { JWT, Session, User };
