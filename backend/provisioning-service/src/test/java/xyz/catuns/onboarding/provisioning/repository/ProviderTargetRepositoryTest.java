package xyz.catuns.onboarding.provisioning.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import xyz.catuns.onboarding.provisioning.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.provisioning.domain.ProviderTarget;
import xyz.catuns.onboarding.provisioning.domain.TargetType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTestContainersConfiguration.class)
class ProviderTargetRepositoryTest {

    @Autowired
    ProviderTargetRepository repository;

    @Test
    void save_persistsAndAssignsGeneratedId() {
        ProviderTarget saved = repository.save(buildTarget(TargetType.GITHUB_TEAM, "org/team-a", true));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTargetType()).isEqualTo(TargetType.GITHUB_TEAM);
    }

    @Test
    void save_populatesTimestamps() {
        ProviderTarget saved = repository.save(buildTarget(TargetType.ATLASSIAN_GROUP, "jira-devs", true));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void save_enabledDefaultsToTrue() {
        ProviderTarget target = new ProviderTarget();
        target.setProviderId(UUID.randomUUID());
        target.setTargetType(TargetType.GITHUB_TEAM);
        target.setExternalKey("default-team");
        target.setDisplayName("Default Team");
        ProviderTarget saved = repository.save(target);

        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void findByTargetTypeAndEnabledTrue_returnsOnlyEnabledTargetsOfType() {
        UUID providerId = UUID.randomUUID();
        repository.save(buildTargetForProvider(providerId, TargetType.GITHUB_TEAM, "team-a", true));
        repository.save(buildTargetForProvider(providerId, TargetType.GITHUB_TEAM, "team-b", false));
        repository.save(buildTargetForProvider(UUID.randomUUID(), TargetType.ATLASSIAN_GROUP, "jira-devs", true));

        List<ProviderTarget> found = repository.findByTargetTypeAndEnabledTrue(TargetType.GITHUB_TEAM);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getExternalKey()).isEqualTo("team-a");
    }

    @Test
    void findByTargetTypeAndEnabledTrue_returnsEmptyWhenNoneMatch() {
        repository.save(buildTarget(TargetType.GITHUB_TEAM, "team-only", true));

        List<ProviderTarget> found = repository.findByTargetTypeAndEnabledTrue(TargetType.ATLASSIAN_GROUP);

        assertThat(found).isEmpty();
    }

    @Test
    void uniqueConstraint_rejectsDuplicateProviderTypeKey() {
        UUID providerId = UUID.randomUUID();
        repository.saveAndFlush(buildTargetForProvider(providerId, TargetType.GITHUB_TEAM, "team-dup", true));

        assertThatThrownBy(() ->
            repository.saveAndFlush(buildTargetForProvider(providerId, TargetType.GITHUB_TEAM, "team-dup", true))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraint_allowsSameKeyUnderDifferentType() {
        UUID providerId = UUID.randomUUID();
        repository.saveAndFlush(buildTargetForProvider(providerId, TargetType.GITHUB_TEAM, "shared-key", true));
        ProviderTarget second = repository.saveAndFlush(
            buildTargetForProvider(providerId, TargetType.ATLASSIAN_GROUP, "shared-key", true)
        );

        assertThat(second.getId()).isNotNull();
    }

    private ProviderTarget buildTarget(TargetType type, String externalKey, boolean enabled) {
        return buildTargetForProvider(UUID.randomUUID(), type, externalKey, enabled);
    }

    private ProviderTarget buildTargetForProvider(UUID providerId, TargetType type, String externalKey, boolean enabled) {
        ProviderTarget t = new ProviderTarget();
        t.setProviderId(providerId);
        t.setTargetType(type);
        t.setExternalKey(externalKey);
        t.setDisplayName(externalKey + " display");
        t.setEnabled(enabled);
        return t;
    }
}