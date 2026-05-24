package xyz.catuns.onboarding.service.exception;

import xyz.catuns.onboarding.service.domain.OnboardingStepState;

public class StepNotRetryableException extends RuntimeException {

    private final String stepKey;

    public StepNotRetryableException(String stepKey, OnboardingStepState currentState) {
        super("Step " + stepKey + " is not retryable" +
              (currentState != null ? " (current state: " + currentState + ")" : " (step not found)"));
        this.stepKey = stepKey;
    }

    public String getStepKey() {
        return stepKey;
    }
}
