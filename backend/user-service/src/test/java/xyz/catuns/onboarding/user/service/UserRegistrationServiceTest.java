package xyz.catuns.onboarding.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import xyz.catuns.onboarding.user.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.user.exception.DuplicateRegistrationException;
import xyz.catuns.onboarding.user.api.dto.RegistrationRequest;
import xyz.catuns.onboarding.user.domain.AppRole;
import xyz.catuns.onboarding.user.domain.AppRoleKey;
import xyz.catuns.onboarding.user.domain.ExternalProvider;
import xyz.catuns.onboarding.user.domain.OutboxEvent;
import xyz.catuns.onboarding.user.domain.ProviderKey;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.domain.UserStatus;
import xyz.catuns.onboarding.user.repository.AppRoleRepository;
import xyz.catuns.onboarding.user.repository.ExternalIdentityRepository;
import xyz.catuns.onboarding.user.repository.ExternalProviderRepository;
import xyz.catuns.onboarding.user.repository.OutboxEventRepository;
import xyz.catuns.onboarding.user.repository.UserProfileRepository;
import xyz.catuns.onboarding.user.repository.UserRoleAssignmentRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JpaTestContainersConfiguration.class,
    UserRegistrationService.class,
    UserRegisteredV1PayloadBuilder.class
})
class UserRegistrationServiceTest {

    @TestConfiguration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    UserRegistrationService service;

    @Autowired
    UserProfileRepository profileRepo;

    @Autowired
    ExternalIdentityRepository identityRepo;

    @Autowired
    ExternalProviderRepository providerRepo;

    @Autowired
    AppRoleRepository roleRepo;

    @Autowired
    OutboxEventRepository outboxRepo;

    @Autowired
    UserRoleAssignmentRepository roleAssignmentRepo;

    @BeforeEach
    void setUp() {
        ExternalProvider github = new ExternalProvider();
        github.setProviderKey(ProviderKey.GITHUB);
        github.setDisplayName("GitHub");
        providerRepo.save(github);

        AppRole student = new AppRole();
        student.setRoleKey(AppRoleKey.STUDENT);
        student.setDisplayName("Student");
        roleRepo.save(student);
    }

    @Test
    void register_newUser_createsProfileIdentityAndOutboxRow() {
        RegistrationResult result = service.register(buildRequest("gh-001", "newuser"));

        assertThat(result.userId()).isNotNull();
        assertThat(result.correlationId()).isNotNull();

        assertThat(profileRepo.findById(result.userId())).isPresent();
        assertThat(identityRepo.findByProvider_ProviderKeyAndExternalUserId(ProviderKey.GITHUB, "gh-001")).isPresent();

        List<OutboxEvent> events = outboxRepo.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("UserRegisteredV1");
        assertThat(events.get(0).isPublished()).isFalse();
        assertThat(events.get(0).getAggregateId()).isEqualTo(result.userId());
    }

    @Test
    void register_duplicateActiveUser_throwsDuplicateRegistrationException() {
        RegistrationResult first = service.register(buildRequest("gh-002", "dupeuser"));

        UserProfile profile = profileRepo.findById(first.userId()).orElseThrow();
        profile.setStatus(UserStatus.ACTIVE);
        profileRepo.saveAndFlush(profile);

        assertThatThrownBy(() -> service.register(buildRequest("gh-002", "dupeuser")))
                .isInstanceOf(DuplicateRegistrationException.class)
                .hasMessageContaining("gh-002");
    }

    @Test
    void register_withRoleKeys_assignsRoles() {
        RegistrationRequest req = buildRequest("gh-003", "student");
        req.setRoleKeys(List.of("STUDENT"));

        RegistrationResult result = service.register(req);

        var assignments = roleAssignmentRepo.findByUserProfile_Id(result.userId());
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getAppRole().getRoleKey()).isEqualTo(AppRoleKey.STUDENT);
    }

    @Test
    void register_correlationIdIsNonNullOnEveryRegistration() {
        RegistrationResult first = service.register(buildRequest("gh-004", "user4"));
        RegistrationResult second = service.register(buildRequest("gh-005", "user5"));

        assertThat(first.correlationId()).isNotNull();
        assertThat(second.correlationId()).isNotNull();
        assertThat(first.correlationId()).isNotEqualTo(second.correlationId());
    }

    private RegistrationRequest buildRequest(String githubUserId, String login) {
        RegistrationRequest req = new RegistrationRequest();
        req.setGithubUserId(githubUserId);
        req.setGithubLogin(login);
        req.setDisplayName("Test User");
        req.setPrimaryEmail("test@example.com");
        return req;
    }
}