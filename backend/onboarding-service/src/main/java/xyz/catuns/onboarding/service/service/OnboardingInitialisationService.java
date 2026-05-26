package xyz.catuns.onboarding.service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitRequest;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitResponse;
import xyz.catuns.onboarding.service.api.dto.OnboardingLatestResponse;
import xyz.catuns.onboarding.service.api.dto.StepSummaryDto;
import xyz.catuns.onboarding.service.domain.*;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepTypeRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class OnboardingInitialisationService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingInitialisationService.class);

    private static final List<StepType> INITIAL_STEP_TYPES = List.of(
        StepType.IDENTITY_CORRELATION,
        StepType.GITHUB_TEAM_PROVISIONING,
        StepType.JIRA_GROUP_PROVISIONING
    );

    private static final Map<StepType, String> TARGET_TYPE_BY_STEP = Map.of(
        StepType.GITHUB_TEAM_PROVISIONING, "GITHUB_TEAM",
        StepType.JIRA_GROUP_PROVISIONING,  "ATLASSIAN_GROUP"
    );

    private final OnboardingRequestRepository requestRepository;
    private final OnboardingStepRepository stepRepository;
    private final OnboardingStepTypeRepository stepTypeRepository;
    private final OnboardingDomainService domainService;
    private final ProviderTargetResolutionService resolutionService;

    public OnboardingInitialisationService(
        OnboardingRequestRepository requestRepository,
        OnboardingStepRepository stepRepository,
        OnboardingStepTypeRepository stepTypeRepository,
        OnboardingDomainService domainService,
        ProviderTargetResolutionService resolutionService
    ) {
        this.requestRepository = requestRepository;
        this.stepRepository = stepRepository;
        this.stepTypeRepository = stepTypeRepository;
        this.domainService = domainService;
        this.resolutionService = resolutionService;
    }

    @Transactional
    public OnboardingInitResponse initialise(OnboardingInitRequest request) {
        OnboardingRequest onboardingRequest = new OnboardingRequest();
        onboardingRequest.setUserProfileId(request.userId());
        onboardingRequest.setState(OnboardingRequestState.REQUESTED);
        onboardingRequest.setSource(OnboardingSource.SELF_REGISTRATION);
        onboardingRequest.setCorrelationId(request.correlationId());

        onboardingRequest = requestRepository.save(onboardingRequest);
        onboardingRequest = domainService.startRequest(onboardingRequest);

        Collection<String> roleKeys = request.roleKeys() != null ? request.roleKeys() : List.of();

        List<StepSummaryDto> stepDtos = new ArrayList<>();
        for (StepType stepType : INITIAL_STEP_TYPES) {
            OnboardingStepType stepTypeEntity = stepTypeRepository.findByStepKey(stepType)
                .orElseThrow(() -> new NoSuchElementException("Step type not found: " + stepType));

            OnboardingStep step = new OnboardingStep();
            step.setOnboardingRequest(onboardingRequest);
            step.setStepType(stepTypeEntity);
            step.setState(OnboardingStepState.PENDING);

            String targetType = TARGET_TYPE_BY_STEP.get(stepType);
            if (targetType != null) {
                Optional<UUID> resolved = resolutionService.resolveTarget(roleKeys, targetType);
                if (resolved.isPresent()) {
                    step.setProviderTargetId(resolved.get());
                } else {
                    log.warn("No provider target resolved for stepType={} roleKeys={}", stepType, roleKeys);
                }
            }

            stepRepository.save(step);
            stepDtos.add(new StepSummaryDto(stepType.name(), OnboardingStepState.PENDING.name()));
        }

        return new OnboardingInitResponse(
            onboardingRequest.getId(),
            onboardingRequest.getState().name(),
            stepDtos
        );
    }

    @Transactional(readOnly = true)
    public Optional<OnboardingLatestResponse> findLatestForUser(String userId) {
        return requestRepository.findTopByUserProfileIdOrderByCreatedAtDesc(userId)
            .map(r -> new OnboardingLatestResponse(r.getId(), r.getState().name()));
    }
}