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

    public String build(UUID userId, UUID correlationId, String githubUserId, String githubLogin,
                        String primaryEmail, List<String> roleKeys, Instant registeredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId.toString());
        payload.put("githubUserId", githubUserId);
        payload.put("githubLogin", githubLogin);
        payload.put("primaryEmail", primaryEmail);
        payload.put("correlationId", correlationId.toString());
        payload.put("roleKeys", roleKeys);
        payload.put("registeredAt", registeredAt.toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize UserRegisteredV1 payload", e);
        }
    }
}