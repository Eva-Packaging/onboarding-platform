package xyz.catuns.onboarding.user.exception;

public class OnboardingServiceUnavailableException extends RuntimeException {

    public OnboardingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}