/* eslint-disable @typescript-eslint/no-empty-object-type */
/* eslint-disable @typescript-eslint/no-empty-interface */
import { z } from 'zod';

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'production']),
  AUTH_API_URL: z.string().url(),
});

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace NodeJS {
    interface ProcessEnv extends z.infer<typeof envSchema> {
      GITHUB_CLIENT_ID: string;
      GITHUB_CLIENT_SECRET: string;
    }
  }
}

export const NODE_ENV = process.env.NODE_ENV;
export const BACKEND_API_URL = process.env.AUTH_API_URL;
export const GITHUB_CLIENT_ID = process.env.GITHUB_CLIENT_ID;
export const GITHUB_CLIENT_SECRET = process.env.GITHUB_CLIENT_SECRET;
