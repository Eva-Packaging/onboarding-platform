package xyz.catuns.onboarding.service.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Getter
@Immutable
@Entity
@Table(name = "app_role")
public class AppRole {

    @Id
    private UUID id;

    @Column(name = "role_key", nullable = false, length = 100)
    private String roleKey;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
