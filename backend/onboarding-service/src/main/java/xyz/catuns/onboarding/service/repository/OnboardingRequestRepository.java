package xyz.catuns.onboarding.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.service.domain.OnboardingRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, UUID> {
    List<OnboardingRequest> findByUserProfileId(String userProfileId);

    Optional<OnboardingRequest> findTopByUserProfileIdOrderByCreatedAtDesc(String userProfileId);
}
