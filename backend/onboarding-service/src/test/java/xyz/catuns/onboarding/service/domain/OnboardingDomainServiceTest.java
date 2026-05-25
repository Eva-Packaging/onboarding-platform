package xyz.catuns.onboarding.service.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.service.TestcontainersConfiguration;
import xyz.catuns.onboarding.service.exception.IllegalStateTransitionException;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepTypeRepository;
import xyz.catuns.onboarding.service.service.OnboardingDomainService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OnboardingDomainServiceTest {

    @Autowired
    OnboardingDomainService domainService;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    OnboardingRequestRepository requestRepository;

    @Autowired
    OnboardingStepRepository stepRepository;

    @Autowired
    OnboardingStepTypeRepository stepTypeRepository;

    private OnboardingRequest request;
    private OnboardingStepType identityType;
    private OnboardingStepType githubType;

    @BeforeEach
    void setUp() {
        request = requestRepository.save(buildRequest());
        identityType = stepTypeRepository.findByStepKey(StepType.IDENTITY_CORRELATION).orElseThrow();
        githubType   = stepTypeRepository.findByStepKey(StepType.GITHUB_TEAM_PROVISIONING).orElseThrow();
    }

    @Test
    void startRequest_transitionsRequestedToInProgress() {
        OnboardingRequest started = domainService.startRequest(request);

        assertThat(started.getState()).isEqualTo(OnboardingRequestState.IN_PROGRESS);
        assertThat(requestRepository.findById(request.getId()))
            .isPresent()
            .get()
            .extracting(OnboardingRequest::getState)
            .isEqualTo(OnboardingRequestState.IN_PROGRESS);
    }

    @Test
    void startRequest_throwsWhenAlreadyInProgress() {
        domainService.startRequest(request);

        assertThatThrownBy(() -> domainService.startRequest(request))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void transitionStep_pendingToProcessing_incrementsAttemptCount() {
        domainService.startRequest(request);
        OnboardingStep step = stepRepository.save(buildStep(identityType));

        OnboardingStep result = domainService.transitionStep(step, OnboardingStepState.PROCESSING);

        assertThat(result.getState()).isEqualTo(OnboardingStepState.PROCESSING);
        assertThat(result.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void transitionStep_processingToSucceeded_singleStep_resolvesRequestToCompleted() {
        domainService.startRequest(request);
        OnboardingStep step = stepRepository.save(buildStep(identityType));

        domainService.transitionStep(step, OnboardingStepState.PROCESSING);
        domainService.transitionStep(step, OnboardingStepState.SUCCEEDED);

        OnboardingRequest reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(OnboardingRequestState.COMPLETED);
    }

    @Test
    void transitionStep_partialFailure_resolvesRequestToPartialSuccess() {
        domainService.startRequest(request);
        OnboardingStep step1 = stepRepository.save(buildStep(identityType));
        OnboardingStep step2 = stepRepository.save(buildStep(githubType));

        domainService.transitionStep(step1, OnboardingStepState.PROCESSING);
        domainService.transitionStep(step1, OnboardingStepState.SUCCEEDED);

        domainService.transitionStep(step2, OnboardingStepState.PROCESSING);
        domainService.transitionStep(step2, OnboardingStepState.FAILED);

        OnboardingRequest reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(OnboardingRequestState.PARTIAL_SUCCESS);
    }

    @Test
    void transitionStep_allFailed_resolvesRequestToFailed() {
        domainService.startRequest(request);
        OnboardingStep step = stepRepository.save(buildStep(identityType));

        domainService.transitionStep(step, OnboardingStepState.PROCESSING);
        domainService.transitionStep(step, OnboardingStepState.FAILED);

        OnboardingRequest reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(OnboardingRequestState.FAILED);
    }

    @Test
    void transitionStep_retry_incrementsAttemptCountAgain() {
        domainService.startRequest(request);
        OnboardingStep step = stepRepository.save(buildStep(identityType));

        domainService.transitionStep(step, OnboardingStepState.PROCESSING);
        domainService.transitionStep(step, OnboardingStepState.FAILED);
        OnboardingStep retried = domainService.transitionStep(step, OnboardingStepState.PROCESSING);

        assertThat(retried.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void transitionStep_invalidTransition_throwsException() {
        domainService.startRequest(request);
        OnboardingStep step = stepRepository.save(buildStep(identityType));

        domainService.transitionStep(step, OnboardingStepState.PROCESSING);
        domainService.transitionStep(step, OnboardingStepState.SUCCEEDED);

        assertThatThrownBy(() -> domainService.transitionStep(step, OnboardingStepState.PROCESSING))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void transitionStep_toFailed_incrementsStepFailureCounter() {
        domainService.startRequest(request);
        OnboardingStep step = stepRepository.save(buildStep(identityType));
        domainService.transitionStep(step, OnboardingStepState.PROCESSING);

        double before = meterRegistry.counter("onboarding.step.failures",
            "step_type", StepType.IDENTITY_CORRELATION.name()).count();

        domainService.transitionStep(step, OnboardingStepState.FAILED);

        assertThat(meterRegistry.counter("onboarding.step.failures",
            "step_type", StepType.IDENTITY_CORRELATION.name()).count())
            .isEqualTo(before + 1);
    }

    @Test
    void transitionStep_toSucceeded_doesNotIncrementFailureCounter() {
        domainService.startRequest(request);
        OnboardingStep step = stepRepository.save(buildStep(identityType));
        domainService.transitionStep(step, OnboardingStepState.PROCESSING);

        double before = meterRegistry.counter("onboarding.step.failures",
            "step_type", StepType.IDENTITY_CORRELATION.name()).count();

        domainService.transitionStep(step, OnboardingStepState.SUCCEEDED);

        assertThat(meterRegistry.counter("onboarding.step.failures",
            "step_type", StepType.IDENTITY_CORRELATION.name()).count())
            .isEqualTo(before);
    }

    private OnboardingRequest buildRequest() {
        OnboardingRequest r = new OnboardingRequest();
        r.setUserProfileId(UUID.randomUUID().toString());
        r.setState(OnboardingRequestState.REQUESTED);
        r.setSource(OnboardingSource.SELF_REGISTRATION);
        r.setCorrelationId(UUID.randomUUID());
        return r;
    }

    private OnboardingStep buildStep(OnboardingStepType type) {
        OnboardingStep s = new OnboardingStep();
        s.setOnboardingRequest(request);
        s.setStepType(type);
        s.setState(OnboardingStepState.PENDING);
        return s;
    }
}