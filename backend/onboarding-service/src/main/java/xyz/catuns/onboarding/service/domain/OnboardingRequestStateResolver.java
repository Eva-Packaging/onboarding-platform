package xyz.catuns.onboarding.service.domain;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class OnboardingRequestStateResolver {

    private static final Set<OnboardingStepState> NON_TERMINAL = EnumSet.of(
        OnboardingStepState.PENDING,
        OnboardingStepState.PROCESSING,
        OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE,
        OnboardingStepState.MANUAL_REVIEW
    );

    public OnboardingRequestState resolve(List<OnboardingStep> steps) {
        if (steps.isEmpty()) {
            return OnboardingRequestState.IN_PROGRESS;
        }

        if (steps.stream().anyMatch(s -> NON_TERMINAL.contains(s.getState()))) {
            return OnboardingRequestState.IN_PROGRESS;
        }

        boolean hasSucceeded = steps.stream().anyMatch(s -> s.getState() == OnboardingStepState.SUCCEEDED);
        boolean hasFailed    = steps.stream().anyMatch(s -> s.getState() == OnboardingStepState.FAILED);

        if (hasSucceeded && hasFailed) {
            return OnboardingRequestState.PARTIAL_SUCCESS;
        }
        if (hasSucceeded) {
            return OnboardingRequestState.COMPLETED;
        }
        return OnboardingRequestState.FAILED;
    }
}