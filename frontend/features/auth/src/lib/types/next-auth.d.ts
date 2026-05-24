import type { DefaultSession, DefaultUser } from 'next-auth';

declare module 'next-auth' {
  interface Session extends DefaultSession {
    user: User;
  }

  interface User extends DefaultUser {
    githubId?: string;
    githubLogin?: string;
    githubAccessToken?: string;
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    // jwtToken: string;
    // expiration: number;
    // refreshToken: string;
    githubAccessToken?: string;
    githubLogin?: string;
    githubId?: string; 
  }
}

export type { User, Session, JWT };
