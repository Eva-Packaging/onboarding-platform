package xyz.catuns.onboarding.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class UserRoleAssignmentId implements Serializable {

    @Column(name = "user_profile_id")
    private UUID userProfileId;

    @Column(name = "app_role_id")
    private UUID appRoleId;
}