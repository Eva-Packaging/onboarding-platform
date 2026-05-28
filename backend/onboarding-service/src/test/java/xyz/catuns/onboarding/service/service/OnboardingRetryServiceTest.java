package xyz.catuns.onboarding.service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import xyz.catuns.onboarding.service.api.dto.OnboardingRetryRequest;
import xyz.catuns.onboarding.service.api.dto.OnboardingRetryResponse;
import xyz.catuns.onboarding.service.domain.*;
import xyz.catuns.onboarding.service.exception.ResourceNotFoundException;
import xyz.catuns.onboarding.service.exception.StepNotRetryableException;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OnboardingRetryServiceTest {

    private OnboardingRequestRepository requestRepository;
    private OnboardingStepRepository stepRepository;
    private OnboardingDomainService domainService;
    private OnboardingRetryService retryService;

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        requestRepository = Mockito.mock(OnboardingRequestRepository.class);
        stepRepository = Mockito.mock(OnboardingStepRepository.class);
        domainService = Mockito.mock(OnboardingDomainService.class);
        retryService = new OnboardingRetryService(requestRepository, stepRepository, domainService);
    }

    @Test
    void retry_failedStep_transitionsToPendingAndReturns202() {
        OnboardingRequest request = buildRequest(OnboardingRequestState.FAILED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep step = buildStep(StepType.JIRA_GROUP_PROVISIONING, OnboardingStepState.FAILED);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of(step));

        when(domainService.transitionStep(eq(step), eq(OnboardingStepState.PENDING))).thenAnswer(inv -> {
            step.setState(OnboardingStepState.PENDING);
            request.setState(OnboardingRequestState.IN_PROGRESS);
            return step;
        });

        OnboardingRetryResponse response = retryService.retry(REQUEST_ID,
            new OnboardingRetryRequest(List.of("JIRA_GROUP_PROVISIONING"), null));

        assertThat(response.requestId()).isEqualTo(REQUEST_ID);
        assertThat(response.requeuedSteps()).containsExactly("JIRA_GROUP_PROVISIONING");
        assertThat(response.correlationId()).isEqualTo(CORRELATION_ID);
        verify(domainService).transitionStep(step, OnboardingStepState.PENDING);
    }

    @Test
    void retry_manualReviewStep_transitionsToPendingAndReturns202() {
        OnboardingRequest request = buildRequest(OnboardingRequestState.FAILED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep step = buildStep(StepType.GITHUB_TEAM_PROVISIONING, OnboardingStepState.MANUAL_REVIEW);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of(step));

        when(domainService.transitionStep(any(), any())).thenReturn(step);

        OnboardingRetryResponse response = retryService.retry(REQUEST_ID,
            new OnboardingRetryRequest(List.of("GITHUB_TEAM_PROVISIONING"), "manual override"));

        assertThat(response.requeuedSteps()).containsExactly("GITHUB_TEAM_PROVISIONING");
        verify(domainService).transitionStep(step, OnboardingStepState.PENDING);
    }

    @Test
    void retry_succeededStep_throwsStepNotRetryableException() {
        OnboardingRequest request = buildRequest(OnboardingRequestState.COMPLETED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep step = buildStep(StepType.GITHUB_TEAM_PROVISIONING, OnboardingStepState.SUCCEEDED);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of(step));

        assertThatThrownBy(() -> retryService.retry(REQUEST_ID,
                new OnboardingRetryRequest(List.of("GITHUB_TEAM_PROVISIONING"), null)))
            .isInstanceOf(StepNotRetryableException.class)
            .hasMessageContaining("GITHUB_TEAM_PROVISIONING");

        verifyNoInteractions(domainService);
    }

    @Test
    void retry_processingStep_throwsStepNotRetryableException() {
        OnboardingRequest request = buildRequest(OnboardingRequestState.IN_PROGRESS);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep step = buildStep(StepType.IDENTITY_CORRELATION, OnboardingStepState.PROCESSING);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of(step));

        assertThatThrownBy(() -> retryService.retry(REQUEST_ID,
                new OnboardingRetryRequest(List.of("IDENTITY_CORRELATION"), null)))
            .isInstanceOf(StepNotRetryableException.class)
            .satisfies(ex -> assertThat(((StepNotRetryableException) ex).getStepKey())
                .isEqualTo("IDENTITY_CORRELATION"));

        verifyNoInteractions(domainService);
    }

    @Test
    void retry_unknownRequest_throwsResourceNotFoundException() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> retryService.retry(REQUEST_ID,
                new OnboardingRetryRequest(List.of("JIRA_GROUP_PROVISIONING"), null)))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(REQUEST_ID.toString());

        verifyNoInteractions(stepRepository);
        verifyNoInteractions(domainService);
    }

    @Test
    void retry_stepTypeNotInRequest_throwsStepNotRetryableException() {
        OnboardingRequest request = buildRequest(OnboardingRequestState.FAILED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep step = buildStep(StepType.IDENTITY_CORRELATION, OnboardingStepState.FAILED);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of(step));

        assertThatThrownBy(() -> retryService.retry(REQUEST_ID,
                new OnboardingRetryRequest(List.of("GITHUB_TEAM_PROVISIONING"), null)))
            .isInstanceOf(StepNotRetryableException.class)
            .hasMessageContaining("GITHUB_TEAM_PROVISIONING");

        verifyNoInteractions(domainService);
    }

    @Test
    void retry_multipleFailedSteps_allTransitioned() {
        OnboardingRequest request = buildRequest(OnboardingRequestState.FAILED);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep githubStep = buildStep(StepType.GITHUB_TEAM_PROVISIONING, OnboardingStepState.FAILED);
        OnboardingStep jiraStep = buildStep(StepType.JIRA_GROUP_PROVISIONING, OnboardingStepState.FAILED);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID))
            .thenReturn(List.of(githubStep, jiraStep));
        when(domainService.transitionStep(any(), any())).thenReturn(githubStep);

        OnboardingRetryResponse response = retryService.retry(REQUEST_ID,
            new OnboardingRetryRequest(List.of("GITHUB_TEAM_PROVISIONING", "JIRA_GROUP_PROVISIONING"), null));

        assertThat(response.requeuedSteps()).containsExactly("GITHUB_TEAM_PROVISIONING", "JIRA_GROUP_PROVISIONING");
        verify(domainService).transitionStep(githubStep, OnboardingStepState.PENDING);
        verify(domainService).transitionStep(jiraStep, OnboardingStepState.PENDING);
    }

    private OnboardingRequest buildRequest(OnboardingRequestState state) {
        OnboardingRequest request = new OnboardingRequest();
        request.setId(REQUEST_ID);
        request.setUserProfileId(USER_ID.toString());
        request.setState(state);
        request.setCorrelationId(CORRELATION_ID);
        request.setStartedAt(Instant.now());
        return request;
    }

    private OnboardingStep buildStep(StepType stepType, OnboardingStepState state) {
        OnboardingStepType type = Mockito.mock(OnboardingStepType.class);
        when(type.getStepKey()).thenReturn(stepType);

        OnboardingStep step = new OnboardingStep();
        step.setStepType(type);
        step.setState(state);
        step.setAttemptCount(1);
        return step;
    }
}
