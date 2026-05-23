package xyz.catuns.onboarding.user.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import xyz.catuns.onboarding.user.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.ExternalProvider;
import xyz.catuns.onboarding.user.domain.IdentityLink;
import xyz.catuns.onboarding.user.domain.MatchStrategy;
import xyz.catuns.onboarding.user.domain.ProviderKey;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.domain.UserStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTestContainersConfiguration.class)
class IdentityLinkRepositoryTest {

    @Autowired
    IdentityLinkRepository identityLinkRepository;

    @Autowired
    ExternalIdentityRepository externalIdentityRepository;

    @Autowired
    ExternalProviderRepository externalProviderRepository;

    @Autowired
    UserProfileRepository userProfileRepository;

    private UserProfile profile;
    private ExternalIdentity githubIdentity;
    private ExternalIdentity atlassianIdentity;

    @BeforeEach
    void setUp() {
        profile = userProfileRepository.save(buildProfile());

        ExternalProvider github = externalProviderRepository.save(buildProvider(ProviderKey.GITHUB, "GitHub"));
        ExternalProvider atlassian = externalProviderRepository.save(buildProvider(ProviderKey.ATLASSIAN, "Atlassian"));

        githubIdentity = externalIdentityRepository.save(buildIdentity(profile, github, "gh-001", "ghuser"));
        atlassianIdentity = externalIdentityRepository.save(buildIdentity(profile, atlassian, "atl-001", "atluser"));
    }

    @Test
    void save_persistsLinkReferencingBothIdentities() {
        IdentityLink saved = identityLinkRepository.save(buildLink(MatchStrategy.EMAIL_EXACT, BigDecimal.ONE));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMatchStrategy()).isEqualTo(MatchStrategy.EMAIL_EXACT);
        assertThat(saved.getGithubIdentity().getId()).isEqualTo(githubIdentity.getId());
        assertThat(saved.getAtlassianIdentity().getId()).isEqualTo(atlassianIdentity.getId());
    }

    @Test
    void save_populatesCreatedAt() {
        IdentityLink saved = identityLinkRepository.save(buildLink(MatchStrategy.EMAIL_EXACT, BigDecimal.ONE));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void save_allowsNullConfidenceScore() {
        IdentityLink saved = identityLinkRepository.save(buildLink(MatchStrategy.UNVERIFIED, null));

        assertThat(saved.getConfidenceScore()).isNull();
    }

    @Test
    void findByUserProfileId_returnsLinksForUser() {
        identityLinkRepository.save(buildLink(MatchStrategy.EMAIL_EXACT, BigDecimal.ONE));

        List<IdentityLink> found = identityLinkRepository.findByUserProfile_Id(profile.getId());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getMatchStrategy()).isEqualTo(MatchStrategy.EMAIL_EXACT);
    }

    @Test
    void findByUserProfileId_returnsEmptyForUnknownUser() {
        List<IdentityLink> found = identityLinkRepository.findByUserProfile_Id(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    private IdentityLink buildLink(MatchStrategy strategy, BigDecimal confidence) {
        IdentityLink link = new IdentityLink();
        link.setUserProfile(profile);
        link.setGithubIdentity(githubIdentity);
        link.setAtlassianIdentity(atlassianIdentity);
        link.setMatchStrategy(strategy);
        link.setConfidenceScore(confidence);
        return link;
    }

    private UserProfile buildProfile() {
        UserProfile p = new UserProfile();
        p.setDisplayName("Link Test User");
        p.setPrimaryEmail("linktest@example.com");
        p.setStatus(UserStatus.PENDING_ONBOARDING);
        return p;
    }

    private ExternalProvider buildProvider(ProviderKey key, String displayName) {
        ExternalProvider p = new ExternalProvider();
        p.setProviderKey(key);
        p.setDisplayName(displayName);
        return p;
    }

    private ExternalIdentity buildIdentity(UserProfile owner, ExternalProvider provider,
                                           String externalUserId, String username) {
        ExternalIdentity identity = new ExternalIdentity();
        identity.setUserProfile(owner);
        identity.setProvider(provider);
        identity.setExternalUserId(externalUserId);
        identity.setUsername(username);
        identity.setPrimary(true);
        return identity;
    }
}