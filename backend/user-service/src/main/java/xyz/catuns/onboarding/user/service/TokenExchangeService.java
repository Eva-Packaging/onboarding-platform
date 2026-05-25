package xyz.catuns.onboarding.user.service;

import feign.FeignException;
import org.springframework.stereotype.Service;
import xyz.catuns.onboarding.common.security.provider.Payload;
import xyz.catuns.onboarding.common.security.provider.PayloadTokenProvider;
import xyz.catuns.onboarding.user.api.dto.TokenExchangeRequest;
import xyz.catuns.onboarding.user.api.dto.TokenResponse;
import xyz.catuns.onboarding.user.client.GitHubApiClient;
import xyz.catuns.onboarding.user.client.GitHubUserResponse;
import xyz.catuns.onboarding.user.domain.ProviderKey;
import xyz.catuns.onboarding.user.exception.GitHubAuthenticationException;
import xyz.catuns.onboarding.user.repository.ExternalIdentityRepository;
import xyz.catuns.spring.jwt.core.model.JwtToken;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TokenExchangeService {

    private final GitHubApiClient gitHubApiClient;
    private final ExternalIdentityRepository identityRepository;
    private final PayloadTokenProvider tokenProvider;

    public TokenExchangeService(GitHubApiClient gitHubApiClient,
                                ExternalIdentityRepository identityRepository,
                                PayloadTokenProvider tokenProvider) {
        this.gitHubApiClient = gitHubApiClient;
        this.identityRepository = identityRepository;
        this.tokenProvider = tokenProvider;
    }

    public TokenResponse exchange(TokenExchangeRequest request) {
        GitHubUserResponse githubUser = verifyGitHubToken(request.getGithubAccessToken());
        String githubUserId = String.valueOf(githubUser.id());

        identityRepository
                .findByProvider_ProviderKeyAndExternalUserId(ProviderKey.GITHUB, githubUserId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No registered user found for GitHub account: " + githubUser.login()));

        JwtToken jwtToken = tokenProvider.generate(
                new Payload(githubUserId, UUID.randomUUID().toString()));

        long expiresIn = Duration.between(Instant.now(), jwtToken.expiration()).getSeconds();
        return TokenResponse.builder()
                .token(jwtToken.value())
                .expiresIn(expiresIn)
                .build();
    }

    private GitHubUserResponse verifyGitHubToken(String githubAccessToken) {
        try {
            return gitHubApiClient.getUser("Bearer " + githubAccessToken);
        } catch (FeignException e) {
            int status = e.status();
            if (status == 401 || status == 403) {
                throw new GitHubAuthenticationException("Invalid or expired GitHub access token");
            }
            throw new GitHubAuthenticationException("Failed to verify GitHub identity: " + status);
        }
    }
}
