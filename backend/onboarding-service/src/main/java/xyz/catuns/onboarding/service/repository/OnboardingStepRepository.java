package xyz.catuns.onboarding.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.service.domain.OnboardingStep;
import xyz.catuns.onboarding.service.domain.StepType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingStepRepository extends JpaRepository<OnboardingStep, UUID> {
    List<OnboardingStep> findByOnboardingRequest_Id(UUID onboardingRequestId);
    Optional<OnboardingStep> findByOnboardingRequest_IdAndStepType_StepKey(UUID requestId, StepType stepKey);
}