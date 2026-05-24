package xyz.catuns.onboarding.service.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OnboardingRetryRequest(
    @NotEmpty List<String> steps,
    String reason
) {}
