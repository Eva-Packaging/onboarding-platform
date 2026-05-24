/* eslint-disable @typescript-eslint/no-empty-object-type */
/* eslint-disable @typescript-eslint/no-empty-interface */
import { z } from 'zod';

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'production']),
  BASE_API_URL: z.string().url(),
  AUTH_GITHUB_ID: z.string(),
  AUTH_GITHUB_SECRET: z.string()
});

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace NodeJS {
    interface ProcessEnv extends z.infer<typeof envSchema> {
    }
  }
}

export const NODE_ENV = process.env.NODE_ENV;
export const BACKEND_API_URL = process.env.BASE_API_URL;
export const GITHUB_CLIENT_ID = process.env.AUTH_GITHUB_ID;
export const GITHUB_CLIENT_SECRET = process.env.AUTH_GITHUB_SECRET;
