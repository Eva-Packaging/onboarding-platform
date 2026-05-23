package xyz.catuns.onboarding.provisioning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.provisioning.domain.ProvisioningAuditLog;

import java.util.List;
import java.util.UUID;

public interface ProvisioningAuditLogRepository extends JpaRepository<ProvisioningAuditLog, UUID> {
    List<ProvisioningAuditLog> findByOnboardingStepId(UUID onboardingStepId);
}