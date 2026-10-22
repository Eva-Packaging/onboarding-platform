import { z } from 'zod';

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'production']),
  /* schema start */
  AUTH_API_URL: z.string(),
  AUTH_GITHUB_ID: z.string(),
  AUTH_GITHUB_SECRET: z.string(),
  /* schema end */
});

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace NodeJS {
    interface ProcessEnv extends z.infer<typeof envSchema> {}
  }
}

export const NODE_ENV = process.env.NODE_ENV;
/* vars start */
export const AUTH_API_URL = process.env.AUTH_API_URL;
export const AUTH_GITHUB_ID = process.env.AUTH_GITHUB_ID;
export const AUTH_GITHUB_SECRET = process.env.AUTH_GITHUB_SECRET;
/* vars end */
