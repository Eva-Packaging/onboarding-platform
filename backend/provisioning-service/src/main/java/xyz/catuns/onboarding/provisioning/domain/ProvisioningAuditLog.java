package xyz.catuns.onboarding.provisioning.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "provisioning_audit_log")
public class ProvisioningAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "onboarding_step_id", nullable = false)
    private UUID onboardingStepId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "action_name", nullable = false, length = 100)
    private String actionName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_state", nullable = false, length = 50)
    private ResultState resultState;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}