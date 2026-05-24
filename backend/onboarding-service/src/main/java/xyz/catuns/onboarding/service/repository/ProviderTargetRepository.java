package xyz.catuns.onboarding.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.service.domain.ProviderTarget;

import java.util.UUID;

public interface ProviderTargetRepository extends JpaRepository<ProviderTarget, UUID> {
}