package xyz.catuns.onboarding.provisioning.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class GithubProvisioningAdapter {

    private static final Logger log = LoggerFactory.getLogger(GithubProvisioningAdapter.class);

    private final RestClient githubRestClient;

    public GithubProvisioningAdapter(RestClient githubRestClient) {
        this.githubRestClient = githubRestClient;
    }

    public GithubMembershipResult addTeamMember(String githubLogin, String org, String teamSlug) {
        log.info("Adding GitHub team member login={} org={} team={}", githubLogin, org, teamSlug);
        try {
            Map<String, Object> body = githubRestClient.put()
                    .uri("/orgs/{org}/teams/{slug}/memberships/{login}", org, teamSlug, githubLogin)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            String state = body != null ? (String) body.get("state") : null;
            if ("active".equals(state)) {
                return new GithubMembershipResult("ACTIVE", true, null, null);
            }
            return new GithubMembershipResult("PENDING", true, null, null);

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                log.warn("GitHub team or user not found login={} org={} team={}", githubLogin, org, teamSlug);
                return new GithubMembershipResult("FAILED", false, "USER_OR_TEAM_NOT_FOUND", e.getMessage());
            }
            if (status == 403) {
                String retryAfter = e.getResponseHeaders() != null
                        ? e.getResponseHeaders().getFirst("Retry-After") : null;
                if (retryAfter != null) {
                    long seconds = Long.parseLong(retryAfter);
                    log.warn("GitHub rate limit hit; retry after {}s login={}", seconds, githubLogin);
                    throw new GithubRateLimitException(seconds);
                }
            }
            log.error("GitHub client error status={} login={} org={} team={}", status, githubLogin, org, teamSlug);
            return new GithubMembershipResult("FAILED", false, String.valueOf(status), e.getMessage());

        } catch (HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            log.error("GitHub server error status={} login={} org={} team={}", status, githubLogin, org, teamSlug);
            return new GithubMembershipResult("FAILED", false, String.valueOf(status), e.getMessage());

        } catch (RestClientException e) {
            log.error("GitHub network error login={} org={} team={}", githubLogin, org, teamSlug, e);
            return new GithubMembershipResult("FAILED", false, "NETWORK_ERROR", e.getMessage());
        }
    }
}