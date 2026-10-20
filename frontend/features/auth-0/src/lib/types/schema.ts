import { z } from 'zod';

/**
 * [register-user-schema]
 * next-feature@0.1.3-2
 * May 23rd 2026, 11:37:55 pm
 */
export const registerUserSchema = z.object({
  githubUserId: z.string().min(1),
  githubLogin: z.string().min(1),
  primaryEmail: z.string().email().optional(),
  displayName: z.string().min(1),
  avatarUrl: z.string().url().optional(),
  roleKeys: z.array(z.string()).optional(),
});
