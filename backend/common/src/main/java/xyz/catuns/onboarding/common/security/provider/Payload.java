package xyz.catuns.onboarding.common.security.provider;

public record Payload(
        String userId,
        String correlationId,
        boolean isAdmin
) {
}
