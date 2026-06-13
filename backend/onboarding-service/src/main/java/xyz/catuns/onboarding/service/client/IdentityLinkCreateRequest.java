package xyz.catuns.onboarding.service.client;

import java.math.BigDecimal;
import java.util.UUID;

public record IdentityLinkCreateRequest(
    UUID userProfileId,
    UUID githubIdentityId,
    UUID atlassianIdentityId,
    MatchStrategy matchStrategy,
    BigDecimal confidenceScore
) {}