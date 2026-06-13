package xyz.catuns.onboarding.provisioning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningCompletedV1;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningRequestedV1;
import xyz.catuns.onboarding.common.events.GithubProvisioningCompletedV1;
import xyz.catuns.onboarding.common.events.GithubProvisioningRequestedV1;
import xyz.catuns.onboarding.provisioning.atlassian.AtlassianGroupMembershipResult;
import xyz.catuns.onboarding.provisioning.atlassian.AtlassianProvisioningAdapter;
import xyz.catuns.onboarding.provisioning.domain.OutboxEvent;
import xyz.catuns.onboarding.provisioning.domain.ProvisioningAuditLog;
import xyz.catuns.onboarding.provisioning.domain.ResultState;
import xyz.catuns.onboarding.provisioning.github.GithubMembershipResult;
import xyz.catuns.onboarding.provisioning.github.GithubProvisioningAdapter;
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
    private final GithubProvisioningAdapter githubAdapter;
    private final AtlassianProvisioningAdapter atlassianAdapter;

    @Value("${app.kafka.topics.github-provisioning}")
    private String githubProvisioningTopic;

    @Value("${app.kafka.topics.atlassian-provisioning}")
    private String atlassianProvisioningTopic;

    public ProvisioningEventService(ProvisioningAuditLogRepository auditLogRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    ObjectMapper objectMapper,
                                    GithubProvisioningAdapter githubAdapter,
                                    AtlassianProvisioningAdapter atlassianAdapter) {
        this.auditLogRepository = auditLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.githubAdapter = githubAdapter;
        this.atlassianAdapter = atlassianAdapter;
    }

    @Transactional
    public void handleGithubProvisioningRequested(GithubProvisioningRequestedV1 event) {
        log.info("Processing GithubProvisioningRequestedV1 userId={} requestId={}",
                event.getUserId(), event.getOnboardingRequestId());

        UUID userId = UUID.fromString(event.getUserId());
        UUID requestId = UUID.fromString(event.getOnboardingRequestId());
        UUID providerTargetId = UUID.fromString(event.getProviderTargetId());
        UUID correlationId = UUID.fromString(event.getCorrelationId());

        GithubMembershipResult result = githubAdapter.addTeamMember(
                event.getGithubLogin(),
                event.getGithubOrg(),
                event.getGithubTeamSlug()
        );

        try {
            ProvisioningAuditLog auditLog = new ProvisioningAuditLog();
            auditLog.setOnboardingStepId(requestId);
            auditLog.setProviderId(providerTargetId);
            auditLog.setActionName("GITHUB_PROVISION");
            auditLog.setRequestPayload(buildGithubRequestPayload(event));
            auditLog.setResponsePayload(buildGithubResponsePayload(result));
            auditLog.setResultState(toResultState(result.membershipState()));
            auditLog.setCorrelationId(correlationId);
            auditLogRepository.save(auditLog);

            OutboxEvent outbox = new OutboxEvent();
            outbox.setAggregateType("User");
            outbox.setAggregateId(userId);
            outbox.setEventType("GithubProvisioningCompletedV1");
            outbox.setPayload(toOutboxPayload(buildCompletedEvent(event, result)));
            outbox.setTopic(githubProvisioningTopic);
            outbox.setCorrelationId(event.getCorrelationId());
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

        UUID userId = UUID.fromString(event.getUserId());
        UUID requestId = UUID.fromString(event.getOnboardingRequestId());
        UUID providerTargetId = UUID.fromString(event.getProviderTargetId());
        UUID correlationId = UUID.fromString(event.getCorrelationId());

        AtlassianGroupMembershipResult result = atlassianAdapter.addGroupMember(
                event.getAtlassianIdentityId(),
                event.getAtlassianEmail(),
                event.getGroupName()
        );

        try {
            ProvisioningAuditLog auditLog = new ProvisioningAuditLog();
            auditLog.setOnboardingStepId(requestId);
            auditLog.setProviderId(providerTargetId);
            auditLog.setActionName("ATLASSIAN_PROVISION");
            auditLog.setRequestPayload(buildAtlassianRequestPayload(event));
            auditLog.setResponsePayload(buildAtlassianResponsePayload(result));
            auditLog.setResultState(toResultState(result.membershipState()));
            auditLog.setCorrelationId(correlationId);
            auditLogRepository.save(auditLog);

            OutboxEvent outbox = new OutboxEvent();
            outbox.setAggregateType("User");
            outbox.setAggregateId(userId);
            outbox.setEventType("AtlassianProvisioningCompletedV1");
            outbox.setPayload(toOutboxPayload(buildAtlassianCompletedEvent(event, result)));
            outbox.setTopic(atlassianProvisioningTopic);
            outbox.setCorrelationId(event.getCorrelationId());
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

    private String buildGithubResponsePayload(GithubMembershipResult result) throws JsonProcessingException {
        return objectMapper.writeValueAsString(result);
    }

    private GithubProvisioningCompletedV1 buildCompletedEvent(
            GithubProvisioningRequestedV1 event, GithubMembershipResult result) {
        return GithubProvisioningCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType("GithubProvisioningCompletedV1")
                .setEventVersion(1)
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(event.getCorrelationId().toString())
                .setProducer("provisioning-service")
                .setUserId(event.getUserId().toString())
                .setOnboardingRequestId(event.getOnboardingRequestId().toString())
                .setProviderTargetId(event.getProviderTargetId().toString())
                .setMembershipState(result.membershipState())
                .setSuccess(result.success())
                .setErrorCode(result.errorCode())
                .setErrorMessage(result.errorMessage())
                .build();
    }

    private String toOutboxPayload(SpecificRecord record) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Schema.Field field : record.getSchema().getFields()) {
            payload.put(field.name(), record.get(field.pos()));
        }
        return objectMapper.writeValueAsString(payload);
    }

    private ResultState toResultState(String membershipState) {
        return switch (membershipState) {
            case "ACTIVE" -> ResultState.SUCCESS;
            case "PENDING" -> ResultState.PENDING;
            default -> ResultState.FAILURE;
        };
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

    private String buildAtlassianResponsePayload(AtlassianGroupMembershipResult result) throws JsonProcessingException {
        return objectMapper.writeValueAsString(result);
    }

    private AtlassianProvisioningCompletedV1 buildAtlassianCompletedEvent(
            AtlassianProvisioningRequestedV1 event, AtlassianGroupMembershipResult result) {
        return AtlassianProvisioningCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType("AtlassianProvisioningCompletedV1")
                .setEventVersion(1)
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(event.getCorrelationId().toString())
                .setProducer("provisioning-service")
                .setUserId(event.getUserId().toString())
                .setOnboardingRequestId(event.getOnboardingRequestId().toString())
                .setProviderTargetId(event.getProviderTargetId().toString())
                .setSuccess(result.success())
                .setErrorCode(result.errorCode())
                .setErrorMessage(result.errorMessage())
                .build();
    }
}