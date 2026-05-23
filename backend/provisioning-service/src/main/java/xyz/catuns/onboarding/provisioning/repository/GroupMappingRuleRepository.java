package xyz.catuns.onboarding.provisioning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.provisioning.domain.GroupMappingRule;

import java.util.List;
import java.util.UUID;

public interface GroupMappingRuleRepository extends JpaRepository<GroupMappingRule, UUID> {
    List<GroupMappingRule> findByProviderTarget_Id(UUID providerTargetId);
}