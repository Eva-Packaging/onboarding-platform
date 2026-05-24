package xyz.catuns.onboarding.apigateway.provider;

import java.util.UUID;

public record Payload(
        String userId,
        String correlationId
) {
}
