package xyz.catuns.onboarding.service.domain;

import org.junit.jupiter.api.Test;
import xyz.catuns.onboarding.service.exception.IllegalStateTransitionException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnboardingStepStateMachineTest {

    private final OnboardingStepStateMachine stateMachine = new OnboardingStepStateMachine();

    // --- valid transitions ---

    @Test
    void pendingToProcessing_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.PENDING);
        stateMachine.transitionTo(step, OnboardingStepState.PROCESSING);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.PROCESSING);
    }

    @Test
    void processingToSucceeded_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.PROCESSING);
        stateMachine.transitionTo(step, OnboardingStepState.SUCCEEDED);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.SUCCEEDED);
    }

    @Test
    void processingToFailed_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.PROCESSING);
        stateMachine.transitionTo(step, OnboardingStepState.FAILED);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.FAILED);
    }

    @Test
    void processingToPendingExternalAcceptance_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.PROCESSING);
        stateMachine.transitionTo(step, OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE);
    }

    @Test
    void processingToManualReview_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.PROCESSING);
        stateMachine.transitionTo(step, OnboardingStepState.MANUAL_REVIEW);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.MANUAL_REVIEW);
    }

    @Test
    void pendingExternalAcceptanceToSucceeded_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE);
        stateMachine.transitionTo(step, OnboardingStepState.SUCCEEDED);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.SUCCEEDED);
    }

    @Test
    void pendingExternalAcceptanceToFailed_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE);
        stateMachine.transitionTo(step, OnboardingStepState.FAILED);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.FAILED);
    }

    @Test
    void failedToProcessing_isValid_forRetry() {
        OnboardingStep step = stepWith(OnboardingStepState.FAILED);
        stateMachine.transitionTo(step, OnboardingStepState.PROCESSING);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.PROCESSING);
    }

    @Test
    void manualReviewToProcessing_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.MANUAL_REVIEW);
        stateMachine.transitionTo(step, OnboardingStepState.PROCESSING);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.PROCESSING);
    }

    @Test
    void manualReviewToFailed_isValid() {
        OnboardingStep step = stepWith(OnboardingStepState.MANUAL_REVIEW);
        stateMachine.transitionTo(step, OnboardingStepState.FAILED);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.FAILED);
    }

    // --- invalid transitions ---

    @Test
    void succeededToPending_isRejected() {
        OnboardingStep step = stepWith(OnboardingStepState.SUCCEEDED);
        assertThatThrownBy(() -> stateMachine.transitionTo(step, OnboardingStepState.PENDING))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void succeededToProcessing_isRejected() {
        OnboardingStep step = stepWith(OnboardingStepState.SUCCEEDED);
        assertThatThrownBy(() -> stateMachine.transitionTo(step, OnboardingStepState.PROCESSING))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void succeededToFailed_isRejected() {
        OnboardingStep step = stepWith(OnboardingStepState.SUCCEEDED);
        assertThatThrownBy(() -> stateMachine.transitionTo(step, OnboardingStepState.FAILED))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void pendingToSucceeded_isRejected() {
        OnboardingStep step = stepWith(OnboardingStepState.PENDING);
        assertThatThrownBy(() -> stateMachine.transitionTo(step, OnboardingStepState.SUCCEEDED))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void failedToSucceeded_isRejected() {
        OnboardingStep step = stepWith(OnboardingStepState.FAILED);
        assertThatThrownBy(() -> stateMachine.transitionTo(step, OnboardingStepState.SUCCEEDED))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void exception_carriesEntityIdAndStates() {
        UUID id = UUID.randomUUID();
        OnboardingStep step = stepWith(OnboardingStepState.SUCCEEDED);
        step.setId(id);

        assertThatThrownBy(() -> stateMachine.transitionTo(step, OnboardingStepState.PENDING))
            .isInstanceOf(IllegalStateTransitionException.class)
            .satisfies(ex -> {
                IllegalStateTransitionException e = (IllegalStateTransitionException) ex;
                assertThat(e.getEntityId()).isEqualTo(id);
                assertThat(e.getFrom()).isEqualTo(OnboardingStepState.SUCCEEDED);
                assertThat(e.getTo()).isEqualTo(OnboardingStepState.PENDING);
            });
    }

    private OnboardingStep stepWith(OnboardingStepState state) {
        OnboardingStep step = new OnboardingStep();
        step.setState(state);
        return step;
    }
}