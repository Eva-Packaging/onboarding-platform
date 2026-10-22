import { JWT } from "next-auth/jwt";
import { exchangeToken } from "@feature/onboarding/server";

/**
 * [refresh-backend-token]
 * next-feature@0.1.4-beta.1
 * October 21st 2026, 7:20:44 pm
 */
export async function refreshBackendToken(token: JWT): Promise<void> {
  if (!token.githubAccessToken) return;

  const result = await exchangeToken(token.githubAccessToken);
  if (result.success && result.data) {
    token.backendToken = result.data.token;
    token.backendTokenExpiresAt = Date.now() + result.data.expiresIn * 1000;
  }
}
