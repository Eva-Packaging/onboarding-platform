package xyz.catuns.onboarding.service.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "onboarding_request")
public class OnboardingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_profile_id", nullable = false)
    private String userProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OnboardingRequestState state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OnboardingSource source;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "onboardingRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OnboardingStep> steps = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (startedAt == null) {
            startedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}