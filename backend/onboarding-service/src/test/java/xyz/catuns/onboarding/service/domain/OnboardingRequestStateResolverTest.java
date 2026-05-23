package xyz.catuns.onboarding.service.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingRequestStateResolverTest {

    private final OnboardingRequestStateResolver resolver = new OnboardingRequestStateResolver();

    @Test
    void emptySteps_resolveToInProgress() {
        assertThat(resolver.resolve(List.of())).isEqualTo(OnboardingRequestState.IN_PROGRESS);
    }

    @Test
    void allPending_resolveToInProgress() {
        List<OnboardingStep> steps = List.of(
            stepWith(OnboardingStepState.PENDING),
            stepWith(OnboardingStepState.PENDING)
        );
        assertThat(resolver.resolve(steps)).isEqualTo(OnboardingRequestState.IN_PROGRESS);
    }

    @Test
    void anyProcessing_resolveToInProgress() {
        List<OnboardingStep> steps = List.of(
            stepWith(OnboardingStepState.PROCESSING),
            stepWith(OnboardingStepState.SUCCEEDED)
        );
        assertThat(resolver.resolve(steps)).isEqualTo(OnboardingRequestState.IN_PROGRESS);
    }

    @Test
    void anyPendingExternalAcceptance_resolveToInProgress() {
        List<OnboardingStep> steps = List.of(
            stepWith(OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE),
            stepWith(OnboardingStepState.SUCCEEDED)
        );
        assertThat(resolver.resolve(steps)).isEqualTo(OnboardingRequestState.IN_PROGRESS);
    }

    @Test
    void anyManualReview_resolveToInProgress() {
        List<OnboardingStep> steps = List.of(
            stepWith(OnboardingStepState.MANUAL_REVIEW),
            stepWith(OnboardingStepState.FAILED)
        );
        assertThat(resolver.resolve(steps)).isEqualTo(OnboardingRequestState.IN_PROGRESS);
    }

    @Test
    void allSucceeded_resolveToCompleted() {
        List<OnboardingStep> steps = List.of(
            stepWith(OnboardingStepState.SUCCEEDED),
            stepWith(OnboardingStepState.SUCCEEDED),
            stepWith(OnboardingStepState.SUCCEEDED)
        );
        assertThat(resolver.resolve(steps)).isEqualTo(OnboardingRequestState.COMPLETED);
    }

    @Test
    void mixedSucceededAndFailed_resolveToPartialSuccess() {
        List<OnboardingStep> steps = List.of(
            stepWith(OnboardingStepState.SUCCEEDED),
            stepWith(OnboardingStepState.SUCCEEDED),
            stepWith(OnboardingStepState.FAILED)
        );
        assertThat(resolver.resolve(steps)).isEqualTo(OnboardingRequestState.PARTIAL_SUCCESS);
    }

    @Test
    void allFailed_resolveToFailed() {
        List<OnboardingStep> steps = List.of(
            stepWith(OnboardingStepState.FAILED),
            stepWith(OnboardingStepState.FAILED)
        );
        assertThat(resolver.resolve(steps)).isEqualTo(OnboardingRequestState.FAILED);
    }

    @Test
    void singleSucceeded_resolveToCompleted() {
        assertThat(resolver.resolve(List.of(stepWith(OnboardingStepState.SUCCEEDED))))
            .isEqualTo(OnboardingRequestState.COMPLETED);
    }

    @Test
    void singleFailed_resolveToFailed() {
        assertThat(resolver.resolve(List.of(stepWith(OnboardingStepState.FAILED))))
            .isEqualTo(OnboardingRequestState.FAILED);
    }

    private OnboardingStep stepWith(OnboardingStepState state) {
        OnboardingStep step = new OnboardingStep();
        step.setState(state);
        return step;
    }
}