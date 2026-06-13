package xyz.catuns.onboarding.service.client;

import java.util.UUID;

public record ExternalIdentityLookupResponse(UUID id, String accountId, String email, String matchState) {}