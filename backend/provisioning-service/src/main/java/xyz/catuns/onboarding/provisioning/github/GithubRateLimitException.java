package xyz.catuns.onboarding.provisioning.github;

public class GithubRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public GithubRateLimitException(long retryAfterSeconds) {
        super("GitHub rate limit exceeded; retry after " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
