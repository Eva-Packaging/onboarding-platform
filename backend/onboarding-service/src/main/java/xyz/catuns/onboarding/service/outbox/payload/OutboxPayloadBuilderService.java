package xyz.catuns.onboarding.service.outbox.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxPayloadBuilderService {

    private final ObjectMapper objectMapper;

    public OutboxPayloadBuilderService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildIdentityCorrelationRequested(String userId, String onboardingRequestId,
            String correlationId, String githubIdentityId, String githubLogin, String primaryEmail) {
        Map<String, Object> p = base("IdentityCorrelationRequestedV1", userId, onboardingRequestId, correlationId);
        p.put("githubIdentityId", githubIdentityId);
        p.put("githubLogin", githubLogin);
        p.put("primaryEmail", primaryEmail);
        return serialize(p);
    }

    public String buildGithubProvisioningRequested(String userId, String onboardingRequestId,
            String correlationId, String githubLogin, String githubOrg, String githubTeamSlug,
            String providerTargetId) {
        Map<String, Object> p = base("GithubProvisioningRequestedV1", userId, onboardingRequestId, correlationId);
        p.put("githubLogin", githubLogin);
        p.put("githubOrg", githubOrg);
        p.put("githubTeamSlug", githubTeamSlug);
        p.put("providerTargetId", providerTargetId);
        return serialize(p);
    }

    public String buildAtlassianProvisioningRequested(String userId, String onboardingRequestId,
            String correlationId, String atlassianIdentityId, String atlassianEmail,
            String groupName, String providerTargetId) {
        Map<String, Object> p = base("AtlassianProvisioningRequestedV1", userId, onboardingRequestId, correlationId);
        p.put("atlassianIdentityId", atlassianIdentityId);
        p.put("atlassianEmail", atlassianEmail);
        p.put("groupName", groupName);
        p.put("providerTargetId", providerTargetId);
        return serialize(p);
    }

    public String buildOnboardingCompleted(String userId, String onboardingRequestId,
            String correlationId, String finalState) {
        Map<String, Object> p = base("OnboardingCompletedV1", userId, onboardingRequestId, correlationId);
        p.put("finalState", finalState);
        return serialize(p);
    }

    public String buildOnboardingFailed(String userId, String onboardingRequestId,
            String correlationId, String failureStep, String failureCode, String failureMessage) {
        Map<String, Object> p = base("OnboardingFailedV1", userId, onboardingRequestId, correlationId);
        p.put("failureStep", failureStep);
        p.put("failureCode", failureCode);
        p.put("failureMessage", failureMessage);
        return serialize(p);
    }

    private Map<String, Object> base(String eventType, String userId, String onboardingRequestId,
            String correlationId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventId", UUID.randomUUID().toString());
        p.put("eventType", eventType);
        p.put("eventVersion", 1);
        p.put("occurredAt", Instant.now().toString());
        p.put("correlationId", correlationId != null ? correlationId : "");
        p.put("producer", "onboarding-service");
        p.put("userId", userId);
        p.put("onboardingRequestId", onboardingRequestId);
        return p;
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}