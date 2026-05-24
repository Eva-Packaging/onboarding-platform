package xyz.catuns.onboarding.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.service.domain.GroupMappingRule;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GroupMappingRuleRepository extends JpaRepository<GroupMappingRule, UUID> {
    List<GroupMappingRule> findByAppRoleIdInAndEnabledTrueOrderByPriorityOrderAsc(Collection<UUID> roleIds);
}
