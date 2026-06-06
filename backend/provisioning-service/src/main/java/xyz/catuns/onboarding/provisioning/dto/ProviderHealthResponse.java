package xyz.catuns.onboarding.provisioning.dto;

public record ProviderHealthResponse(
        String provider,
        String status,
        long latencyMs,
        String checkedAt
) {
}
