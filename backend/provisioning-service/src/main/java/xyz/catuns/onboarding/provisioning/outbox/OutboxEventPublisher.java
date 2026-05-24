package xyz.catuns.onboarding.provisioning.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.catuns.onboarding.provisioning.domain.OutboxEvent;
import xyz.catuns.onboarding.provisioning.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxEventPublisher(OutboxEventRepository outboxRepo, KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void publishPendingEvents() {
        outboxRepo.findByPublishedFalseOrderByCreatedAtAsc().forEach(event -> {
            if (event.getTopic() == null) {
                log.warn("Skipping outbox event {} (type={}) — no topic set", event.getId(), event.getEventType());
                return;
            }
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    event.getTopic(),
                    event.getAggregateId().toString(),
                    event.getPayload()
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
}
