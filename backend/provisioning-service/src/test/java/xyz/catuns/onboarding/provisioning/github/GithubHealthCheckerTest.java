package xyz.catuns.onboarding.provisioning.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import xyz.catuns.onboarding.provisioning.dto.ProviderHealthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class GithubHealthCheckerTest {

    private RestClient.ResponseSpec responseSpec;
    private GithubHealthChecker checker;

    @BeforeEach
    void setUp() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString());
        doReturn(responseSpec).when(headersSpec).retrieve();

        checker = new GithubHealthChecker(restClient);
    }

    @Test
    void check_githubReachable_returnsUpWithNonNegativeLatency() {
        doReturn(org.springframework.http.ResponseEntity.ok().build()).when(responseSpec).toBodilessEntity();

        ProviderHealthResponse result = checker.check();

        assertThat(result.provider()).isEqualTo("GITHUB");
        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.checkedAt()).isNotBlank();
    }

    @Test
    void check_githubUnreachable_returnsDownWithoutThrowing() {
        doThrow(new ResourceAccessException("Connection refused")).when(responseSpec).toBodilessEntity();

        ProviderHealthResponse result = checker.check();

        assertThat(result.provider()).isEqualTo("GITHUB");
        assertThat(result.status()).isEqualTo("DOWN");
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.checkedAt()).isNotBlank();
    }
}
