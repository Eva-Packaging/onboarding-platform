import type { DefaultSession, DefaultUser, Session, User } from 'next-auth';
import type { DefaultJWT, JWT } from 'next-auth/jwt';

declare module 'next-auth' {
  interface Session extends DefaultSession {
    user: User;
  }

  interface User extends DefaultUser {
    id: string;
    // Replace with a union of your application's role names, e.g. 'admin' | 'user'
    role: string;
    jwtToken?: string;
    refreshToken?: string;
    expiration?: number;
  }
}

declare module 'next-auth/jwt' {
  interface JWT extends DefaultJWT {
    role: string;
    jwtToken: string;
    expiration: number;
    refreshToken: string;
  }
}

export type { JWT, Session, User };
