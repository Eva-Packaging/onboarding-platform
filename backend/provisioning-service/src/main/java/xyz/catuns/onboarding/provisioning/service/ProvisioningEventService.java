package xyz.catuns.onboarding.provisioning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningRequestedV1;
import xyz.catuns.onboarding.common.events.GithubProvisioningRequestedV1;
import xyz.catuns.onboarding.provisioning.domain.OutboxEvent;
import xyz.catuns.onboarding.provisioning.domain.ProvisioningAuditLog;
import xyz.catuns.onboarding.provisioning.domain.ResultState;
import xyz.catuns.onboarding.provisioning.repository.OutboxEventRepository;
import xyz.catuns.onboarding.provisioning.repository.ProvisioningAuditLogRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProvisioningEventService {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningEventService.class);

    private final ProvisioningAuditLogRepository auditLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.github-provisioning}")
    private String githubProvisioningTopic;

    @Value("${app.kafka.topics.atlassian-provisioning}")
    private String atlassianProvisioningTopic;

    public ProvisioningEventService(ProvisioningAuditLogRepository auditLogRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleGithubProvisioningRequested(GithubProvisioningRequestedV1 event) {
        log.info("Processing GithubProvisioningRequestedV1 userId={} requestId={}",
                event.getUserId(), event.getOnboardingRequestId());

        UUID userId = UUID.fromString(event.getUserId().toString());
        UUID requestId = UUID.fromString(event.getOnboardingRequestId().toString());
        UUID providerTargetId = UUID.fromString(event.getProviderTargetId().toString());
        UUID correlationId = UUID.fromString(event.getCorrelationId().toString());

        try {
            ProvisioningAuditLog auditLog = new ProvisioningAuditLog();
            // Phase 4 stub: onboardingRequestId used as step proxy until step IDs are propagated in Phase 5
            auditLog.setOnboardingStepId(requestId);
            auditLog.setProviderId(providerTargetId);
            auditLog.setActionName("GITHUB_PROVISION");
            auditLog.setRequestPayload(buildGithubRequestPayload(event));
            auditLog.setResultState(ResultState.SUCCESS);
            auditLog.setCorrelationId(correlationId);
            auditLogRepository.save(auditLog);

            OutboxEvent outbox = new OutboxEvent();
            outbox.setAggregateType("User");
            outbox.setAggregateId(userId);
            outbox.setEventType("GithubProvisioningCompletedV1");
            outbox.setPayload(buildGithubCompletionPayload(event));
            outbox.setTopic(githubProvisioningTopic);
            outbox.setCorrelationId(event.getCorrelationId().toString());
            outboxEventRepository.save(outbox);

            log.debug("Wrote audit log and outbox entry for GithubProvisioningRequestedV1 userId={}", userId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build payload for GithubProvisioningRequestedV1 eventId=" + event.getEventId(), e);
        }
    }

    @Transactional
    public void handleAtlassianProvisioningRequested(AtlassianProvisioningRequestedV1 event) {
        log.info("Processing AtlassianProvisioningRequestedV1 userId={} requestId={}",
                event.getUserId(), event.getOnboardingRequestId());

        UUID userId = UUID.fromString(event.getUserId().toString());
        UUID requestId = UUID.fromString(event.getOnboardingRequestId().toString());
        UUID providerTargetId = UUID.fromString(event.getProviderTargetId().toString());
        UUID correlationId = UUID.fromString(event.getCorrelationId().toString());

        try {
            ProvisioningAuditLog auditLog = new ProvisioningAuditLog();
            auditLog.setOnboardingStepId(requestId);
            auditLog.setProviderId(providerTargetId);
            auditLog.setActionName("ATLASSIAN_PROVISION");
            auditLog.setRequestPayload(buildAtlassianRequestPayload(event));
            auditLog.setResultState(ResultState.SUCCESS);
            auditLog.setCorrelationId(correlationId);
            auditLogRepository.save(auditLog);

            OutboxEvent outbox = new OutboxEvent();
            outbox.setAggregateType("User");
            outbox.setAggregateId(userId);
            outbox.setEventType("AtlassianProvisioningCompletedV1");
            outbox.setPayload(buildAtlassianCompletionPayload(event));
            outbox.setTopic(atlassianProvisioningTopic);
            outbox.setCorrelationId(event.getCorrelationId().toString());
            outboxEventRepository.save(outbox);

            log.debug("Wrote audit log and outbox entry for AtlassianProvisioningRequestedV1 userId={}", userId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build payload for AtlassianProvisioningRequestedV1 eventId=" + event.getEventId(), e);
        }
    }

    private String buildGithubRequestPayload(GithubProvisioningRequestedV1 event) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getEventId().toString());
        payload.put("userId", event.getUserId().toString());
        payload.put("onboardingRequestId", event.getOnboardingRequestId().toString());
        payload.put("githubLogin", event.getGithubLogin().toString());
        payload.put("githubOrg", event.getGithubOrg().toString());
        payload.put("githubTeamSlug", event.getGithubTeamSlug().toString());
        payload.put("providerTargetId", event.getProviderTargetId().toString());
        return objectMapper.writeValueAsString(payload);
    }

    private String buildGithubCompletionPayload(GithubProvisioningRequestedV1 event) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "GithubProvisioningCompletedV1");
        payload.put("eventVersion", 1);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("correlationId", event.getCorrelationId().toString());
        payload.put("producer", "provisioning-service");
        payload.put("userId", event.getUserId().toString());
        payload.put("onboardingRequestId", event.getOnboardingRequestId().toString());
        payload.put("providerTargetId", event.getProviderTargetId().toString());
        payload.put("membershipState", "ACTIVE");
        payload.put("success", true);
        payload.put("errorCode", null);
        payload.put("errorMessage", null);
        return objectMapper.writeValueAsString(payload);
    }

    private String buildAtlassianRequestPayload(AtlassianProvisioningRequestedV1 event) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getEventId().toString());
        payload.put("userId", event.getUserId().toString());
        payload.put("onboardingRequestId", event.getOnboardingRequestId().toString());
        payload.put("groupName", event.getGroupName().toString());
        payload.put("providerTargetId", event.getProviderTargetId().toString());
        return objectMapper.writeValueAsString(payload);
    }

    private String buildAtlassianCompletionPayload(AtlassianProvisioningRequestedV1 event) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "AtlassianProvisioningCompletedV1");
        payload.put("eventVersion", 1);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("correlationId", event.getCorrelationId().toString());
        payload.put("producer", "provisioning-service");
        payload.put("userId", event.getUserId().toString());
        payload.put("onboardingRequestId", event.getOnboardingRequestId().toString());
        payload.put("providerTargetId", event.getProviderTargetId().toString());
        payload.put("success", true);
        payload.put("errorCode", null);
        payload.put("errorMessage", null);
        return objectMapper.writeValueAsString(payload);
    }
}