package xyz.catuns.onboarding.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.ProviderKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {
    Optional<ExternalIdentity> findByProvider_ProviderKeyAndExternalUserId(ProviderKey providerKey, String externalUserId);
    List<ExternalIdentity> findByUserProfile_Id(UUID userProfileId);
}