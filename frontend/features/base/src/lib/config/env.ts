/* eslint-disable @typescript-eslint/no-empty-object-type */
/* eslint-disable @typescript-eslint/no-empty-interface */
import { z } from 'zod';

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'production']),
  BASE_API_URL: z.string().url(),
});

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace NodeJS {
    interface ProcessEnv extends z.infer<typeof envSchema> {}
  }
}

export const NODE_ENV = process.env.NODE_ENV;
export const BACKEND_API_URL = process.env.BASE_API_URL;
