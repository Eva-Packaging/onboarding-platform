package xyz.catuns.onboarding.user.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import xyz.catuns.onboarding.common.events.UserRegisteredV1;
import xyz.catuns.onboarding.user.domain.OutboxEvent;
import xyz.catuns.onboarding.user.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepo;
    @Mock
    private KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(outboxRepo, kafkaTemplate, new ObjectMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPendingEvents_sendsAvroRecordWithCorrelationIdHeader() {
        OutboxEvent event = unpublishedEvent("edu.user.registered.v1", "cid-abc-123");
        when(outboxRepo.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, SpecificRecord>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, SpecificRecord> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("edu.user.registered.v1");
        assertThat(record.value()).isInstanceOf(UserRegisteredV1.class);

        Header correlationHeader = record.headers().lastHeader("X-Correlation-ID");
        assertThat(correlationHeader).isNotNull();
        assertThat(new String(correlationHeader.value(), StandardCharsets.UTF_8)).isEqualTo("cid-abc-123");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPendingEvents_nullCorrelationId_sendsWithoutHeader() {
        OutboxEvent event = unpublishedEvent("edu.user.registered.v1", null);
        when(outboxRepo.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, SpecificRecord>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().headers().lastHeader("X-Correlation-ID")).isNull();
    }

    @Test
    void publishPendingEvents_nullTopic_skipsEvent() {
        OutboxEvent event = unpublishedEvent(null, "cid-abc-123");
        when(outboxRepo.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPendingEvents_onSuccess_marksEventPublished() {
        OutboxEvent event = unpublishedEvent("edu.user.registered.v1", "cid-abc-123");
        when(outboxRepo.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPendingEvents();

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxRepo).save(event);
    }

    @Test
    void publishPendingEvents_onKafkaFailure_doesNotMarkPublished() {
        OutboxEvent event = unpublishedEvent("edu.user.registered.v1", "cid-abc-123");
        when(outboxRepo.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, SpecificRecord>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        publisher.publishPendingEvents();

        assertThat(event.isPublished()).isFalse();
        verify(outboxRepo, never()).save(event);
    }

    @Test
    void publishPendingEvents_unknownEventType_skipsEvent() {
        OutboxEvent event = unpublishedEvent("edu.user.registered.v1", "cid-abc-123");
        event.setEventType("UnknownEventV1");
        when(outboxRepo.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verifyNoInteractions(kafkaTemplate);
    }

    private OutboxEvent unpublishedEvent(String topic, String correlationId) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("UserProfile");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("UserRegisteredV1");
        event.setTopic(topic);
        event.setCorrelationId(correlationId);
        event.setPayload("""
                {
                  "eventId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "eventType": "UserRegisteredV1",
                  "eventVersion": 1,
                  "occurredAt": "2026-05-25T00:00:00Z",
                  "correlationId": "cid-abc-123",
                  "producer": "user-service",
                  "userId": "abc",
                  "onboardingRequestId": "req-001",
                  "displayName": "Test User",
                  "primaryEmail": "test@example.com",
                  "githubUserId": "gh-001",
                  "githubLogin": "testuser",
                  "roleKeys": []
                }""");
        return event;
    }
}