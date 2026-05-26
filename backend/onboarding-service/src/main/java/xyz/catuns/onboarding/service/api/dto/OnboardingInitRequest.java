package xyz.catuns.onboarding.service.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;


public record OnboardingInitRequest(
    @NotNull String userId,
    @NotNull UUID correlationId,
    List<String> roleKeys
) {}