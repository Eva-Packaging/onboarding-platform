import { z } from 'zod';

/**
 * [register-user]
 * next-feature@0.1.4-beta.1
 * October 21st 2026, 4:10:27 pm
 */
export const registerUserSchema = z.object({
  githubUserId: z.string().min(1),
  githubLogin: z.string().min(1),
  primaryEmail: z.string().email().optional(),
  displayName: z.string().min(1),
  avatarUrl: z.string().url().optional(),
  roleKeys: z.array(z.string()).optional(),
});
export type RegisterUserRequest = z.infer<typeof registerUserSchema>;
export type RegisterUserResponse = {
  userId: string;
  onboardingRequestId: string;
  status: string;
  correlationId: string;
  steps: StepSummary[];
};
export interface StepSummary {
  type: string;
  state: string;
}

/**
 * [exchange-token]
 * next-feature@0.1.4-beta.1
 * October 21st 2026, 6:55:11 pm
 */
export type ExchangeTokenResponse = {
  token: string;
  expiresIn: number;
};


