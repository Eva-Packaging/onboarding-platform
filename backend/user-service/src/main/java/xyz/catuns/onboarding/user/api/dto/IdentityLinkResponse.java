package xyz.catuns.onboarding.user.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IdentityLinkResponse(
    UUID id,
    UUID userProfileId,
    UUID githubIdentityId,
    UUID atlassianIdentityId,
    String matchStrategy,
    BigDecimal confidenceScore,
    Instant createdAt
) {}