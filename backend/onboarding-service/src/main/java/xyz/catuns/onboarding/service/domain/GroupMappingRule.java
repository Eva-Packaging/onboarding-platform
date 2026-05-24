package xyz.catuns.onboarding.service.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "group_mapping_rule")
public class GroupMappingRule {

    @Id
    private UUID id;

    @Column(name = "app_role_id", nullable = false)
    private UUID appRoleId;

    @Column(name = "provider_target_id", nullable = false)
    private UUID providerTargetId;

    @Column(name = "cohort_key", length = 100)
    private String cohortKey;

    @Column(name = "priority_order", nullable = false)
    private int priorityOrder;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
