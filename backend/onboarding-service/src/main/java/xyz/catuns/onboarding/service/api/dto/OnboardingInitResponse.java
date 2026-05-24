package xyz.catuns.onboarding.service.api.dto;

import java.util.List;
import java.util.UUID;

public record OnboardingInitResponse(UUID requestId, String state, List<StepSummaryDto> steps) {}