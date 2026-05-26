package xyz.catuns.onboarding.user.service;

import java.util.UUID;

public record RegistrationResult(UUID userId, UUID correlationId, boolean existingUser) {
    public RegistrationResult(UUID userId, UUID correlationId) {
        this(userId, correlationId, false);
    }
}