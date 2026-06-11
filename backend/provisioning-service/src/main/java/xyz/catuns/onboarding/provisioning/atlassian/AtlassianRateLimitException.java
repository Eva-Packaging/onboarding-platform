package xyz.catuns.onboarding.provisioning.atlassian;

public class AtlassianRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public AtlassianRateLimitException(long retryAfterSeconds) {
        super("Atlassian rate limit exceeded; retry after " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}