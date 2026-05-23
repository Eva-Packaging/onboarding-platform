import type { DefaultSession, DefaultUser } from 'next-auth';

declare module 'next-auth' {
  interface Session extends DefaultSession {
    user: User;
  }

  interface User extends DefaultUser {
    id: string;
    role: Role;
    jwtToken?: string;
    refreshToken?: string;
    expiration?: number;
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    jwtToken: string;
    expiration: number;
    refreshToken: string;
  }
}

export type { User, Session, JWT };
