package xyz.catuns.onboarding.service.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.service.domain.*;
import xyz.catuns.onboarding.service.exception.IllegalStateTransitionException;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;

import java.util.List;
import java.util.Set;

@Service
public class OnboardingDomainService {

    private final OnboardingRequestRepository requestRepository;
    private final OnboardingStepRepository stepRepository;
    private final OnboardingStepStateMachine stepStateMachine;
    private final OnboardingRequestStateResolver requestStateResolver;
    private final MeterRegistry meterRegistry;

    public OnboardingDomainService(
        OnboardingRequestRepository requestRepository,
        OnboardingStepRepository stepRepository,
        OnboardingStepStateMachine stepStateMachine,
        OnboardingRequestStateResolver requestStateResolver,
        MeterRegistry meterRegistry
    ) {
        this.requestRepository = requestRepository;
        this.stepRepository = stepRepository;
        this.stepStateMachine = stepStateMachine;
        this.requestStateResolver = requestStateResolver;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public OnboardingRequest startRequest(OnboardingRequest request) {
        OnboardingRequestState current = request.getState();
        Set<OnboardingRequestState> allowed =
            RequestStateTransitions.ALLOWED.getOrDefault(current, Set.of());
        if (!allowed.contains(OnboardingRequestState.IN_PROGRESS)) {
            throw new IllegalStateTransitionException(
                request.getId(), current, OnboardingRequestState.IN_PROGRESS);
        }
        request.setState(OnboardingRequestState.IN_PROGRESS);
        return requestRepository.save(request);
    }

    @Transactional
    public OnboardingStep transitionStep(OnboardingStep step, OnboardingStepState target) {
        stepStateMachine.transitionTo(step, target);
        if (target == OnboardingStepState.PROCESSING) {
            step.setAttemptCount(step.getAttemptCount() + 1);
        }
        if (target == OnboardingStepState.FAILED) {
            String stepTypeName = step.getStepType() != null
                    ? step.getStepType().getStepKey().name()
                    : "UNKNOWN";
            meterRegistry.counter("onboarding.step.failures", "step_type", stepTypeName).increment();
        }
        OnboardingStep saved = stepRepository.save(step);
        recalculateRequestState(step.getOnboardingRequest());
        return saved;
    }

    @Transactional
    public void recalculateRequestState(OnboardingRequest request) {
        List<OnboardingStep> steps = stepRepository.findByOnboardingRequest_Id(request.getId());
        OnboardingRequestState resolved = requestStateResolver.resolve(steps);
        if (!resolved.equals(request.getState())) {
            request.setState(resolved);
            requestRepository.save(request);
        }
    }
}