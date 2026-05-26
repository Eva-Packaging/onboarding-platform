package xyz.catuns.onboarding.user.service;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.catuns.onboarding.common.security.provider.Payload;
import xyz.catuns.onboarding.common.security.provider.PayloadTokenProvider;
import xyz.catuns.onboarding.user.api.dto.TokenExchangeRequest;
import xyz.catuns.onboarding.user.api.dto.TokenResponse;
import xyz.catuns.onboarding.user.client.GitHubApiClient;
import xyz.catuns.onboarding.user.client.GitHubUserResponse;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.ProviderKey;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.exception.GitHubAuthenticationException;
import xyz.catuns.onboarding.user.repository.ExternalIdentityRepository;
import xyz.catuns.spring.jwt.core.model.JwtToken;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenExchangeServiceTest {

    private static final UUID PROFILE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private GitHubApiClient gitHubApiClient;
    private ExternalIdentityRepository identityRepository;
    private PayloadTokenProvider tokenProvider;
    private TokenExchangeService service;

    @BeforeEach
    void setUp() {
        gitHubApiClient = mock(GitHubApiClient.class);
        identityRepository = mock(ExternalIdentityRepository.class);
        tokenProvider = mock(PayloadTokenProvider.class);
        service = new TokenExchangeService(gitHubApiClient, identityRepository, tokenProvider);
    }

    @Test
    void exchange_validToken_returnsSignedJwtWithExpiry() {
        Instant expiry = Instant.now().plusSeconds(86400);
        stubGitHubSuccess(12345678L, "student-dev");
        stubIdentityFound();
        when(tokenProvider.generate(any())).thenReturn(new JwtToken("signed.jwt.value", expiry, Instant.now()));

        TokenResponse response = service.exchange(request("gho_valid"));

        assertThat(response.getToken()).isEqualTo("signed.jwt.value");
        assertThat(response.getExpiresIn()).isGreaterThan(86300).isLessThanOrEqualTo(86400);
    }

    @Test
    void exchange_usesInternalProfileIdAsJwtSubject() {
        stubGitHubSuccess(99999L, "another-user");
        stubIdentityFound();
        when(tokenProvider.generate(any())).thenReturn(jwt());

        service.exchange(request("gho_token"));

        verify(tokenProvider).generate(argThat(p -> PROFILE_ID.toString().equals(p.userId())));
    }

    @Test
    void exchange_generatesRandomCorrelationId() {
        stubGitHubSuccess(12345678L, "student-dev");
        stubIdentityFound();
        when(tokenProvider.generate(any())).thenReturn(jwt());

        service.exchange(request("gho_token"));

        verify(tokenProvider).generate(argThat(p -> p.correlationId() != null && !p.correlationId().isBlank()));
    }

    @Test
    void exchange_invalidGitHubToken_throwsGitHubAuthenticationException() {
        when(gitHubApiClient.getUser(any())).thenThrow(feignException(401));

        assertThatThrownBy(() -> service.exchange(request("gho_bad")))
                .isInstanceOf(GitHubAuthenticationException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void exchange_forbiddenGitHubToken_throwsGitHubAuthenticationException() {
        when(gitHubApiClient.getUser(any())).thenThrow(feignException(403));

        assertThatThrownBy(() -> service.exchange(request("gho_revoked")))
                .isInstanceOf(GitHubAuthenticationException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void exchange_genericFeignError_includesStatusInMessage() {
        when(gitHubApiClient.getUser(any())).thenThrow(feignException(422));

        assertThatThrownBy(() -> service.exchange(request("gho_bad")))
                .isInstanceOf(GitHubAuthenticationException.class)
                .hasMessageContaining("422");
    }

    @Test
    void exchange_gitHubCallReceivesBearerPrefix() {
        stubGitHubSuccess(12345678L, "student-dev");
        stubIdentityFound();
        when(tokenProvider.generate(any())).thenReturn(jwt());

        service.exchange(request("gho_mytoken"));

        verify(gitHubApiClient).getUser("Bearer gho_mytoken");
    }

    @Test
    void exchange_unregisteredUser_throwsNoSuchElementException() {
        stubGitHubSuccess(12345678L, "ghost-user");
        when(identityRepository.findByProvider_ProviderKeyAndExternalUserId(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(request("gho_test")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("ghost-user");
    }

    @Test
    void exchange_lookupUsesGithubProviderKeyAndStringId() {
        stubGitHubSuccess(77777L, "some-user");
        stubIdentityFound();
        when(tokenProvider.generate(any())).thenReturn(jwt());

        service.exchange(request("gho_test"));

        verify(identityRepository).findByProvider_ProviderKeyAndExternalUserId(ProviderKey.GITHUB, "77777");
    }

    // --- helpers ---

    private void stubGitHubSuccess(long id, String login) {
        when(gitHubApiClient.getUser(any())).thenReturn(new GitHubUserResponse(id, login));
    }

    private void stubIdentityFound() {
        UserProfile profile = mock(UserProfile.class);
        when(profile.getId()).thenReturn(PROFILE_ID);
        ExternalIdentity identity = mock(ExternalIdentity.class);
        when(identity.getUserProfile()).thenReturn(profile);
        when(identityRepository.findByProvider_ProviderKeyAndExternalUserId(any(), any()))
                .thenReturn(Optional.of(identity));
    }

    private TokenExchangeRequest request(String accessToken) {
        TokenExchangeRequest req = new TokenExchangeRequest();
        req.setGithubAccessToken(accessToken);
        return req;
    }

    private JwtToken jwt() {
        return new JwtToken("test.jwt", Instant.now().plusSeconds(3600), Instant.now());
    }

    private FeignException feignException(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "https://api.github.com/user",
                Map.of(), null, null, null);
        return FeignException.errorStatus("getUser", feign.Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Map.of())
                .build());
    }
}
