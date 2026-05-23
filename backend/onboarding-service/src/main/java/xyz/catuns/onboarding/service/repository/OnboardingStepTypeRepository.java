package xyz.catuns.onboarding.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.service.domain.OnboardingStepType;
import xyz.catuns.onboarding.service.domain.StepType;

import java.util.Optional;

public interface OnboardingStepTypeRepository extends JpaRepository<OnboardingStepType, Short> {
    Optional<OnboardingStepType> findByStepKey(StepType stepKey);
}