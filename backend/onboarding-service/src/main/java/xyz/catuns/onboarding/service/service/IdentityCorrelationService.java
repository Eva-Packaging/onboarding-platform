package xyz.catuns.onboarding.service.service;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.common.events.IdentityCorrelationRequestedV1;
import xyz.catuns.onboarding.service.client.ExternalIdentityLookupResponse;
import xyz.catuns.onboarding.service.client.IdentityLinkCreateRequest;
import xyz.catuns.onboarding.service.client.MatchStrategy;
import xyz.catuns.onboarding.service.client.UserServiceFeignClient;
import xyz.catuns.onboarding.service.domain.OutboxEvent;
import xyz.catuns.onboarding.service.outbox.payload.OutboxPayloadBuilderService;
import xyz.catuns.onboarding.service.repository.OutboxEventRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class IdentityCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityCorrelationService.class);
    private static final String NO_MATCHING_ATLASSIAN_IDENTITY = "NO_MATCHING_ATLASSIAN_IDENTITY";

    @Value("${app.kafka.topics.id-correlation}")
    private String idCorrelationTopicName;

    private final UserServiceFeignClient userServiceFeignClient;
    private final OutboxEventRepository outboxRepo;
    private final OutboxPayloadBuilderService payloadBuilder;

    public IdentityCorrelationService(UserServiceFeignClient userServiceFeignClient,
            OutboxEventRepository outboxRepo, OutboxPayloadBuilderService payloadBuilder) {
        this.userServiceFeignClient = userServiceFeignClient;
        this.outboxRepo = outboxRepo;
        this.payloadBuilder = payloadBuilder;
    }

    @Transactional
    public void handleIdentityCorrelationRequested(IdentityCorrelationRequestedV1 event) {
        String userId = event.getUserId();
        String onboardingRequestId = event.getOnboardingRequestId();
        String correlationId = event.getCorrelationId();
        String githubIdentityId = event.getGithubIdentityId();
        String primaryEmail = event.getPrimaryEmail();

        if (primaryEmail == null) {
            saveFailedOutbox(userId, onboardingRequestId, correlationId,
                    "No primary email available for identity correlation");
            return;
        }

        ExternalIdentityLookupResponse atlassianIdentity;
        try {
            atlassianIdentity = userServiceFeignClient.getAtlassianIdentityByEmail(primaryEmail);
        } catch (FeignException.NotFound e) {
            saveFailedOutbox(userId, onboardingRequestId, correlationId,
                    "No Atlassian identity found for email " + primaryEmail);
            return;
        }

        ExternalIdentityLookupResponse githubIdentity = userServiceFeignClient
                .getGithubIdentityByUserProfileId(UUID.fromString(userId));

        userServiceFeignClient.createIdentityLink(new IdentityLinkCreateRequest(
                UUID.fromString(userId),
                githubIdentity.id(),
                atlassianIdentity.id(),
                MatchStrategy.EMAIL_EXACT,
                BigDecimal.ONE));

        log.info("Identity correlation matched for userId={} atlassianAccountId={}",
                userId, atlassianIdentity.accountId());

        String payload = payloadBuilder.buildIdentityCorrelationCompleted(userId, onboardingRequestId, correlationId,
                githubIdentityId, atlassianIdentity.accountId(), MatchStrategy.EMAIL_EXACT.name(), 1.0, true);
        outboxRepo.save(newOutboxEvent(UUID.fromString(onboardingRequestId), "IdentityCorrelationCompletedV1",
                correlationId, payload));
    }

    private void saveFailedOutbox(String userId, String onboardingRequestId, String correlationId, String reasonMessage) {
        log.info("Identity correlation failed for userId={} reason={}", userId, reasonMessage);
        String payload = payloadBuilder.buildIdentityCorrelationFailed(userId, onboardingRequestId, correlationId,
                NO_MATCHING_ATLASSIAN_IDENTITY, reasonMessage);
        outboxRepo.save(newOutboxEvent(UUID.fromString(onboardingRequestId), "IdentityCorrelationFailedV1",
                correlationId, payload));
    }

    private OutboxEvent newOutboxEvent(UUID aggregateId, String eventType, String correlationId, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.setAggregateType("OnboardingRequest");
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        e.setTopic(idCorrelationTopicName);
        e.setCorrelationId(correlationId);
        e.setPayload(payload);
        return e;
    }
}