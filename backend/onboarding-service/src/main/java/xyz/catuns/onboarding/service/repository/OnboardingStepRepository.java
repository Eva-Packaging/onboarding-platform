package xyz.catuns.onboarding.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.service.domain.OnboardingStep;

import java.util.List;
import java.util.UUID;

public interface OnboardingStepRepository extends JpaRepository<OnboardingStep, UUID> {
    List<OnboardingStep> findByOnboardingRequest_Id(UUID onboardingRequestId);
}