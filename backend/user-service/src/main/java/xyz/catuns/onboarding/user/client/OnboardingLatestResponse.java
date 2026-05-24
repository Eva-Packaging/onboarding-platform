package xyz.catuns.onboarding.user.client;

import java.util.UUID;

public record OnboardingLatestResponse(UUID requestId, String state) {}