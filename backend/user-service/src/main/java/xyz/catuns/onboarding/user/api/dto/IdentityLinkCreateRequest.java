package xyz.catuns.onboarding.user.api.dto;

import jakarta.validation.constraints.NotNull;
import xyz.catuns.onboarding.user.domain.MatchStrategy;

import java.math.BigDecimal;
import java.util.UUID;

public record IdentityLinkCreateRequest(
    @NotNull UUID userProfileId,
    @NotNull UUID githubIdentityId,
    @NotNull UUID atlassianIdentityId,
    @NotNull MatchStrategy matchStrategy,
    BigDecimal confidenceScore
) {}