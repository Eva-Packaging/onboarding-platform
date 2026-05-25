package xyz.catuns.onboarding.service.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OnboardingStatusResponse(
    UUID requestId,
    String userId,
    String state,
    UUID correlationId,
    Instant startedAt,
    List<StepDetailDto> steps
) {}