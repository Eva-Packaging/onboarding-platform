import { z } from 'zod';


export const getOnboardingStatusSchema = z.object({
  requestId: z.string().uuid(),
});