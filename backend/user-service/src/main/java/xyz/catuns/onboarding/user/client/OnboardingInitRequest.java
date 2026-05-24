package xyz.catuns.onboarding.user.client;

import java.util.List;
import java.util.UUID;

record OnboardingInitRequest(UUID userId, UUID correlationId, List<String> roleKeys) {}