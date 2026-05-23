package xyz.catuns.onboarding.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.user.domain.IdentityLink;

import java.util.List;
import java.util.UUID;

public interface IdentityLinkRepository extends JpaRepository<IdentityLink, UUID> {
    List<IdentityLink> findByUserProfile_Id(UUID userProfileId);
}