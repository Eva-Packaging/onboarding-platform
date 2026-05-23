package xyz.catuns.onboarding.user.service;

public class DuplicateRegistrationException extends RuntimeException {

    private final String githubUserId;

    public DuplicateRegistrationException(String githubUserId) {
        super("Active registration already exists for GitHub user: " + githubUserId);
        this.githubUserId = githubUserId;
    }

    public String getGithubUserId() {
        return githubUserId;
    }
}