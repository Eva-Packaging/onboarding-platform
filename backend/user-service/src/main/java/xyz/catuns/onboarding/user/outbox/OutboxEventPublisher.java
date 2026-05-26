package xyz.catuns.onboarding.user.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.catuns.onboarding.common.events.UserRegisteredV1;
import xyz.catuns.onboarding.user.domain.OutboxEvent;
import xyz.catuns.onboarding.user.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository outboxRepo,
                                KafkaTemplate<String, SpecificRecord> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void publishPendingEvents() {
        outboxRepo.findByPublishedFalseOrderByCreatedAtAsc().forEach(event -> {
            if (event.getTopic() == null) {
                log.warn("Skipping outbox event {} (type={}) — no topic set", event.getId(), event.getEventType());
                return;
            }

            SpecificRecord avroRecord;
            try {
                avroRecord = toAvroRecord(event);
            } catch (Exception e) {
                log.error("Cannot convert outbox event {} (type={}) to Avro record: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                return;
            }

            ProducerRecord<String, SpecificRecord> record = new ProducerRecord<>(
                    event.getTopic(),
                    event.getAggregateId().toString(),
                    avroRecord
            );
            if (event.getCorrelationId() != null) {
                record.headers().add("X-Correlation-ID",
                        event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
            }
            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex == null) {
                    event.setPublished(true);
                    event.setPublishedAt(Instant.now());
                    outboxRepo.save(event);
                    log.debug("Published outbox event {} to {}", event.getId(), event.getTopic());
                } else {
                    log.error("Failed to publish outbox event {} to {}: {}",
                            event.getId(), event.getTopic(), ex.getMessage());
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    private SpecificRecord toAvroRecord(OutboxEvent event) throws Exception {
        Map<String, Object> data = objectMapper.readValue(event.getPayload(), Map.class);
        return switch (event.getEventType()) {
            case "UserRegisteredV1" -> UserRegisteredV1.newBuilder()
                    .setEventId((String) data.get("eventId"))
                    .setEventType((String) data.get("eventType"))
                    .setEventVersion(((Number) data.get("eventVersion")).intValue())
                    .setOccurredAt((String) data.get("occurredAt"))
                    .setCorrelationId((String) data.get("correlationId"))
                    .setProducer((String) data.get("producer"))
                    .setUserId((String) data.get("userId"))
                    .setOnboardingRequestId((String) data.get("onboardingRequestId"))
                    .setDisplayName((String) data.get("displayName"))
                    .setPrimaryEmail((String) data.get("primaryEmail"))
                    .setGithubUserId((String) data.get("githubUserId"))
                    .setGithubLogin((String) data.get("githubLogin"))
                    .setRoleKeys((List<String>) data.getOrDefault("roleKeys", List.of()))
                    .build();
            default -> throw new IllegalStateException("Unknown event type: " + event.getEventType());
        };
    }
}