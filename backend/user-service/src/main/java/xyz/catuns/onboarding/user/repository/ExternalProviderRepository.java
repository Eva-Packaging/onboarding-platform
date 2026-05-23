package xyz.catuns.onboarding.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.user.domain.ExternalProvider;
import xyz.catuns.onboarding.user.domain.ProviderKey;

import java.util.Optional;
import java.util.UUID;

public interface ExternalProviderRepository extends JpaRepository<ExternalProvider, UUID> {
    Optional<ExternalProvider> findByProviderKey(ProviderKey providerKey);
}