package xyz.catuns.onboarding.provisioning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.provisioning.domain.ProviderTarget;
import xyz.catuns.onboarding.provisioning.domain.TargetType;

import java.util.List;
import java.util.UUID;

public interface ProviderTargetRepository extends JpaRepository<ProviderTarget, UUID> {
    List<ProviderTarget> findByTargetTypeAndEnabledTrue(TargetType targetType);
}