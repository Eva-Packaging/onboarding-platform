package xyz.catuns.onboarding.service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import xyz.catuns.onboarding.service.api.dto.OnboardingStatusResponse;
import xyz.catuns.onboarding.service.api.dto.StepDetailDto;
import xyz.catuns.onboarding.service.domain.*;
import xyz.catuns.onboarding.service.exception.OnboardingAccessDeniedException;
import xyz.catuns.onboarding.service.exception.ResourceNotFoundException;
import xyz.catuns.onboarding.service.repository.OnboardingRequestRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;
import xyz.catuns.onboarding.service.repository.ProviderTargetRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class OnboardingStatusServiceTest {

    private OnboardingRequestRepository requestRepository;
    private OnboardingStepRepository stepRepository;
    private ProviderTargetRepository providerTargetRepository;
    private OnboardingStatusService statusService;

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String OWNER_ID = "123456789";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        requestRepository = Mockito.mock(OnboardingRequestRepository.class);
        stepRepository = Mockito.mock(OnboardingStepRepository.class);
        providerTargetRepository = Mockito.mock(ProviderTargetRepository.class);
        statusService = new OnboardingStatusService(requestRepository, stepRepository, providerTargetRepository);
    }

    @Test
    void findById_ownerAccess_returnsStatusResponse() {
        OnboardingRequest request = buildRequest(OWNER_ID, OnboardingRequestState.IN_PROGRESS);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of());
        when(providerTargetRepository.findAllById(List.of())).thenReturn(List.of());

        OnboardingStatusResponse response = statusService.findById(REQUEST_ID, OWNER_ID);

        assertThat(response.requestId()).isEqualTo(REQUEST_ID);
        assertThat(response.userId()).isEqualTo(OWNER_ID);
        assertThat(response.state()).isEqualTo("IN_PROGRESS");
        assertThat(response.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(response.steps()).isEmpty();
    }

    @Test
    void findById_nonOwner_throwsAccessDeniedException() {
        String nonOwner = "999999999";
        OnboardingRequest request = buildRequest(OWNER_ID, OnboardingRequestState.IN_PROGRESS);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> statusService.findById(REQUEST_ID, nonOwner))
            .isInstanceOf(OnboardingAccessDeniedException.class)
            .hasMessageContaining(REQUEST_ID.toString());
    }

    @Test
    void findById_requestNotFound_throwsResourceNotFoundException() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statusService.findById(REQUEST_ID, OWNER_ID))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(REQUEST_ID.toString());
    }

    @Test
    void findById_stepWithProviderTarget_includesTargetDetails() {
        UUID targetId = UUID.randomUUID();
        OnboardingRequest request = buildRequest(OWNER_ID, OnboardingRequestState.IN_PROGRESS);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep step = buildStep(StepType.GITHUB_TEAM_PROVISIONING, OnboardingStepState.SUCCEEDED, targetId);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of(step));

        ProviderTarget target = buildProviderTarget(targetId, "GITHUB_TEAM", "org/devs-team");
        when(providerTargetRepository.findAllById(java.util.Set.of(targetId))).thenReturn(List.of(target));

        OnboardingStatusResponse response = statusService.findById(REQUEST_ID, OWNER_ID);

        assertThat(response.steps()).hasSize(1);
        StepDetailDto stepDto = response.steps().get(0);
        assertThat(stepDto.type()).isEqualTo("GITHUB_TEAM_PROVISIONING");
        assertThat(stepDto.state()).isEqualTo("SUCCEEDED");
        assertThat(stepDto.target()).isNotNull();
        assertThat(stepDto.target().provider()).isEqualTo("GITHUB");
        assertThat(stepDto.target().targetType()).isEqualTo("GITHUB_TEAM");
        assertThat(stepDto.target().externalKey()).isEqualTo("org/devs-team");
    }

    @Test
    void findById_stepWithNoProviderTarget_targetIsNull() {
        OnboardingRequest request = buildRequest(OWNER_ID, OnboardingRequestState.IN_PROGRESS);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep step = buildStep(StepType.IDENTITY_CORRELATION, OnboardingStepState.PENDING, null);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of(step));
        when(providerTargetRepository.findAllById(java.util.Set.of())).thenReturn(List.of());

        OnboardingStatusResponse response = statusService.findById(REQUEST_ID, OWNER_ID);

        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).target()).isNull();
    }

    @Test
    void findById_atlassianStep_derivesAtlassianProvider() {
        UUID targetId = UUID.randomUUID();
        OnboardingRequest request = buildRequest(OWNER_ID, OnboardingRequestState.IN_PROGRESS);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        OnboardingStep step = buildStep(StepType.JIRA_GROUP_PROVISIONING, OnboardingStepState.SUCCEEDED, targetId);
        when(stepRepository.findByOnboardingRequest_Id(REQUEST_ID)).thenReturn(List.of(step));

        ProviderTarget target = buildProviderTarget(targetId, "ATLASSIAN_GROUP", "jira-developers");
        when(providerTargetRepository.findAllById(java.util.Set.of(targetId))).thenReturn(List.of(target));

        OnboardingStatusResponse response = statusService.findById(REQUEST_ID, OWNER_ID);

        assertThat(response.steps().get(0).target().provider()).isEqualTo("ATLASSIAN");
    }

    private OnboardingRequest buildRequest(String userId, OnboardingRequestState state) {
        OnboardingRequest req = new OnboardingRequest();
        req.setId(REQUEST_ID);
        req.setUserProfileId(userId);
        req.setState(state);
        req.setCorrelationId(CORRELATION_ID);
        req.setStartedAt(Instant.now());
        return req;
    }

    private OnboardingStep buildStep(StepType stepType, OnboardingStepState state, UUID targetId) {
        OnboardingStepType type = Mockito.mock(OnboardingStepType.class);
        when(type.getStepKey()).thenReturn(stepType);

        OnboardingStep step = new OnboardingStep();
        step.setStepType(type);
        step.setState(state);
        step.setProviderTargetId(targetId);
        step.setAttemptCount(0);
        return step;
    }

    private ProviderTarget buildProviderTarget(UUID id, String targetType, String externalKey) {
        ProviderTarget target = new ProviderTarget();
        target.setId(id);
        target.setTargetType(targetType);
        target.setExternalKey(externalKey);
        target.setDisplayName(externalKey);
        target.setProviderId(UUID.randomUUID());
        return target;
    }
}
