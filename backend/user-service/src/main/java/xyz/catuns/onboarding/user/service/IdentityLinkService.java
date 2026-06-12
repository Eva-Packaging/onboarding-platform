package xyz.catuns.onboarding.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.user.api.dto.IdentityLinkCreateRequest;
import xyz.catuns.onboarding.user.api.dto.IdentityLinkResponse;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.IdentityLink;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.repository.ExternalIdentityRepository;
import xyz.catuns.onboarding.user.repository.IdentityLinkRepository;
import xyz.catuns.onboarding.user.repository.UserProfileRepository;

import java.util.NoSuchElementException;

@Service
public class IdentityLinkService {

    private final IdentityLinkRepository identityLinkRepository;
    private final UserProfileRepository userProfileRepository;
    private final ExternalIdentityRepository externalIdentityRepository;

    public IdentityLinkService(IdentityLinkRepository identityLinkRepository,
                               UserProfileRepository userProfileRepository,
                               ExternalIdentityRepository externalIdentityRepository) {
        this.identityLinkRepository = identityLinkRepository;
        this.userProfileRepository = userProfileRepository;
        this.externalIdentityRepository = externalIdentityRepository;
    }

    @Transactional
    public IdentityLinkResponse createIdentityLink(IdentityLinkCreateRequest request) {
        UserProfile userProfile = userProfileRepository.findById(request.userProfileId())
            .orElseThrow(() -> new NoSuchElementException("No user profile found for id: " + request.userProfileId()));

        ExternalIdentity githubIdentity = externalIdentityRepository.findById(request.githubIdentityId())
            .orElseThrow(() -> new NoSuchElementException("No external identity found for id: " + request.githubIdentityId()));

        ExternalIdentity atlassianIdentity = externalIdentityRepository.findById(request.atlassianIdentityId())
            .orElseThrow(() -> new NoSuchElementException("No external identity found for id: " + request.atlassianIdentityId()));

        IdentityLink link = new IdentityLink();
        link.setUserProfile(userProfile);
        link.setGithubIdentity(githubIdentity);
        link.setAtlassianIdentity(atlassianIdentity);
        link.setMatchStrategy(request.matchStrategy());
        link.setConfidenceScore(request.confidenceScore());

        IdentityLink saved = identityLinkRepository.save(link);

        return new IdentityLinkResponse(
            saved.getId(),
            saved.getUserProfile().getId(),
            saved.getGithubIdentity().getId(),
            saved.getAtlassianIdentity().getId(),
            saved.getMatchStrategy().name(),
            saved.getConfidenceScore(),
            saved.getCreatedAt()
        );
    }
}