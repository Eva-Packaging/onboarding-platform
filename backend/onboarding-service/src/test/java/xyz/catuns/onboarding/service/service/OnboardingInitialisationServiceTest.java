package xyz.catuns.onboarding.service.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.service.TestcontainersConfiguration;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitRequest;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitResponse;
import xyz.catuns.onboarding.service.api.dto.StepSummaryDto;
import xyz.catuns.onboarding.service.domain.OnboardingRequestState;
import xyz.catuns.onboarding.service.domain.OnboardingStepState;
import xyz.catuns.onboarding.service.domain.StepType;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OnboardingInitialisationServiceTest {

    @Autowired
    OnboardingInitialisationService initialisationService;

    @Autowired
    OnboardingRequestRepository requestRepository;

    @Autowired
    OnboardingStepRepository stepRepository;

    @Test
    void initialise_createsOnboardingRequestInInProgressState() {
        OnboardingInitResponse response = initialisationService.initialise(buildRequest());

        assertThat(response.requestId()).isNotNull();
        assertThat(response.state()).isEqualTo(OnboardingRequestState.IN_PROGRESS.name());
        assertThat(requestRepository.findById(response.requestId()))
            .isPresent()
            .get()
            .extracting(r -> r.getState().name())
            .isEqualTo(OnboardingRequestState.IN_PROGRESS.name());
    }

    @Test
    void initialise_createsExactlyThreeStepRows() {
        OnboardingInitResponse response = initialisationService.initialise(buildRequest());

        assertThat(response.steps()).hasSize(3);
        assertThat(stepRepository.findByOnboardingRequest_Id(response.requestId())).hasSize(3);
    }

    @Test
    void initialise_stepsHaveCorrectTypesInOrder() {
        OnboardingInitResponse response = initialisationService.initialise(buildRequest());

        List<String> types = response.steps().stream().map(StepSummaryDto::type).toList();
        assertThat(types).containsExactly(
            StepType.IDENTITY_CORRELATION.name(),
            StepType.GITHUB_TEAM_PROVISIONING.name(),
            StepType.JIRA_GROUP_PROVISIONING.name()
        );
    }

    @Test
    void initialise_allStepsArePending() {
        OnboardingInitResponse response = initialisationService.initialise(buildRequest());

        assertThat(response.steps())
            .allMatch(s -> s.state().equals(OnboardingStepState.PENDING.name()));
    }

    @Test
    void initialise_correlationIdIsPreservedOnRequest() {
        UUID correlationId = UUID.randomUUID();
        OnboardingInitRequest request = new OnboardingInitRequest(UUID.randomUUID(), correlationId, List.of("STUDENT"));

        OnboardingInitResponse response = initialisationService.initialise(request);

        assertThat(requestRepository.findById(response.requestId()))
            .isPresent()
            .get()
            .extracting(r -> r.getCorrelationId())
            .isEqualTo(correlationId);
    }

    private OnboardingInitRequest buildRequest() {
        return new OnboardingInitRequest(UUID.randomUUID(), UUID.randomUUID(), List.of("STUDENT"));
    }
}