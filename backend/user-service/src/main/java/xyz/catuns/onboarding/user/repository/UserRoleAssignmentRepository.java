package xyz.catuns.onboarding.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.user.domain.UserRoleAssignment;
import xyz.catuns.onboarding.user.domain.UserRoleAssignmentId;

import java.util.List;
import java.util.UUID;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleAssignmentId> {
    List<UserRoleAssignment> findByUserProfile_Id(UUID userProfileId);
}