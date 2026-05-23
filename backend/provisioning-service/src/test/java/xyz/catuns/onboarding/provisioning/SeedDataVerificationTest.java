package xyz.catuns.onboarding.provisioning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import xyz.catuns.onboarding.provisioning.domain.GroupMappingRule;
import xyz.catuns.onboarding.provisioning.domain.ProviderTarget;
import xyz.catuns.onboarding.provisioning.domain.TargetType;
import xyz.catuns.onboarding.provisioning.repository.GroupMappingRuleRepository;
import xyz.catuns.onboarding.provisioning.repository.ProviderTargetRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SeedDataVerificationTest {

    private static final UUID STUDENT_ROLE_ID =
        UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID INSTRUCTOR_ROLE_ID =
        UUID.fromString("b0000000-0000-0000-0000-000000000002");

    @Autowired
    ProviderTargetRepository providerTargetRepository;

    @Autowired
    GroupMappingRuleRepository groupMappingRuleRepository;

    @Test
    void seededGithubTeamTarget_isEnabledAndQueryable() {
        List<ProviderTarget> targets =
            providerTargetRepository.findByTargetTypeAndEnabledTrue(TargetType.GITHUB_TEAM);

        assertThat(targets).isNotEmpty();
        assertThat(targets).allMatch(ProviderTarget::isEnabled);
    }

    @Test
    void seededAtlassianGroupTarget_isEnabledAndQueryable() {
        List<ProviderTarget> targets =
            providerTargetRepository.findByTargetTypeAndEnabledTrue(TargetType.ATLASSIAN_GROUP);

        assertThat(targets).isNotEmpty();
        assertThat(targets).allMatch(ProviderTarget::isEnabled);
    }

    @Test
    void seededMappingRules_existForStudentRole() {
        List<GroupMappingRule> all = groupMappingRuleRepository.findAll();

        assertThat(all)
            .filteredOn(r -> STUDENT_ROLE_ID.equals(r.getAppRoleId()) && r.isEnabled())
            .isNotEmpty();
    }

    @Test
    void seededMappingRules_existForInstructorRole() {
        List<GroupMappingRule> all = groupMappingRuleRepository.findAll();

        assertThat(all)
            .filteredOn(r -> INSTRUCTOR_ROLE_ID.equals(r.getAppRoleId()) && r.isEnabled())
            .isNotEmpty();
    }

    @Test
    void seededMappingRules_coversAllProviderTypes() {
        List<ProviderTarget> githubTargets =
            providerTargetRepository.findByTargetTypeAndEnabledTrue(TargetType.GITHUB_TEAM);
        List<ProviderTarget> atlassianTargets =
            providerTargetRepository.findByTargetTypeAndEnabledTrue(TargetType.ATLASSIAN_GROUP);
        List<GroupMappingRule> all = groupMappingRuleRepository.findAll();

        UUID githubTargetId = githubTargets.get(0).getId();
        UUID atlassianTargetId = atlassianTargets.get(0).getId();

        assertThat(all).anyMatch(r -> githubTargetId.equals(r.getProviderTarget().getId()));
        assertThat(all).anyMatch(r -> atlassianTargetId.equals(r.getProviderTarget().getId()));
    }
}