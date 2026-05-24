package xyz.catuns.onboarding.service.api.dto;

import java.util.List;
import java.util.UUID;

public record OnboardingRetryResponse(
    UUID requestId,
    String state,
    List<String> requeuedSteps,
    UUID correlationId
) {}
