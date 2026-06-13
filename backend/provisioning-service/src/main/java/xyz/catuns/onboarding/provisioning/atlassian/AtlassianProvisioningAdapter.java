package xyz.catuns.onboarding.provisioning.atlassian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AtlassianProvisioningAdapter {

    private static final Logger log = LoggerFactory.getLogger(AtlassianProvisioningAdapter.class);

    private final RestClient atlassianRestClient;

    public AtlassianProvisioningAdapter(RestClient atlassianRestClient) {
        this.atlassianRestClient = atlassianRestClient;
    }

    public AtlassianGroupMembershipResult addGroupMember(String atlassianIdentityId, String atlassianEmail, String groupName) {
        String accountId = (atlassianIdentityId != null && !atlassianIdentityId.isBlank())
                ? atlassianIdentityId
                : resolveAccountId(atlassianEmail).orElse(null);

        if (accountId == null) {
            log.warn("No Atlassian account resolved for email={}", atlassianEmail);
            return new AtlassianGroupMembershipResult("FAILED", false, "ACCOUNT_NOT_FOUND",
                    "No Atlassian account found for email " + atlassianEmail, null);
        }

        log.info("Adding Atlassian group member accountId={} group={}", accountId, groupName);
        try {
            atlassianRestClient.post()
                    .uri("/rest/api/3/group/user?groupName={groupName}", groupName)
                    .body(Map.of("accountId", accountId))
                    .retrieve()
                    .toBodilessEntity();

            return new AtlassianGroupMembershipResult("ACTIVE", true, null, null, accountId);

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 400 && isAlreadyMember(e)) {
                log.info("Account accountId={} is already a member of group={}", accountId, groupName);
                return new AtlassianGroupMembershipResult("ACTIVE", true, null, null, accountId);
            }
            if (status == 404) {
                log.warn("Atlassian group or user not found accountId={} group={}", accountId, groupName);
                return new AtlassianGroupMembershipResult("FAILED", false, "GROUP_OR_USER_NOT_FOUND", e.getMessage(), accountId);
            }
            if (status == 429) {
                String retryAfter = e.getResponseHeaders() != null
                        ? e.getResponseHeaders().getFirst("Retry-After") : null;
                if (retryAfter != null) {
                    long seconds = Long.parseLong(retryAfter);
                    log.warn("Atlassian rate limit hit; retry after {}s accountId={}", seconds, accountId);
                    throw new AtlassianRateLimitException(seconds);
                }
            }
            log.error("Atlassian client error status={} accountId={} group={}", status, accountId, groupName);
            return new AtlassianGroupMembershipResult("FAILED", false, String.valueOf(status), e.getMessage(), accountId);

        } catch (HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            log.error("Atlassian server error status={} accountId={} group={}", status, accountId, groupName);
            return new AtlassianGroupMembershipResult("FAILED", false, String.valueOf(status), e.getMessage(), accountId);

        } catch (RestClientException e) {
            log.error("Atlassian network error accountId={} group={}", accountId, groupName, e);
            return new AtlassianGroupMembershipResult("FAILED", false, "NETWORK_ERROR", e.getMessage(), accountId);
        }
    }

    private Optional<String> resolveAccountId(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        List<Map<String, Object>> results = atlassianRestClient.get()
                .uri("/rest/api/3/user/search?query={email}", email)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }

        Object accountId = results.get(0).get("accountId");
        return Optional.ofNullable(accountId).map(Object::toString);
    }

    private boolean isAlreadyMember(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        return body != null && body.toLowerCase().contains("already a member");
    }
}