package xyz.catuns.onboarding.user.client;

import xyz.catuns.onboarding.user.api.dto.StepSummaryDto;

import java.util.List;
import java.util.UUID;

public record OnboardingInitResponse(UUID requestId, String state, List<StepSummaryDto> steps) {}