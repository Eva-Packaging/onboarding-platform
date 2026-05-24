package xyz.catuns.onboarding.service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.service.api.dto.OnboardingRetryRequest;
import xyz.catuns.onboarding.service.api.dto.OnboardingRetryResponse;
import xyz.catuns.onboarding.service.domain.OnboardingRequest;
import xyz.catuns.onboarding.service.domain.OnboardingStep;
import xyz.catuns.onboarding.service.domain.OnboardingStepState;
import xyz.catuns.onboarding.service.exception.ResourceNotFoundException;
import xyz.catuns.onboarding.service.exception.StepNotRetryableException;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OnboardingRetryService {

    private static final Set<OnboardingStepState> RETRYABLE = Set.of(
        OnboardingStepState.FAILED, OnboardingStepState.MANUAL_REVIEW
    );

    private final OnboardingRequestRepository requestRepository;
    private final OnboardingStepRepository stepRepository;
    private final OnboardingDomainService domainService;

    public OnboardingRetryService(
        OnboardingRequestRepository requestRepository,
        OnboardingStepRepository stepRepository,
        OnboardingDomainService domainService
    ) {
        this.requestRepository = requestRepository;
        this.stepRepository = stepRepository;
        this.domainService = domainService;
    }

    @Transactional
    public OnboardingRetryResponse retry(UUID requestId, OnboardingRetryRequest retryRequest) {
        OnboardingRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("OnboardingRequest", requestId));

        List<OnboardingStep> allSteps = stepRepository.findByOnboardingRequest_Id(requestId);
        Map<String, OnboardingStep> stepsByType = allSteps.stream()
            .collect(Collectors.toMap(
                s -> s.getStepType().getStepKey().name(),
                Function.identity()
            ));

        List<OnboardingStep> stepsToRetry = new ArrayList<>();
        for (String stepKey : retryRequest.steps()) {
            OnboardingStep step = stepsByType.get(stepKey);
            if (step == null || !RETRYABLE.contains(step.getState())) {
                throw new StepNotRetryableException(stepKey, step == null ? null : step.getState());
            }
            stepsToRetry.add(step);
        }

        for (OnboardingStep step : stepsToRetry) {
            domainService.transitionStep(step, OnboardingStepState.PENDING);
        }

        return new OnboardingRetryResponse(
            requestId,
            request.getState().name(),
            retryRequest.steps(),
            request.getCorrelationId()
        );
    }
}
