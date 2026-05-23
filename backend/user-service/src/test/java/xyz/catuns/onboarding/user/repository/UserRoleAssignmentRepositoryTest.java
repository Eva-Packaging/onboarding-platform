package xyz.catuns.onboarding.user.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import xyz.catuns.onboarding.user.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.user.domain.AppRole;
import xyz.catuns.onboarding.user.domain.AppRoleKey;
import xyz.catuns.onboarding.user.domain.UserProfile;
import xyz.catuns.onboarding.user.domain.UserRoleAssignment;
import xyz.catuns.onboarding.user.domain.UserRoleAssignmentId;
import xyz.catuns.onboarding.user.domain.UserStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTestContainersConfiguration.class)
class UserRoleAssignmentRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    AppRoleRepository appRoleRepository;

    @Autowired
    UserProfileRepository profileRepository;

    @Test
    void save_persistsAssignmentWithCompositeKey() {
        UserProfile profile = profileRepository.save(buildProfile());
        AppRole role = appRoleRepository.save(buildRole(AppRoleKey.STUDENT));

        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUserProfile(profile);
        assignment.setAppRole(role);
        em.persist(assignment);
        em.flush();

        UserRoleAssignmentId key = new UserRoleAssignmentId();
        key.setUserProfileId(profile.getId());
        key.setAppRoleId(role.getId());

        UserRoleAssignment found = em.find(UserRoleAssignment.class, key);

        assertThat(found).isNotNull();
        assertThat(found.getAssignedAt()).isNotNull();
    }

    @Test
    void compositeKey_equalityBasedOnBothIds() {
        UUID profileId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        UserRoleAssignmentId id1 = new UserRoleAssignmentId();
        id1.setUserProfileId(profileId);
        id1.setAppRoleId(roleId);

        UserRoleAssignmentId id2 = new UserRoleAssignmentId();
        id2.setUserProfileId(profileId);
        id2.setAppRoleId(roleId);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void findByRoleKey_resolvesPersistedRole() {
        appRoleRepository.save(buildRole(AppRoleKey.INSTRUCTOR));

        assertThat(appRoleRepository.findByRoleKey(AppRoleKey.INSTRUCTOR)).isPresent();
    }

    private UserProfile buildProfile() {
        UserProfile p = new UserProfile();
        p.setDisplayName("Role Test User");
        p.setPrimaryEmail("roletest@example.com");
        p.setStatus(UserStatus.PENDING_ONBOARDING);
        return p;
    }

    private AppRole buildRole(AppRoleKey key) {
        AppRole role = new AppRole();
        role.setRoleKey(key);
        role.setDisplayName(key.name());
        return role;
    }
}