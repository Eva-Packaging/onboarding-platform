package xyz.catuns.onboarding.user.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import xyz.catuns.onboarding.user.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.domain.UserStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTestContainersConfiguration.class)
class UserProfileRepositoryTest {

    @Autowired
    UserProfileRepository repository;

    @Test
    void save_persistsAndAssignsGeneratedId() {
        UserProfile saved = repository.save(buildProfile("alice@example.com", "Alice"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_ONBOARDING);
    }

    @Test
    void save_populatesCreatedAtAndUpdatedAt() {
        UserProfile saved = repository.save(buildProfile("bob@example.com", "Bob"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void findById_returnsPersistedProfile() {
        UserProfile saved = repository.save(buildProfile("carol@example.com", "Carol"));

        Optional<UserProfile> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Carol");
        assertThat(found.get().getPrimaryEmail()).isEqualTo("carol@example.com");
    }

    @Test
    void findByPrimaryEmail_returnsMatchingProfile() {
        repository.save(buildProfile("dave@example.com", "Dave"));

        Optional<UserProfile> found = repository.findByPrimaryEmail("dave@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Dave");
    }

    @Test
    void findByPrimaryEmail_returnsEmptyForUnknownEmail() {
        Optional<UserProfile> found = repository.findByPrimaryEmail("nobody@example.com");

        assertThat(found).isEmpty();
    }

    private UserProfile buildProfile(String email, String name) {
        UserProfile profile = new UserProfile();
        profile.setDisplayName(name);
        profile.setPrimaryEmail(email);
        profile.setStatus(UserStatus.PENDING_ONBOARDING);
        return profile;
    }
}