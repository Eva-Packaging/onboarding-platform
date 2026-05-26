package xyz.catuns.onboarding.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UserRegisteredV1PayloadBuilder {

    private final ObjectMapper objectMapper;

    public UserRegisteredV1PayloadBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(UUID userId, String onboardingRequestId, String correlationId,
                        String displayName, String githubUserId, String githubLogin,
                        String primaryEmail, List<String> roleKeys, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "UserRegisteredV1");
        payload.put("eventVersion", 1);
        payload.put("occurredAt", occurredAt.toString());
        payload.put("correlationId", correlationId != null ? correlationId : "");
        payload.put("producer", "user-service");
        payload.put("userId", userId.toString());
        payload.put("onboardingRequestId", onboardingRequestId);
        payload.put("displayName", displayName != null ? displayName : "");
        payload.put("primaryEmail", primaryEmail != null ? primaryEmail : "");
        payload.put("githubUserId", githubUserId);
        payload.put("githubLogin", githubLogin);
        payload.put("roleKeys", roleKeys != null ? roleKeys : List.of());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize UserRegisteredV1 payload", e);
        }
    }
}