package xyz.catuns.onboarding.service.domain;

import org.springframework.stereotype.Component;
import xyz.catuns.onboarding.service.exception.IllegalStateTransitionException;

import java.util.Set;

@Component
public class OnboardingStepStateMachine {

    public void transitionTo(OnboardingStep step, OnboardingStepState target) {
        OnboardingStepState current = step.getState();
        Set<OnboardingStepState> allowed = StepStateTransitions.ALLOWED.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalStateTransitionException(step.getId(), current, target);
        }
        step.setState(target);
    }
}