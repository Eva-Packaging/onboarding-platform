package xyz.catuns.onboarding.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.user.domain.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();
}