package xyz.catuns.onboarding.service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import xyz.catuns.onboarding.service.domain.AppRole;
import xyz.catuns.onboarding.service.domain.GroupMappingRule;
import xyz.catuns.onboarding.service.domain.ProviderTarget;
import xyz.catuns.onboarding.service.repository.AppRoleRepository;
import xyz.catuns.onboarding.service.repository.GroupMappingRuleRepository;
import xyz.catuns.onboarding.service.repository.ProviderTargetRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProviderTargetResolutionServiceTest {

    private AppRoleRepository appRoleRepository;
    private GroupMappingRuleRepository ruleRepository;
    private ProviderTargetRepository providerTargetRepository;
    private ProviderTargetResolutionService resolutionService;

    private static final UUID STUDENT_ROLE_ID = UUID.randomUUID();
    private static final UUID GITHUB_TARGET_ID = UUID.randomUUID();
    private static final UUID ATLASSIAN_TARGET_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        appRoleRepository = Mockito.mock(AppRoleRepository.class);
        ruleRepository = Mockito.mock(GroupMappingRuleRepository.class);
        providerTargetRepository = Mockito.mock(ProviderTargetRepository.class);
        resolutionService = new ProviderTargetResolutionService(
            appRoleRepository, ruleRepository, providerTargetRepository
        );
    }

    @Test
    void resolveTarget_singleMatchingRule_returnsProviderTargetId() {
        AppRole studentRole = role(STUDENT_ROLE_ID, "STUDENT");
        when(appRoleRepository.findByRoleKeyIn(List.of("STUDENT")))
            .thenReturn(Set.of(studentRole));
        when(ruleRepository.findByAppRoleIdInAndEnabledTrueOrderByPriorityOrderAsc(Set.of(STUDENT_ROLE_ID)))
            .thenReturn(List.of(rule(STUDENT_ROLE_ID, GITHUB_TARGET_ID, 1)));
        when(providerTargetRepository.findAllById(Set.of(GITHUB_TARGET_ID)))
            .thenReturn(List.of(target(GITHUB_TARGET_ID, "GITHUB_TEAM", true)));

        Optional<UUID> result = resolutionService.resolveTarget(List.of("STUDENT"), "GITHUB_TEAM");

        assertThat(result).contains(GITHUB_TARGET_ID);
    }

    @Test
    void resolveTarget_multipleRules_returnsLowestPriorityOrder() {
        UUID highPriorityTarget = UUID.randomUUID();
        UUID lowPriorityTarget = UUID.randomUUID();

        AppRole studentRole = role(STUDENT_ROLE_ID, "STUDENT");
        when(appRoleRepository.findByRoleKeyIn(List.of("STUDENT")))
            .thenReturn(Set.of(studentRole));
        when(ruleRepository.findByAppRoleIdInAndEnabledTrueOrderByPriorityOrderAsc(Set.of(STUDENT_ROLE_ID)))
            .thenReturn(List.of(
                rule(STUDENT_ROLE_ID, highPriorityTarget, 1),
                rule(STUDENT_ROLE_ID, lowPriorityTarget, 2)
            ));
        when(providerTargetRepository.findAllById(Set.of(highPriorityTarget, lowPriorityTarget)))
            .thenReturn(List.of(
                target(highPriorityTarget, "GITHUB_TEAM", true),
                target(lowPriorityTarget, "GITHUB_TEAM", true)
            ));

        Optional<UUID> result = resolutionService.resolveTarget(List.of("STUDENT"), "GITHUB_TEAM");

        assertThat(result).contains(highPriorityTarget);
    }

    @Test
    void resolveTarget_noMatchingRoleKey_returnsEmpty() {
        when(appRoleRepository.findByRoleKeyIn(List.of("UNKNOWN_ROLE")))
            .thenReturn(Set.of());

        Optional<UUID> result = resolutionService.resolveTarget(List.of("UNKNOWN_ROLE"), "GITHUB_TEAM");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveTarget_noRulesForRole_returnsEmpty() {
        AppRole studentRole = role(STUDENT_ROLE_ID, "STUDENT");
        when(appRoleRepository.findByRoleKeyIn(List.of("STUDENT")))
            .thenReturn(Set.of(studentRole));
        when(ruleRepository.findByAppRoleIdInAndEnabledTrueOrderByPriorityOrderAsc(Set.of(STUDENT_ROLE_ID)))
            .thenReturn(List.of());
        when(providerTargetRepository.findAllById(Set.of()))
            .thenReturn(List.of());

        Optional<UUID> result = resolutionService.resolveTarget(List.of("STUDENT"), "GITHUB_TEAM");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveTarget_targetTypeDoesNotMatch_returnsEmpty() {
        AppRole studentRole = role(STUDENT_ROLE_ID, "STUDENT");
        when(appRoleRepository.findByRoleKeyIn(List.of("STUDENT")))
            .thenReturn(Set.of(studentRole));
        when(ruleRepository.findByAppRoleIdInAndEnabledTrueOrderByPriorityOrderAsc(Set.of(STUDENT_ROLE_ID)))
            .thenReturn(List.of(rule(STUDENT_ROLE_ID, ATLASSIAN_TARGET_ID, 1)));
        when(providerTargetRepository.findAllById(Set.of(ATLASSIAN_TARGET_ID)))
            .thenReturn(List.of(target(ATLASSIAN_TARGET_ID, "ATLASSIAN_GROUP", true)));

        Optional<UUID> result = resolutionService.resolveTarget(List.of("STUDENT"), "GITHUB_TEAM");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveTarget_disabledProviderTarget_excluded() {
        AppRole studentRole = role(STUDENT_ROLE_ID, "STUDENT");
        when(appRoleRepository.findByRoleKeyIn(List.of("STUDENT")))
            .thenReturn(Set.of(studentRole));
        when(ruleRepository.findByAppRoleIdInAndEnabledTrueOrderByPriorityOrderAsc(Set.of(STUDENT_ROLE_ID)))
            .thenReturn(List.of(rule(STUDENT_ROLE_ID, GITHUB_TARGET_ID, 1)));
        when(providerTargetRepository.findAllById(Set.of(GITHUB_TARGET_ID)))
            .thenReturn(List.of(target(GITHUB_TARGET_ID, "GITHUB_TEAM", false)));

        Optional<UUID> result = resolutionService.resolveTarget(List.of("STUDENT"), "GITHUB_TEAM");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveTarget_emptyRoleKeys_returnsEmpty() {
        Optional<UUID> result = resolutionService.resolveTarget(List.of(), "GITHUB_TEAM");

        assertThat(result).isEmpty();
        verifyNoInteractions(appRoleRepository, ruleRepository, providerTargetRepository);
    }

    private AppRole role(UUID id, String roleKey) {
        AppRole role = Mockito.mock(AppRole.class);
        when(role.getId()).thenReturn(id);
        when(role.getRoleKey()).thenReturn(roleKey);
        return role;
    }

    private GroupMappingRule rule(UUID roleId, UUID targetId, int priority) {
        GroupMappingRule rule = new GroupMappingRule();
        rule.setAppRoleId(roleId);
        rule.setProviderTargetId(targetId);
        rule.setPriorityOrder(priority);
        rule.setEnabled(true);
        return rule;
    }

    private ProviderTarget target(UUID id, String targetType, boolean enabled) {
        ProviderTarget pt = new ProviderTarget();
        pt.setId(id);
        pt.setTargetType(targetType);
        pt.setExternalKey("some-key");
        pt.setDisplayName("Some Target");
        pt.setProviderId(UUID.randomUUID());
        pt.setEnabled(enabled);
        return pt;
    }
}
