package xyz.catuns.onboarding.service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.catuns.onboarding.common.events.IdentityCorrelationRequestedV1;
import xyz.catuns.onboarding.service.client.ExternalIdentityLookupResponse;
import xyz.catuns.onboarding.service.client.IdentityLinkCreateRequest;
import xyz.catuns.onboarding.service.client.MatchStrategy;
import xyz.catuns.onboarding.service.client.UserServiceFeignClient;
import xyz.catuns.onboarding.service.domain.OutboxEvent;
import xyz.catuns.onboarding.service.outbox.payload.OutboxPayloadBuilderService;
import xyz.catuns.onboarding.service.repository.OutboxEventRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityCorrelationServiceTest {

    private static final String TOPIC = "edu.identity.correlation.v1";

    private UserServiceFeignClient userServiceFeignClient;
    private OutboxEventRepository outboxRepo;
    private IdentityCorrelationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userServiceFeignClient = Mockito.mock(UserServiceFeignClient.class);
        outboxRepo = Mockito.mock(OutboxEventRepository.class);
        service = new IdentityCorrelationService(userServiceFeignClient, outboxRepo,
                new OutboxPayloadBuilderService(objectMapper));
        ReflectionTestUtils.setField(service, "idCorrelationTopicName", TOPIC);
    }

    @Test
    void handleIdentityCorrelationRequested_matchFound_createsIdentityLinkAndWritesCompletedOutbox() throws Exception {
        String userId = UUID.randomUUID().toString();
        String onboardingRequestId = UUID.randomUUID().toString();
        UUID githubIdentityUuid = UUID.randomUUID();
        UUID atlassianIdentityUuid = UUID.randomUUID();

        when(userServiceFeignClient.getAtlassianIdentityByEmail("student@example.com"))
                .thenReturn(new ExternalIdentityLookupResponse(atlassianIdentityUuid, "atl-acc-456", "student@example.com", "MATCHED"));
        when(userServiceFeignClient.getGithubIdentityByUserProfileId(UUID.fromString(userId)))
                .thenReturn(new ExternalIdentityLookupResponse(githubIdentityUuid, "gh-123", "student@example.com", "MATCHED"));

        service.handleIdentityCorrelationRequested(
                requestedEvent(userId, onboardingRequestId, "gh-123", "student@example.com"));

        ArgumentCaptor<IdentityLinkCreateRequest> linkCaptor = ArgumentCaptor.forClass(IdentityLinkCreateRequest.class);
        verify(userServiceFeignClient).createIdentityLink(linkCaptor.capture());
        IdentityLinkCreateRequest link = linkCaptor.getValue();
        assertThat(link.userProfileId()).isEqualTo(UUID.fromString(userId));
        assertThat(link.githubIdentityId()).isEqualTo(githubIdentityUuid);
        assertThat(link.atlassianIdentityId()).isEqualTo(atlassianIdentityUuid);
        assertThat(link.matchStrategy()).isEqualTo(MatchStrategy.EMAIL_EXACT);
        assertThat(link.confidenceScore()).isEqualByComparingTo(new BigDecimal("1.00"));

        OutboxEvent outboxEvent = captureSavedOutbox();
        assertThat(outboxEvent.getEventType()).isEqualTo("IdentityCorrelationCompletedV1");
        assertThat(outboxEvent.getTopic()).isEqualTo(TOPIC);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(UUID.fromString(onboardingRequestId));

        JsonNode payload = objectMapper.readTree(outboxEvent.getPayload());
        assertThat(payload.get("matched").asBoolean()).isTrue();
        assertThat(payload.get("matchStrategy").asText()).isEqualTo("EMAIL_EXACT");
        assertThat(payload.get("atlassianIdentityId").asText()).isEqualTo("atl-acc-456");
        assertThat(payload.get("githubIdentityId").asText()).isEqualTo("gh-123");
        assertThat(payload.get("confidenceScore").asDouble()).isEqualTo(1.0);
    }

    @Test
    void handleIdentityCorrelationRequested_noMatchingAtlassianIdentity_writesFailedOutboxWithoutCreatingIdentityLink() throws Exception {
        String userId = UUID.randomUUID().toString();
        String onboardingRequestId = UUID.randomUUID().toString();

        when(userServiceFeignClient.getAtlassianIdentityByEmail("nomatch@example.com")).thenThrow(notFound());

        service.handleIdentityCorrelationRequested(
                requestedEvent(userId, onboardingRequestId, "gh-123", "nomatch@example.com"));

        verify(userServiceFeignClient, never()).createIdentityLink(any());

        OutboxEvent outboxEvent = captureSavedOutbox();
        assertThat(outboxEvent.getEventType()).isEqualTo("IdentityCorrelationFailedV1");
        assertThat(outboxEvent.getTopic()).isEqualTo(TOPIC);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(UUID.fromString(onboardingRequestId));

        JsonNode payload = objectMapper.readTree(outboxEvent.getPayload());
        assertThat(payload.get("reasonCode").asText()).isEqualTo("NO_MATCHING_ATLASSIAN_IDENTITY");
    }

    @Test
    void handleIdentityCorrelationRequested_noPrimaryEmail_writesFailedOutboxWithoutCallingFeignClient() throws Exception {
        String userId = UUID.randomUUID().toString();
        String onboardingRequestId = UUID.randomUUID().toString();

        service.handleIdentityCorrelationRequested(
                requestedEvent(userId, onboardingRequestId, "gh-123", null));

        verifyNoInteractions(userServiceFeignClient);

        OutboxEvent outboxEvent = captureSavedOutbox();
        assertThat(outboxEvent.getEventType()).isEqualTo("IdentityCorrelationFailedV1");

        JsonNode payload = objectMapper.readTree(outboxEvent.getPayload());
        assertThat(payload.get("reasonCode").asText()).isEqualTo("NO_MATCHING_ATLASSIAN_IDENTITY");
    }

    private OutboxEvent captureSavedOutbox() {
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(outboxCaptor.capture());
        return outboxCaptor.getValue();
    }

    private FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/internal/external-identities",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("Not Found", request, null, Map.of());
    }

    private IdentityCorrelationRequestedV1 requestedEvent(String userId, String onboardingRequestId,
            String githubIdentityId, String primaryEmail) {
        return IdentityCorrelationRequestedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("onboarding-service")
                .setUserId(userId)
                .setOnboardingRequestId(onboardingRequestId)
                .setGithubIdentityId(githubIdentityId)
                .setGithubLogin("testuser")
                .setPrimaryEmail(primaryEmail)
                .build();
    }
}