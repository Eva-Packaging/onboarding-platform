package xyz.catuns.onboarding.user.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import xyz.catuns.onboarding.user.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.user.domain.OutboxEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTestContainersConfiguration.class)
class OutboxEventRepositoryTest {

    @Autowired
    OutboxEventRepository repository;

    @Test
    void save_publishedDefaultsToFalseWithNullPublishedAt() {
        OutboxEvent saved = repository.save(buildEvent("UserRegisteredV1"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isPublished()).isFalse();
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    void save_populatesCreatedAt() {
        OutboxEvent saved = repository.save(buildEvent("UserRegisteredV1"));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByPublishedFalse_excludesPublishedEvents() {
        OutboxEvent unpublished = repository.save(buildEvent("UserRegisteredV1"));

        OutboxEvent published = buildEvent("UserRegisteredV1");
        published.setPublished(true);
        published.setPublishedAt(Instant.now());
        repository.save(published);

        List<OutboxEvent> found = repository.findByPublishedFalseOrderByCreatedAtAsc();

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(unpublished.getId());
    }

    @Test
    void findByPublishedFalse_ordersOldestFirst() throws InterruptedException {
        OutboxEvent first = repository.save(buildEvent("EventA"));
        repository.flush();
        Thread.sleep(5);
        OutboxEvent second = repository.save(buildEvent("EventB"));

        List<OutboxEvent> found = repository.findByPublishedFalseOrderByCreatedAtAsc();

        assertThat(found).hasSizeGreaterThanOrEqualTo(2);
        assertThat(indexOfId(found, first.getId())).isLessThan(indexOfId(found, second.getId()));
    }

    @Test
    void findByPublishedFalse_returnsEmptyWhenAllPublished() {
        OutboxEvent event = buildEvent("SomeEvent");
        event.setPublished(true);
        event.setPublishedAt(Instant.now());
        repository.save(event);

        assertThat(repository.findByPublishedFalseOrderByCreatedAtAsc()).isEmpty();
    }

    private OutboxEvent buildEvent(String eventType) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("UserProfile");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setPayload("{\"userId\":\"" + UUID.randomUUID() + "\"}");
        return event;
    }

    private int indexOfId(List<OutboxEvent> events, UUID id) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getId().equals(id)) return i;
        }
        return -1;
    }
}