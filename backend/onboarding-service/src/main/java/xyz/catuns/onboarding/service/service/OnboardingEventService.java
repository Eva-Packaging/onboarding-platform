package xyz.catuns.onboarding.service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningCompletedV1;
import xyz.catuns.onboarding.common.events.GithubProvisioningCompletedV1;
import xyz.catuns.onboarding.common.events.IdentityCorrelationCompletedV1;
import xyz.catuns.onboarding.common.events.IdentityCorrelationFailedV1;
import xyz.catuns.onboarding.common.events.UserRegisteredV1;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitRequest;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitResponse;
import xyz.catuns.onboarding.service.domain.OnboardingRequest;
import xyz.catuns.onboarding.service.domain.OnboardingRequestState;
import xyz.catuns.onboarding.service.domain.OnboardingStep;
import xyz.catuns.onboarding.service.domain.OnboardingStepState;
import xyz.catuns.onboarding.service.domain.OutboxEvent;
import xyz.catuns.onboarding.service.domain.ProviderTarget;
import xyz.catuns.onboarding.service.domain.StepType;
import xyz.catuns.onboarding.service.outbox.payload.OutboxPayloadBuilderService;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;
import xyz.catuns.onboarding.service.repository.OutboxEventRepository;
import xyz.catuns.onboarding.service.repository.ProviderTargetRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OnboardingEventService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingEventService.class);

    @Value("${app.kafka.topics.onboarding-lifecycle}")
    private String lifecycleTopicName;

    @Value("${app.kafka.topics.id-correlation}")
    private String idCorrelationTopicName;

    @Value("${app.kafka.topics.github-provisioning}")
    private String githubProvisioningTopicName;

    @Value("${app.kafka.topics.atlassian-provisioning}")
    private String atlassianProvisioningTopicName;

    private final OnboardingRequestRepository requestRepository;
    private final OnboardingStepRepository stepRepository;
    private final OnboardingInitialisationService initialisationService;
    private final OnboardingDomainService domainService;
    private final ProviderTargetRepository providerTargetRepository;
    private final OutboxEventRepository outboxRepo;
    private final OutboxPayloadBuilderService payloadBuilder;

    public OnboardingEventService(
            OnboardingRequestRepository requestRepository,
            OnboardingStepRepository stepRepository,
            OnboardingInitialisationService initialisationService,
            OnboardingDomainService domainService,
            ProviderTargetRepository providerTargetRepository,
            OutboxEventRepository outboxRepo,
            OutboxPayloadBuilderService payloadBuilder) {
        this.requestRepository = requestRepository;
        this.stepRepository = stepRepository;
        this.initialisationService = initialisationService;
        this.domainService = domainService;
        this.providerTargetRepository = providerTargetRepository;
        this.outboxRepo = outboxRepo;
        this.payloadBuilder = payloadBuilder;
    }

    @Transactional
    public void handleUserRegistered(UserRegisteredV1 event) {
        String userId = event.getUserId();
        String httpCorrelationId = event.getCorrelationId();

        // Normal path: OnboardingRequest was already created synchronously by user-service
        // via the internal API before this event was published. Look it up; fall back to
        // creating it here only if the event somehow arrived before the API call completed.
        OnboardingRequest request = requestRepository
                .findTopByUserProfileIdOrderByCreatedAtDesc(userId)
                .orElseGet(() -> {
                    log.warn("OnboardingRequest not found for userId={}, creating fallback", userId);
                    UUID correlationId = UUID.fromString(event.getOnboardingRequestId());
                    List<String> roleKeys = event.getRoleKeys().stream()
                            .map(CharSequence::toString)
                            .collect(Collectors.toList());
                    OnboardingInitResponse resp = initialisationService.initialise(
                            new OnboardingInitRequest(userId, correlationId, roleKeys));
                    return requestRepository.findById(resp.requestId()).orElseThrow();
                });

        UUID requestId = request.getId();
        List<OnboardingStep> steps = stepRepository.findByOnboardingRequest_Id(requestId);

        for (OnboardingStep step : steps) {
            if (step.getState() != OnboardingStepState.PENDING) {
                // Already dispatched — guard against duplicate event delivery
                log.debug("Step {} already in state {}, skipping dispatch for request {}",
                        step.getStepType().getStepKey(), step.getState(), requestId);
                continue;
            }
            switch (step.getStepType().getStepKey()) {
                case IDENTITY_CORRELATION -> {
                    domainService.transitionStep(step, OnboardingStepState.PROCESSING);
                    outboxRepo.save(buildIdentityCorrelationOutbox(step, event, requestId.toString(), httpCorrelationId));
                }
                case GITHUB_TEAM_PROVISIONING -> {
                    if (step.getProviderTargetId() == null) {
                        log.warn("No providerTarget for GITHUB step on request {}, skipping dispatch", requestId);
                        break;
                    }
                    ProviderTarget target = providerTargetRepository.findById(step.getProviderTargetId()).orElse(null);
                    if (target == null) {
                        log.warn("ProviderTarget {} not found, skipping GitHub dispatch", step.getProviderTargetId());
                        break;
                    }
                    domainService.transitionStep(step, OnboardingStepState.PROCESSING);
                    outboxRepo.save(buildGithubOutbox(step, event, requestId.toString(), httpCorrelationId, target));
                }
                case JIRA_GROUP_PROVISIONING -> {
                    if (step.getProviderTargetId() == null) {
                        log.warn("No providerTarget for JIRA step on request {}, skipping dispatch", requestId);
                        break;
                    }
                    ProviderTarget target = providerTargetRepository.findById(step.getProviderTargetId()).orElse(null);
                    if (target == null) {
                        log.warn("ProviderTarget {} not found, skipping Atlassian dispatch", step.getProviderTargetId());
                        break;
                    }
                    domainService.transitionStep(step, OnboardingStepState.PROCESSING);
                    outboxRepo.save(buildAtlassianOutbox(step, event, requestId.toString(), httpCorrelationId, target));
                }
            }
        }

        log.info("Dispatched onboarding events for request {} userId={}", requestId, userId);
    }

    @Transactional
    public void handleIdentityCorrelationCompleted(IdentityCorrelationCompletedV1 event) {
        OnboardingRequest request = findRequest(event.getOnboardingRequestId());
        if (request == null) return;

        OnboardingStep step = findStep(request.getId(), StepType.IDENTITY_CORRELATION);
        if (step == null) return;

        log.info("Identity correlated for request={} matched={} atlassianIdentityId={} matchStrategy={}",
                event.getOnboardingRequestId(), event.getMatched(),
                event.getAtlassianIdentityId(), event.getMatchStrategy());

        domainService.transitionStep(step, OnboardingStepState.SUCCEEDED);
        emitLifecycleEventIfTerminal(request, event.getUserId(),
                event.getOnboardingRequestId(), event.getCorrelationId());
    }

    @Transactional
    public void handleIdentityCorrelationFailed(IdentityCorrelationFailedV1 event) {
        OnboardingRequest request = findRequest(event.getOnboardingRequestId());
        if (request == null) return;

        OnboardingStep step = findStep(request.getId(), StepType.IDENTITY_CORRELATION);
        if (step == null) return;

        step.setLastErrorCode(event.getReasonCode());
        step.setLastErrorMessage(event.getReasonMessage());
        domainService.transitionStep(step, OnboardingStepState.FAILED);
        emitLifecycleEventIfTerminal(request, event.getUserId(),
                event.getOnboardingRequestId(), event.getCorrelationId());
    }

    @Transactional
    public void handleGithubProvisioningCompleted(GithubProvisioningCompletedV1 event) {
        OnboardingRequest request = findRequest(event.getOnboardingRequestId());
        if (request == null) return;

        OnboardingStep step = findStep(request.getId(), StepType.GITHUB_TEAM_PROVISIONING);
        if (step == null) return;

        String membershipState = event.getMembershipState() != null ? event.getMembershipState().toString() : "";
        OnboardingStepState target = switch (membershipState) {
            case "ACTIVE"   -> OnboardingStepState.SUCCEEDED;
            case "PENDING"  -> OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE;
            default         -> {
                step.setLastErrorCode(event.getErrorCode() != null ? event.getErrorCode().toString() : null);
                step.setLastErrorMessage(event.getErrorMessage() != null ? event.getErrorMessage().toString() : null);
                yield OnboardingStepState.FAILED;
            }
        };
        domainService.transitionStep(step, target);
        emitLifecycleEventIfTerminal(request, event.getUserId(),
                event.getOnboardingRequestId(), event.getCorrelationId());
    }

    @Transactional
    public void handleAtlassianProvisioningCompleted(AtlassianProvisioningCompletedV1 event) {
        OnboardingRequest request = findRequest(event.getOnboardingRequestId());
        if (request == null) return;

        OnboardingStep step = findStep(request.getId(), StepType.JIRA_GROUP_PROVISIONING);
        if (step == null) return;

        OnboardingStepState target = event.getSuccess() ? OnboardingStepState.SUCCEEDED : OnboardingStepState.FAILED;
        if (!event.getSuccess()) {
            step.setLastErrorCode(event.getErrorCode());
            step.setLastErrorMessage(event.getErrorMessage());
        }
        domainService.transitionStep(step, target);
        emitLifecycleEventIfTerminal(request, event.getUserId(),
                event.getOnboardingRequestId(), event.getCorrelationId());
    }

    private void emitLifecycleEventIfTerminal(OnboardingRequest request, String userId,
            String onboardingRequestId, String correlationId) {
        OnboardingRequestState state = requestRepository.findById(request.getId())
                .map(OnboardingRequest::getState)
                .orElseThrow();

        if (state == OnboardingRequestState.COMPLETED) {
            outboxRepo.save(newOutboxEvent(request.getId(), "OnboardingCompletedV1",
                    lifecycleTopicName, correlationId,
                    payloadBuilder.buildOnboardingCompleted(userId, onboardingRequestId, correlationId, state.name())));
            log.info("Onboarding {} completed for userId={}", onboardingRequestId, userId);

        } else if (state == OnboardingRequestState.FAILED || state == OnboardingRequestState.PARTIAL_SUCCESS) {
            List<OnboardingStep> steps = stepRepository.findByOnboardingRequest_Id(request.getId());
            String failureStep = steps.stream()
                    .filter(s -> s.getState() == OnboardingStepState.FAILED)
                    .map(s -> s.getStepType().getStepKey().name())
                    .findFirst()
                    .orElse("UNKNOWN");
            outboxRepo.save(newOutboxEvent(request.getId(), "OnboardingFailedV1",
                    lifecycleTopicName, correlationId,
                    payloadBuilder.buildOnboardingFailed(userId, onboardingRequestId, correlationId,
                            failureStep, null, null)));
            log.info("Onboarding {} {} for userId={}", onboardingRequestId, state, userId);
        }
    }

    private OutboxEvent buildIdentityCorrelationOutbox(OnboardingStep step, UserRegisteredV1 event,
            String requestId, String correlationId) {
        String payload = payloadBuilder.buildIdentityCorrelationRequested(
                event.getUserId(), requestId, correlationId,
                event.getGithubUserId(), event.getGithubLogin(), event.getPrimaryEmail());
        return newOutboxEvent(step.getOnboardingRequest().getId(),
                "IdentityCorrelationRequestedV1", idCorrelationTopicName, correlationId, payload);
    }

    private OutboxEvent buildGithubOutbox(OnboardingStep step, UserRegisteredV1 event,
            String requestId, String correlationId, ProviderTarget target) {
        String[] parts = target.getExternalKey().split("/", 2);
        String githubOrg = parts.length == 2 ? parts[0] : "";
        String githubTeamSlug = parts.length == 2 ? parts[1] : parts[0];
        String payload = payloadBuilder.buildGithubProvisioningRequested(
                event.getUserId(), requestId, correlationId,
                event.getGithubLogin(), githubOrg, githubTeamSlug, target.getId().toString());
        return newOutboxEvent(step.getOnboardingRequest().getId(),
                "GithubProvisioningRequestedV1", githubProvisioningTopicName, correlationId, payload);
    }

    private OutboxEvent buildAtlassianOutbox(OnboardingStep step, UserRegisteredV1 event,
            String requestId, String correlationId, ProviderTarget target) {
        String payload = payloadBuilder.buildAtlassianProvisioningRequested(
                event.getUserId(), requestId, correlationId,
                null, null, target.getExternalKey(), target.getId().toString());
        return newOutboxEvent(step.getOnboardingRequest().getId(),
                "AtlassianProvisioningRequestedV1", atlassianProvisioningTopicName, correlationId, payload);
    }

    private OutboxEvent newOutboxEvent(UUID aggregateId, String eventType, String topic,
            String correlationId, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.setAggregateType("OnboardingRequest");
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        e.setTopic(topic);
        e.setCorrelationId(correlationId);
        e.setPayload(payload);
        return e;
    }

    private OnboardingRequest findRequest(String onboardingRequestId) {
        try {
            return requestRepository.findById(UUID.fromString(onboardingRequestId)).orElseGet(() -> {
                log.warn("OnboardingRequest {} not found, skipping event", onboardingRequestId);
                return null;
            });
        } catch (IllegalArgumentException e) {
            log.warn("Invalid onboardingRequestId '{}', skipping event", onboardingRequestId);
            return null;
        }
    }

    private OnboardingStep findStep(UUID requestId, StepType stepType) {
        return stepRepository.findByOnboardingRequest_IdAndStepType_StepKey(requestId, stepType)
                .orElseGet(() -> {
                    log.warn("Step {} not found for request {}, skipping", stepType, requestId);
                    return null;
                });
    }
}