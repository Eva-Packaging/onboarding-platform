package xyz.catuns.onboarding.service.api.dto;

import java.util.UUID;

public record OnboardingLatestResponse(UUID requestId, String state) {}