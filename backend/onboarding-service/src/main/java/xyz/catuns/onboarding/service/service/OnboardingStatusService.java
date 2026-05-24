package xyz.catuns.onboarding.service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.service.api.dto.OnboardingStatusResponse;
import xyz.catuns.onboarding.service.api.dto.ProviderTargetDto;
import xyz.catuns.onboarding.service.api.dto.StepDetailDto;
import xyz.catuns.onboarding.service.domain.OnboardingRequest;
import xyz.catuns.onboarding.service.domain.OnboardingStep;
import xyz.catuns.onboarding.service.domain.ProviderTarget;
import xyz.catuns.onboarding.service.exception.OnboardingAccessDeniedException;
import xyz.catuns.onboarding.service.exception.ResourceNotFoundException;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;
import xyz.catuns.onboarding.service.repository.ProviderTargetRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OnboardingStatusService {

    private final OnboardingRequestRepository requestRepository;
    private final OnboardingStepRepository stepRepository;
    private final ProviderTargetRepository providerTargetRepository;

    public OnboardingStatusService(
        OnboardingRequestRepository requestRepository,
        OnboardingStepRepository stepRepository,
        ProviderTargetRepository providerTargetRepository
    ) {
        this.requestRepository = requestRepository;
        this.stepRepository = stepRepository;
        this.providerTargetRepository = providerTargetRepository;
    }

    @Transactional(readOnly = true)
    public OnboardingStatusResponse findById(UUID requestId, UUID callerId) {
        OnboardingRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("OnboardingRequest", requestId));

        if (!request.getUserProfileId().equals(callerId)) {
            throw new OnboardingAccessDeniedException(requestId);
        }

        List<OnboardingStep> steps = stepRepository.findByOnboardingRequest_Id(requestId);

        Set<UUID> targetIds = steps.stream()
            .map(OnboardingStep::getProviderTargetId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<UUID, ProviderTarget> targetCache = providerTargetRepository.findAllById(targetIds)
            .stream()
            .collect(Collectors.toMap(ProviderTarget::getId, Function.identity()));

        List<StepDetailDto> stepDtos = steps.stream()
            .map(s -> toStepDetailDto(s, targetCache))
            .toList();

        return new OnboardingStatusResponse(
            request.getId(),
            request.getUserProfileId(),
            request.getState().name(),
            request.getCorrelationId(),
            request.getStartedAt(),
            stepDtos
        );
    }

    private StepDetailDto toStepDetailDto(OnboardingStep step, Map<UUID, ProviderTarget> targetCache) {
        ProviderTargetDto targetDto = null;
        if (step.getProviderTargetId() != null) {
            ProviderTarget target = targetCache.get(step.getProviderTargetId());
            if (target != null) {
                targetDto = new ProviderTargetDto(
                    deriveProvider(target.getTargetType()),
                    target.getTargetType(),
                    target.getExternalKey()
                );
            }
        }

        return new StepDetailDto(
            step.getStepType().getStepKey().name(),
            step.getState().name(),
            targetDto,
            step.getAttemptCount(),
            step.getLastErrorCode(),
            step.getStartedAt(),
            step.getCompletedAt()
        );
    }

    private static String deriveProvider(String targetType) {
        if (targetType.startsWith("GITHUB")) return "GITHUB";
        if (targetType.startsWith("ATLASSIAN")) return "ATLASSIAN";
        return targetType;
    }
}
