package xyz.catuns.onboarding.user.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import xyz.catuns.onboarding.user.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.ExternalProvider;
import xyz.catuns.onboarding.user.domain.ProviderKey;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.domain.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTestContainersConfiguration.class)
class ExternalIdentityRepositoryTest {

    @Autowired
    ExternalIdentityRepository identityRepository;

    @Autowired
    ExternalProviderRepository providerRepository;

    @Autowired
    UserProfileRepository profileRepository;

    private ExternalProvider githubProvider;
    private UserProfile userProfile;

    @BeforeEach
    void setUp() {
        githubProvider = providerRepository.save(buildProvider(ProviderKey.GITHUB, "GitHub"));

        userProfile = profileRepository.save(buildProfile("test@example.com", "Test User"));
    }

    @Test
    void save_persistsIdentityLinkedToProviderAndProfile() {
        ExternalIdentity saved = identityRepository.save(buildIdentity("gh-001", "testuser"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getExternalUserId()).isEqualTo("gh-001");
        assertThat(saved.getProvider().getProviderKey()).isEqualTo(ProviderKey.GITHUB);
    }

    @Test
    void save_populatesTimestamps() {
        ExternalIdentity saved = identityRepository.save(buildIdentity("gh-002", "user2"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByProviderKeyAndExternalUserId_returnsMatch() {
        identityRepository.save(buildIdentity("gh-003", "findme"));

        Optional<ExternalIdentity> found = identityRepository
            .findByProvider_ProviderKeyAndExternalUserId(ProviderKey.GITHUB, "gh-003");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("findme");
    }

    @Test
    void findByProviderKeyAndExternalUserId_returnsEmptyForMismatch() {
        Optional<ExternalIdentity> found = identityRepository
            .findByProvider_ProviderKeyAndExternalUserId(ProviderKey.GITHUB, "does-not-exist");

        assertThat(found).isEmpty();
    }

    @Test
    void findByUserProfileId_returnsAllIdentitiesForUser() {
        identityRepository.save(buildIdentity("gh-010", "userA"));
        identityRepository.save(buildIdentity("gh-011", "userB"));

        List<ExternalIdentity> found = identityRepository.findByUserProfile_Id(userProfile.getId());

        assertThat(found).hasSize(2);
    }

    @Test
    void findByProviderIdAndExternalUserId_returnsMatch() {
        identityRepository.save(buildIdentity("gh-020", "byuuid"));

        Optional<ExternalIdentity> found = identityRepository
            .findByProviderIdAndExternalUserId(githubProvider.getId(), "gh-020");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("byuuid");
    }

    @Test
    void findByProviderIdAndExternalUserId_returnsEmptyForUnknownProvider() {
        Optional<ExternalIdentity> found = identityRepository
            .findByProviderIdAndExternalUserId(UUID.randomUUID(), "gh-020");

        assertThat(found).isEmpty();
    }

    @Test
    void findByProviderKeyAndEmail_returnsMatch() {
        ExternalProvider atlassianProvider = providerRepository.save(buildProvider(ProviderKey.ATLASSIAN, "Atlassian"));

        ExternalIdentity atlassianIdentity = new ExternalIdentity();
        atlassianIdentity.setUserProfile(userProfile);
        atlassianIdentity.setProvider(atlassianProvider);
        atlassianIdentity.setExternalUserId("atl-account-001");
        atlassianIdentity.setEmail("findme@example.com");
        identityRepository.save(atlassianIdentity);

        Optional<ExternalIdentity> found = identityRepository
            .findByProvider_ProviderKeyAndEmail(ProviderKey.ATLASSIAN, "findme@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getExternalUserId()).isEqualTo("atl-account-001");
    }

    @Test
    void findByProviderKeyAndEmail_returnsEmptyForMismatch() {
        Optional<ExternalIdentity> found = identityRepository
            .findByProvider_ProviderKeyAndEmail(ProviderKey.ATLASSIAN, "missing@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void uniqueConstraint_rejectsDuplicateProviderAndExternalUserId() {
        identityRepository.saveAndFlush(buildIdentity("gh-dup", "dupuser"));

        assertThatThrownBy(() -> identityRepository.saveAndFlush(buildIdentity("gh-dup", "other")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ExternalIdentity buildIdentity(String externalUserId, String username) {
        ExternalIdentity identity = new ExternalIdentity();
        identity.setUserProfile(userProfile);
        identity.setProvider(githubProvider);
        identity.setExternalUserId(externalUserId);
        identity.setUsername(username);
        identity.setEmail("user@example.com");
        identity.setPrimary(true);
        return identity;
    }

    private ExternalProvider buildProvider(ProviderKey key, String displayName) {
        ExternalProvider p = new ExternalProvider();
        p.setProviderKey(key);
        p.setDisplayName(displayName);
        return p;
    }

    private UserProfile buildProfile(String email, String name) {
        UserProfile p = new UserProfile();
        p.setDisplayName(name);
        p.setPrimaryEmail(email);
        p.setStatus(UserStatus.PENDING_ONBOARDING);
        return p;
    }
}