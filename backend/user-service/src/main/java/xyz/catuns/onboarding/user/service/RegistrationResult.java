package xyz.catuns.onboarding.user.service;

import java.util.UUID;

public record RegistrationResult(UUID userId, UUID correlationId) {}