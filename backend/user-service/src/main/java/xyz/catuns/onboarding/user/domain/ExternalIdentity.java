package xyz.catuns.onboarding.user.domain;

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
@Table(
    name = "external_identity",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_external_identity_provider_user",
        columnNames = {"provider_id", "external_user_id"}
    )
)
public class ExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ExternalProvider provider;

    @Column(name = "external_user_id", nullable = false, length = 255)
    private String externalUserId;

    @Column(length = 255)
    private String username;

    @Column(length = 320)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_json", columnDefinition = "jsonb")
    private String profileJson;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

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