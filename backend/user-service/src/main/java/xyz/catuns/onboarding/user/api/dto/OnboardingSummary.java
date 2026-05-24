package xyz.catuns.onboarding.user.api.dto;

import java.util.UUID;

public record OnboardingSummary(UUID requestId, String state) {}