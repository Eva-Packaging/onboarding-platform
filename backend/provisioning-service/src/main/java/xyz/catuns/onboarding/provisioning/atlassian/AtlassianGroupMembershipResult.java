package xyz.catuns.onboarding.provisioning.atlassian;

public record AtlassianGroupMembershipResult(
        String membershipState,
        boolean success,
        String errorCode,
        String errorMessage,
        String accountId
) {}