package xyz.catuns.onboarding.provisioning.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import xyz.catuns.onboarding.provisioning.dto.ProviderHealthResponse;

import java.time.Instant;

@Component
public class GithubHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(GithubHealthChecker.class);

    private final RestClient githubRestClient;

    public GithubHealthChecker(RestClient githubRestClient) {
        this.githubRestClient = githubRestClient;
    }

    public ProviderHealthResponse check() {
        long start = System.currentTimeMillis();
        String checkedAt = Instant.now().toString();
        try {
            githubRestClient.get()
                    .uri("/rate_limit")
                    .retrieve()
                    .toBodilessEntity();
            long latencyMs = System.currentTimeMillis() - start;
            return new ProviderHealthResponse("GITHUB", "UP", latencyMs, checkedAt);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("GitHub health check failed: {}", e.getMessage());
            return new ProviderHealthResponse("GITHUB", "DOWN", latencyMs, checkedAt);
        }
    }
}
