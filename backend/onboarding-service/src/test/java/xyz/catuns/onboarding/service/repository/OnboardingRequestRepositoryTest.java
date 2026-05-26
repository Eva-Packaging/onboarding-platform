package xyz.catuns.onboarding.service.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import xyz.catuns.onboarding.service.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.service.domain.OnboardingRequest;
import xyz.catuns.onboarding.service.domain.OnboardingRequestState;
import xyz.catuns.onboarding.service.domain.OnboardingSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTestContainersConfiguration.class)
class OnboardingRequestRepositoryTest {

    @Autowired
    OnboardingRequestRepository repository;

    @Test
    void save_persistsAndAssignsGeneratedId() {
        OnboardingRequest saved = repository.save(buildRequest(UUID.randomUUID().toString()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getState()).isEqualTo(OnboardingRequestState.REQUESTED);
    }

    @Test
    void save_populatesCreatedAtAndUpdatedAt() {
        OnboardingRequest saved = repository.save(buildRequest(UUID.randomUUID().toString()));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void save_populatesStartedAtFromPrePersist() {
        OnboardingRequest saved = repository.save(buildRequest(UUID.randomUUID().toString()));

        assertThat(saved.getStartedAt()).isNotNull();
    }

    @Test
    void findById_returnsPersistedRequest() {
        OnboardingRequest saved = repository.save(buildRequest(UUID.randomUUID().toString()));

        Optional<OnboardingRequest> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getSource()).isEqualTo(OnboardingSource.SELF_REGISTRATION);
        assertThat(found.get().getCorrelationId()).isEqualTo(saved.getCorrelationId());
    }

    @Test
    void findByUserProfileId_returnsRequestsForUser() {
        String userProfileId = UUID.randomUUID().toString();
        repository.save(buildRequest(userProfileId));
        repository.save(buildRequest(userProfileId));
        repository.save(buildRequest(UUID.randomUUID().toString()));

        List<OnboardingRequest> found = repository.findByUserProfileId(userProfileId);

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(r -> r.getUserProfileId().equals(userProfileId));
    }

    @Test
    void findByUserProfileId_returnsEmptyForUnknownUser() {
        List<OnboardingRequest> found = repository.findByUserProfileId(UUID.randomUUID().toString());

        assertThat(found).isEmpty();
    }

    private OnboardingRequest buildRequest(String userProfileId) {
        OnboardingRequest request = new OnboardingRequest();
        request.setUserProfileId(userProfileId);
        request.setState(OnboardingRequestState.REQUESTED);
        request.setSource(OnboardingSource.SELF_REGISTRATION);
        request.setCorrelationId(UUID.randomUUID());
        return request;
    }
}