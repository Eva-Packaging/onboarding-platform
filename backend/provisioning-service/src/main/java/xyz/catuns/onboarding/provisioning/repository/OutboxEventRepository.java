package xyz.catuns.onboarding.provisioning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.provisioning.domain.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();
}