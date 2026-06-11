package xyz.catuns.onboarding.provisioning.atlassian;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
class AtlassianProvisioningAdapterTest {

    private RestClient.RequestHeadersUriSpec getUriSpec;
    private RestClient.ResponseSpec getResponseSpec;
    private RestClient.RequestBodySpec postBodySpec;
    private RestClient.ResponseSpec postResponseSpec;
    private AtlassianProvisioningAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient restClient = mock(RestClient.class);

        getUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        getResponseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(getUriSpec);
        when(getUriSpec.uri(anyString(), any(Object[].class))).thenReturn(getUriSpec);
        when(getUriSpec.retrieve()).thenReturn(getResponseSpec);

        RestClient.RequestBodyUriSpec postUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        postBodySpec = mock(RestClient.RequestBodySpec.class);
        postResponseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(postUriSpec);
        when(postUriSpec.uri(anyString(), any(Object[].class))).thenReturn(postBodySpec);
        when(postBodySpec.body(any(Object.class))).thenReturn(postBodySpec);
        when(postBodySpec.retrieve()).thenReturn(postResponseSpec);

        adapter = new AtlassianProvisioningAdapter(restClient);
    }

    @Test
    void addGroupMember_created_returnsActiveMembershipResult() {
        when(postResponseSpec.toBodilessEntity()).thenReturn(ResponseEntity.status(201).build());

        AtlassianGroupMembershipResult result = adapter.addGroupMember("account-123", null, "developers");

        assertThat(result.membershipState()).isEqualTo("ACTIVE");
        assertThat(result.success()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(result.accountId()).isEqualTo("account-123");
    }

    @Test
    void addGroupMember_alreadyAMember_returnsActiveMembershipResult() {
        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "{\"errorMessages\":[\"The user is already a member of the group.\"]}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(postResponseSpec.toBodilessEntity()).thenThrow(badRequest);

        AtlassianGroupMembershipResult result = adapter.addGroupMember("account-123", null, "developers");

        assertThat(result.membershipState()).isEqualTo("ACTIVE");
        assertThat(result.success()).isTrue();
        assertThat(result.errorCode()).isNull();
    }

    @Test
    void addGroupMember_notFound_returnsFailedWithGroupOrUserNotFound() {
        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        when(postResponseSpec.toBodilessEntity()).thenThrow(notFound);

        AtlassianGroupMembershipResult result = adapter.addGroupMember("account-123", null, "developers");

        assertThat(result.membershipState()).isEqualTo("FAILED");
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("GROUP_OR_USER_NOT_FOUND");
    }

    @Test
    void addGroupMember_rateLimited_throwsAtlassianRateLimitException() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "45");
        HttpClientErrorException tooManyRequests = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], StandardCharsets.UTF_8);
        when(postResponseSpec.toBodilessEntity()).thenThrow(tooManyRequests);

        assertThatThrownBy(() -> adapter.addGroupMember("account-123", null, "developers"))
                .isInstanceOf(AtlassianRateLimitException.class)
                .satisfies(ex ->
                        assertThat(((AtlassianRateLimitException) ex).getRetryAfterSeconds()).isEqualTo(45L));
    }

    @Test
    void addGroupMember_resolvesAccountByEmail_whenIdentityIdAbsent() {
        when(getResponseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of(Map.of("accountId", "resolved-account")));
        when(postResponseSpec.toBodilessEntity()).thenReturn(ResponseEntity.status(201).build());

        AtlassianGroupMembershipResult result = adapter.addGroupMember(null, "user@example.com", "developers");

        assertThat(result.accountId()).isEqualTo("resolved-account");
        assertThat(result.membershipState()).isEqualTo("ACTIVE");
    }

    @Test
    void addGroupMember_noMatchingAccount_returnsAccountNotFoundWithoutGroupCall() {
        when(getResponseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of());

        AtlassianGroupMembershipResult result = adapter.addGroupMember(null, "user@example.com", "developers");

        assertThat(result.membershipState()).isEqualTo("FAILED");
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(result.accountId()).isNull();
    }
}