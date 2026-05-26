package xyz.catuns.onboarding.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.user.api.dto.*;
import xyz.catuns.onboarding.user.client.OnboardingServiceClient;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.ProviderKey;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.repository.ExternalIdentityRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class UserProfileService {

    private final ExternalIdentityRepository identityRepository;
    private final OnboardingServiceClient onboardingClient;

    public UserProfileService(ExternalIdentityRepository identityRepository,
                              OnboardingServiceClient onboardingClient) {
        this.identityRepository = identityRepository;
        this.onboardingClient = onboardingClient;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(String userId, Set<String> includes) {
        UUID profileId = UUID.fromString(userId);
        ExternalIdentity githubIdentity = identityRepository
            .findByProvider_ProviderKeyAndUserProfile_Id(ProviderKey.GITHUB, profileId)
            .orElseThrow(() -> new NoSuchElementException("No user found for userId: " + userId));

        UserProfile profile = githubIdentity.getUserProfile();

        List<String> roles = profile.getRoleAssignments().stream()
            .map(ra -> ra.getAppRole().getRoleKey().name())
            .toList();

        MeResponse.MeResponseBuilder builder = MeResponse.builder()
            .userId(profile.getId())
            .displayName(profile.getDisplayName())
            .primaryEmail(profile.getPrimaryEmail())
            .status(profile.getStatus().name())
            .roles(roles);

        if (includes.contains("identities")) {
            builder.github(new GitHubIdentitySummary(
                githubIdentity.getExternalUserId(),
                githubIdentity.getUsername(),
                githubIdentity.getEmail()
            ));

            AtlassianIdentitySummary atlassian = identityRepository
                .findByProvider_ProviderKeyAndUserProfile_Id(ProviderKey.ATLASSIAN, profile.getId())
                .map(ai -> new AtlassianIdentitySummary(ai.getExternalUserId(), ai.getEmail(), "MATCHED"))
                .orElse(new AtlassianIdentitySummary(null, null, "PENDING"));

            builder.atlassian(atlassian);
        }

        if (includes.contains("onboarding")) {
            onboardingClient.getLatestOnboardingForUser(profile.getId())
                .map(r -> new OnboardingSummary(r.requestId(), r.state()))
                .ifPresent(builder::onboarding);
        }

        return builder.build();
    }
}