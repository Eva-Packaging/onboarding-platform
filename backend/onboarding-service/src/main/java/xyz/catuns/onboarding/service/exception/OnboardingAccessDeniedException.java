package xyz.catuns.onboarding.service.exception;

import java.util.UUID;

public class OnboardingAccessDeniedException extends RuntimeException {

    public OnboardingAccessDeniedException(UUID requestId) {
        super("Caller is not the owner of onboarding request: " + requestId);
    }
}