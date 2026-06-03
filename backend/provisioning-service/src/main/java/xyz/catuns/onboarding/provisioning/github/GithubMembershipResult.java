package xyz.catuns.onboarding.provisioning.github;

public record GithubMembershipResult(
        String membershipState,
        boolean success,
        String errorCode,
        String errorMessage
) {}
