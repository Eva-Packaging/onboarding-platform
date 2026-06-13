package xyz.catuns.onboarding.user.api.dto;

import java.util.UUID;

public record ExternalIdentityLookupResponse(UUID id, String accountId, String email, String matchState) {}