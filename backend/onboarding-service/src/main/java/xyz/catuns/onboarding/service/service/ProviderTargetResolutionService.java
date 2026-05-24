package xyz.catuns.onboarding.service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.service.domain.AppRole;
import xyz.catuns.onboarding.service.domain.GroupMappingRule;
import xyz.catuns.onboarding.service.domain.ProviderTarget;
import xyz.catuns.onboarding.service.repository.AppRoleRepository;
import xyz.catuns.onboarding.service.repository.GroupMappingRuleRepository;
import xyz.catuns.onboarding.service.repository.ProviderTargetRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProviderTargetResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ProviderTargetResolutionService.class);

    private final AppRoleRepository appRoleRepository;
    private final GroupMappingRuleRepository ruleRepository;
    private final ProviderTargetRepository providerTargetRepository;

    public ProviderTargetResolutionService(
        AppRoleRepository appRoleRepository,
        GroupMappingRuleRepository ruleRepository,
        ProviderTargetRepository providerTargetRepository
    ) {
        this.appRoleRepository = appRoleRepository;
        this.ruleRepository = ruleRepository;
        this.providerTargetRepository = providerTargetRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveTarget(Collection<String> roleKeys, String targetType) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return Optional.empty();
        }

        Set<UUID> roleIds = appRoleRepository.findByRoleKeyIn(roleKeys)
            .stream()
            .map(AppRole::getId)
            .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            log.warn("No app_role rows found for roleKeys={}", roleKeys);
            return Optional.empty();
        }

        List<GroupMappingRule> rules =
            ruleRepository.findByAppRoleIdInAndEnabledTrueOrderByPriorityOrderAsc(roleIds);

        Set<UUID> targetIds = rules.stream()
            .map(GroupMappingRule::getProviderTargetId)
            .collect(Collectors.toSet());

        Map<UUID, ProviderTarget> targets = providerTargetRepository.findAllById(targetIds)
            .stream()
            .filter(t -> t.isEnabled() && targetType.equals(t.getTargetType()))
            .collect(Collectors.toMap(ProviderTarget::getId, Function.identity()));

        return rules.stream()
            .filter(r -> targets.containsKey(r.getProviderTargetId()))
            .map(GroupMappingRule::getProviderTargetId)
            .findFirst();
    }
}
