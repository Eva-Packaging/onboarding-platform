package xyz.catuns.onboarding.provisioning.atlassian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import xyz.catuns.onboarding.provisioning.dto.ProviderHealthResponse;

import java.time.Instant;

@Component
public class AtlassianHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(AtlassianHealthChecker.class);

    private final RestClient atlassianRestClient;

    public AtlassianHealthChecker(RestClient atlassianRestClient) {
        this.atlassianRestClient = atlassianRestClient;
    }

    public ProviderHealthResponse check() {
        long start = System.currentTimeMillis();
        String checkedAt = Instant.now().toString();
        try {
            atlassianRestClient.get()
                    .uri("/rest/api/3/myself")
                    .retrieve()
                    .toBodilessEntity();
            long latencyMs = System.currentTimeMillis() - start;
            return new ProviderHealthResponse("ATLASSIAN", "UP", latencyMs, checkedAt);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Atlassian health check failed: {}", e.getMessage());
            return new ProviderHealthResponse("ATLASSIAN", "DOWN", latencyMs, checkedAt);
        }
    }
}