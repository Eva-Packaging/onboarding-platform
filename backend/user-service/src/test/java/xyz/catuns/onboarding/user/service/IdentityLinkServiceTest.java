package xyz.catuns.onboarding.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import xyz.catuns.onboarding.user.api.dto.IdentityLinkCreateRequest;
import xyz.catuns.onboarding.user.api.dto.IdentityLinkResponse;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.IdentityLink;
import xyz.catuns.onboarding.user.domain.MatchStrategy;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.repository.ExternalIdentityRepository;
import xyz.catuns.onboarding.user.repository.IdentityLinkRepository;
import xyz.catuns.onboarding.user.repository.UserProfileRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class IdentityLinkServiceTest {

    private IdentityLinkRepository identityLinkRepository;
    private UserProfileRepository userProfileRepository;
    private ExternalIdentityRepository externalIdentityRepository;
    private IdentityLinkService identityLinkService;

    private final UUID userProfileId = UUID.randomUUID();
    private final UUID githubIdentityId = UUID.randomUUID();
    private final UUID atlassianIdentityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        identityLinkRepository = Mockito.mock(IdentityLinkRepository.class);
        userProfileRepository = Mockito.mock(UserProfileRepository.class);
        externalIdentityRepository = Mockito.mock(ExternalIdentityRepository.class);
        identityLinkService = new IdentityLinkService(identityLinkRepository, userProfileRepository, externalIdentityRepository);
    }

    @Test
    void createIdentityLink_validRequest_persistsAndReturnsResponse() {
        UserProfile profile = new UserProfile();
        profile.setId(userProfileId);

        ExternalIdentity github = new ExternalIdentity();
        github.setId(githubIdentityId);

        ExternalIdentity atlassian = new ExternalIdentity();
        atlassian.setId(atlassianIdentityId);

        when(userProfileRepository.findById(userProfileId)).thenReturn(Optional.of(profile));
        when(externalIdentityRepository.findById(githubIdentityId)).thenReturn(Optional.of(github));
        when(externalIdentityRepository.findById(atlassianIdentityId)).thenReturn(Optional.of(atlassian));
        when(identityLinkRepository.save(any(IdentityLink.class))).thenAnswer(invocation -> {
            IdentityLink link = invocation.getArgument(0);
            link.setId(UUID.randomUUID());
            link.setCreatedAt(Instant.now());
            return link;
        });

        IdentityLinkCreateRequest request = new IdentityLinkCreateRequest(
            userProfileId, githubIdentityId, atlassianIdentityId, MatchStrategy.EMAIL_EXACT, BigDecimal.ONE
        );

        IdentityLinkResponse response = identityLinkService.createIdentityLink(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.userProfileId()).isEqualTo(userProfileId);
        assertThat(response.githubIdentityId()).isEqualTo(githubIdentityId);
        assertThat(response.atlassianIdentityId()).isEqualTo(atlassianIdentityId);
        assertThat(response.matchStrategy()).isEqualTo("EMAIL_EXACT");
        assertThat(response.confidenceScore()).isEqualTo(BigDecimal.ONE);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void createIdentityLink_unknownUserProfile_throwsNoSuchElement() {
        when(userProfileRepository.findById(userProfileId)).thenReturn(Optional.empty());

        IdentityLinkCreateRequest request = new IdentityLinkCreateRequest(
            userProfileId, githubIdentityId, atlassianIdentityId, MatchStrategy.EMAIL_EXACT, BigDecimal.ONE
        );

        assertThatThrownBy(() -> identityLinkService.createIdentityLink(request))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void createIdentityLink_unknownExternalIdentity_throwsNoSuchElement() {
        UserProfile profile = new UserProfile();
        profile.setId(userProfileId);

        when(userProfileRepository.findById(userProfileId)).thenReturn(Optional.of(profile));
        when(externalIdentityRepository.findById(githubIdentityId)).thenReturn(Optional.empty());

        IdentityLinkCreateRequest request = new IdentityLinkCreateRequest(
            userProfileId, githubIdentityId, atlassianIdentityId, MatchStrategy.EMAIL_EXACT, BigDecimal.ONE
        );

        assertThatThrownBy(() -> identityLinkService.createIdentityLink(request))
            .isInstanceOf(NoSuchElementException.class);
    }
}