package xyz.catuns.onboarding.provisioning.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningRequestedV1;
import xyz.catuns.onboarding.common.events.GithubProvisioningRequestedV1;
import xyz.catuns.onboarding.provisioning.TestcontainersConfiguration;
import xyz.catuns.onboarding.provisioning.domain.OutboxEvent;
import xyz.catuns.onboarding.provisioning.domain.ProvisioningAuditLog;
import xyz.catuns.onboarding.provisioning.domain.ResultState;
import xyz.catuns.onboarding.provisioning.outbox.OutboxEventPublisher;
import xyz.catuns.onboarding.provisioning.repository.OutboxEventRepository;
import xyz.catuns.onboarding.provisioning.repository.ProvisioningAuditLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProvisioningEventServiceTest {

    @Autowired private ProvisioningEventService eventService;
    @Autowired private ProvisioningAuditLogRepository auditLogRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;

    // Prevent the @Scheduled publisher from attempting KafkaAvroSerializer
    // (Schema Registry is not available in the test environment)
    @MockBean private OutboxEventPublisher outboxEventPublisher;

    // ── GithubProvisioningRequestedV1 ────────────────────────────────────────────

    @Test
    @Transactional
    void handleGithubProvisioningRequested_writesAuditLogRow() {
        GithubProvisioningRequestedV1 event = githubRequestedEvent();
        eventService.handleGithubProvisioningRequested(event);

        List<ProvisioningAuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);

        ProvisioningAuditLog log = logs.getFirst();
        assertThat(log.getActionName()).isEqualTo("GITHUB_PROVISION");
        assertThat(log.getResultState()).isEqualTo(ResultState.SUCCESS);
        assertThat(log.getProviderId()).isEqualTo(UUID.fromString(event.getProviderTargetId().toString()));
        assertThat(log.getCorrelationId()).isEqualTo(UUID.fromString(event.getCorrelationId().toString()));
    }

    @Test
    @Transactional
    void handleGithubProvisioningRequested_writesCompletionOutboxRow() {
        GithubProvisioningRequestedV1 event = githubRequestedEvent();
        eventService.handleGithubProvisioningRequested(event);

        List<OutboxEvent> rows = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        assertThat(rows).hasSize(1);

        OutboxEvent outbox = rows.getFirst();
        assertThat(outbox.getEventType()).isEqualTo("GithubProvisioningCompletedV1");
        assertThat(outbox.getTopic()).isEqualTo("edu.provisioning.github.v1");
        assertThat(outbox.getAggregateId()).isEqualTo(UUID.fromString(event.getUserId().toString()));
        assertThat(outbox.isPublished()).isFalse();
    }

    @Test
    @Transactional
    void handleGithubProvisioningRequested_outboxPayloadContainsSuccessTrue() {
        GithubProvisioningRequestedV1 event = githubRequestedEvent();
        eventService.handleGithubProvisioningRequested(event);

        OutboxEvent outbox = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc().getFirst();
        assertThat(outbox.getPayload())
                .contains("\"success\":true")
                .contains("\"membershipState\":\"ACTIVE\"")
                .contains("\"eventType\":\"GithubProvisioningCompletedV1\"");
    }

    @Test
    @Transactional
    void handleGithubProvisioningRequested_writesAuditLogAndOutboxInSameTransaction() {
        GithubProvisioningRequestedV1 event = githubRequestedEvent();
        eventService.handleGithubProvisioningRequested(event);

        assertThat(auditLogRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    // ── AtlassianProvisioningRequestedV1 ────────────────────────────────────────

    @Test
    @Transactional
    void handleAtlassianProvisioningRequested_writesAuditLogRow() {
        AtlassianProvisioningRequestedV1 event = atlassianRequestedEvent();
        eventService.handleAtlassianProvisioningRequested(event);

        List<ProvisioningAuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);

        ProvisioningAuditLog log = logs.getFirst();
        assertThat(log.getActionName()).isEqualTo("ATLASSIAN_PROVISION");
        assertThat(log.getResultState()).isEqualTo(ResultState.SUCCESS);
        assertThat(log.getProviderId()).isEqualTo(UUID.fromString(event.getProviderTargetId().toString()));
        assertThat(log.getCorrelationId()).isEqualTo(UUID.fromString(event.getCorrelationId().toString()));
    }

    @Test
    @Transactional
    void handleAtlassianProvisioningRequested_writesCompletionOutboxRow() {
        AtlassianProvisioningRequestedV1 event = atlassianRequestedEvent();
        eventService.handleAtlassianProvisioningRequested(event);

        List<OutboxEvent> rows = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        assertThat(rows).hasSize(1);

        OutboxEvent outbox = rows.getFirst();
        assertThat(outbox.getEventType()).isEqualTo("AtlassianProvisioningCompletedV1");
        assertThat(outbox.getTopic()).isEqualTo("edu.provisioning.atlassian.v1");
        assertThat(outbox.getAggregateId()).isEqualTo(UUID.fromString(event.getUserId().toString()));
        assertThat(outbox.isPublished()).isFalse();
    }

    @Test
    @Transactional
    void handleAtlassianProvisioningRequested_outboxPayloadContainsSuccessTrue() {
        AtlassianProvisioningRequestedV1 event = atlassianRequestedEvent();
        eventService.handleAtlassianProvisioningRequested(event);

        OutboxEvent outbox = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc().getFirst();
        assertThat(outbox.getPayload())
                .contains("\"success\":true")
                .contains("\"eventType\":\"AtlassianProvisioningCompletedV1\"");
    }

    // ── Outbox routing ───────────────────────────────────────────────────────────

    @Test
    @Transactional
    void outboxRows_routedToCorrectTopics() {
        eventService.handleGithubProvisioningRequested(githubRequestedEvent());
        eventService.handleAtlassianProvisioningRequested(atlassianRequestedEvent());

        List<OutboxEvent> rows = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(OutboxEvent::getTopic)
                .containsExactlyInAnyOrder("edu.provisioning.github.v1", "edu.provisioning.atlassian.v1");
    }

    // ── Avro event builders ───────────────────────────────────────────────────────

    private GithubProvisioningRequestedV1 githubRequestedEvent() {
        return GithubProvisioningRequestedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("onboarding-service")
                .setUserId(UUID.randomUUID().toString())
                .setOnboardingRequestId(UUID.randomUUID().toString())
                .setGithubLogin("testuser")
                .setGithubOrg("evaitcs-org")
                .setGithubTeamSlug("students")
                .setProviderTargetId(UUID.randomUUID().toString())
                .build();
    }

    private AtlassianProvisioningRequestedV1 atlassianRequestedEvent() {
        return AtlassianProvisioningRequestedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("onboarding-service")
                .setUserId(UUID.randomUUID().toString())
                .setOnboardingRequestId(UUID.randomUUID().toString())
                .setGroupName("jira-students")
                .setProviderTargetId(UUID.randomUUID().toString())
                .build();
    }
}