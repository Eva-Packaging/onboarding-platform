package xyz.catuns.onboarding.service.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import xyz.catuns.onboarding.service.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.service.domain.OnboardingRequest;
import xyz.catuns.onboarding.service.domain.OnboardingRequestState;
import xyz.catuns.onboarding.service.domain.OnboardingSource;
import xyz.catuns.onboarding.service.domain.OnboardingStep;
import xyz.catuns.onboarding.service.domain.OnboardingStepState;
import xyz.catuns.onboarding.service.domain.OnboardingStepType;
import xyz.catuns.onboarding.service.domain.StepType;

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
@Sql(statements = {
    "insert into onboarding_step_type (id, step_key, description) values " +
    "(1,'IDENTITY_CORRELATION','Correlate identities'), " +
    "(2,'GITHUB_TEAM_PROVISIONING','GitHub team'), " +
    "(3,'JIRA_GROUP_PROVISIONING','Jira group')"
})
class OnboardingStepRepositoryTest {

    @Autowired
    OnboardingStepRepository stepRepository;

    @Autowired
    OnboardingRequestRepository requestRepository;

    @Autowired
    OnboardingStepTypeRepository stepTypeRepository;

    private OnboardingRequest request;
    private OnboardingStepType identityCorrelationType;

    @BeforeEach
    void setUp() {
        request = requestRepository.save(buildRequest());
        identityCorrelationType = stepTypeRepository.findByStepKey(StepType.IDENTITY_CORRELATION).orElseThrow();
    }

    @Test
    void save_persistsStepLinkedToRequestAndType() {
        OnboardingStep saved = stepRepository.save(buildStep(request, identityCorrelationType));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getState()).isEqualTo(OnboardingStepState.PENDING);
    }

    @Test
    void save_populatesTimestamps() {
        OnboardingStep saved = stepRepository.save(buildStep(request, identityCorrelationType));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_resolveStepTypeAndRequest() {
        OnboardingStep saved = stepRepository.save(buildStep(request, identityCorrelationType));
        stepRepository.flush();

        Optional<OnboardingStep> found = stepRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStepType().getStepKey()).isEqualTo(StepType.IDENTITY_CORRELATION);
        assertThat(found.get().getOnboardingRequest().getId()).isEqualTo(request.getId());
    }

    @Test
    void findByOnboardingRequest_Id_returnsAllStepsForRequest() {
        OnboardingStepType githubType = stepTypeRepository.findByStepKey(StepType.GITHUB_TEAM_PROVISIONING).orElseThrow();
        stepRepository.save(buildStep(request, identityCorrelationType));
        stepRepository.save(buildStep(request, githubType));

        List<OnboardingStep> found = stepRepository.findByOnboardingRequest_Id(request.getId());

        assertThat(found).hasSize(2);
    }

    @Test
    void findByOnboardingRequest_Id_returnsEmptyForUnknownRequest() {
        List<OnboardingStep> found = stepRepository.findByOnboardingRequest_Id(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void save_storesProviderTargetIdAsBareUuid() {
        UUID targetId = UUID.randomUUID();
        OnboardingStep step = buildStep(request, identityCorrelationType);
        step.setProviderTargetId(targetId);
        OnboardingStep saved = stepRepository.save(step);

        assertThat(saved.getProviderTargetId()).isEqualTo(targetId);
    }

    private OnboardingRequest buildRequest() {
        OnboardingRequest r = new OnboardingRequest();
        r.setUserProfileId(UUID.randomUUID());
        r.setState(OnboardingRequestState.REQUESTED);
        r.setSource(OnboardingSource.SELF_REGISTRATION);
        r.setCorrelationId(UUID.randomUUID());
        return r;
    }

    private OnboardingStep buildStep(OnboardingRequest request, OnboardingStepType stepType) {
        OnboardingStep step = new OnboardingStep();
        step.setOnboardingRequest(request);
        step.setStepType(stepType);
        step.setState(OnboardingStepState.PENDING);
        return step;
    }
}