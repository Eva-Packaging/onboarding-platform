package xyz.catuns.onboarding.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "user_role_assignment")
public class UserRoleAssignment {

    @EmbeddedId
    private UserRoleAssignmentId id = new UserRoleAssignmentId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userProfileId")
    @JoinColumn(name = "user_profile_id")
    private UserProfile userProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("appRoleId")
    @JoinColumn(name = "app_role_id")
    private AppRole appRole;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    void onCreate() {
        assignedAt = Instant.now();
    }
}