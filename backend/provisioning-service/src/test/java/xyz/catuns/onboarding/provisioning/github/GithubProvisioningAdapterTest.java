package xyz.catuns.onboarding.provisioning.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class GithubProvisioningAdapterTest {

    private RestClient.ResponseSpec responseSpec;
    private GithubProvisioningAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.put()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(), any(), any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        adapter = new GithubProvisioningAdapter(restClient);
    }

    @Test
    void addTeamMember_activeState_returnsActiveMembershipResult() {
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("state", "active"));

        GithubMembershipResult result = adapter.addTeamMember("octocat", "evaitcs", "students");

        assertThat(result.membershipState()).isEqualTo("ACTIVE");
        assertThat(result.success()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void addTeamMember_pendingState_returnsPendingMembershipResult() {
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(Map.of("state", "pending"));

        GithubMembershipResult result = adapter.addTeamMember("octocat", "evaitcs", "students");

        assertThat(result.membershipState()).isEqualTo("PENDING");
        assertThat(result.success()).isTrue();
        assertThat(result.errorCode()).isNull();
    }

    @Test
    void addTeamMember_notFound_returnsFailedWithUserOrTeamNotFound() {
        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenThrow(notFound);

        GithubMembershipResult result = adapter.addTeamMember("octocat", "evaitcs", "students");

        assertThat(result.membershipState()).isEqualTo("FAILED");
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("USER_OR_TEAM_NOT_FOUND");
    }

    @Test
    void addTeamMember_forbiddenWithRetryAfterHeader_throwsGithubRateLimitException() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "30");
        HttpClientErrorException rateLimited = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", headers, new byte[0], StandardCharsets.UTF_8);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenThrow(rateLimited);

        assertThatThrownBy(() -> adapter.addTeamMember("octocat", "evaitcs", "students"))
                .isInstanceOf(GithubRateLimitException.class)
                .satisfies(ex ->
                        assertThat(((GithubRateLimitException) ex).getRetryAfterSeconds()).isEqualTo(30L));
    }
}