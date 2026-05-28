package xyz.catuns.onboarding.service.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.catuns.onboarding.service.api.dto.OnboardingRetryResponse;
import xyz.catuns.onboarding.service.api.dto.OnboardingStatusResponse;
import xyz.catuns.onboarding.service.domain.OnboardingStepState;
import xyz.catuns.onboarding.service.exception.OnboardingAccessDeniedException;
import xyz.catuns.onboarding.service.exception.ResourceNotFoundException;
import xyz.catuns.onboarding.service.exception.StepNotRetryableException;
import xyz.catuns.onboarding.service.security.JwtPrincipalExtractor;
import xyz.catuns.onboarding.service.service.OnboardingRetryService;
import xyz.catuns.onboarding.service.service.OnboardingStatusService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingControllerTest {

    private MockMvc mockMvc;
    private OnboardingStatusService statusService;
    private OnboardingRetryService retryService;
    private JwtPrincipalExtractor principalExtractor;

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        statusService = Mockito.mock(OnboardingStatusService.class);
        retryService = Mockito.mock(OnboardingRetryService.class);
        principalExtractor = Mockito.mock(JwtPrincipalExtractor.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new OnboardingController(statusService, retryService, principalExtractor))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        when(principalExtractor.extractUserId(any())).thenReturn(CALLER_ID.toString());
    }

    // --- GET /{requestId} ---

    @Test
    void getStatus_ownerRequest_returns200WithBody() throws Exception {
        when(statusService.findById(eq(REQUEST_ID), eq(CALLER_ID.toString()))).thenReturn(buildStatusResponse());

        mockMvc.perform(get("/api/v1/onboarding/{requestId}", REQUEST_ID)
                .header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
            .andExpect(jsonPath("$.userId").value(CALLER_ID.toString()))
            .andExpect(jsonPath("$.state").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()))
            .andExpect(jsonPath("$.steps").isArray());
    }

    @Test
    void getStatus_notFound_returns404WithErrorEnvelope() throws Exception {
        when(statusService.findById(eq(REQUEST_ID), eq(CALLER_ID.toString())))
            .thenThrow(new ResourceNotFoundException("OnboardingRequest", REQUEST_ID));

        mockMvc.perform(get("/api/v1/onboarding/{requestId}", REQUEST_ID)
                .header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getStatus_nonOwner_returns403WithErrorEnvelope() throws Exception {
        when(statusService.findById(eq(REQUEST_ID), eq(CALLER_ID.toString())))
            .thenThrow(new OnboardingAccessDeniedException(REQUEST_ID));

        mockMvc.perform(get("/api/v1/onboarding/{requestId}", REQUEST_ID)
                .header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getStatus_emptySteps_returnsEmptyArray() throws Exception {
        OnboardingStatusResponse response = new OnboardingStatusResponse(
            REQUEST_ID, CALLER_ID.toString(), "COMPLETED", CORRELATION_ID, Instant.now(), List.of()
        );
        when(statusService.findById(eq(REQUEST_ID), eq(CALLER_ID.toString()))).thenReturn(response);

        mockMvc.perform(get("/api/v1/onboarding/{requestId}", REQUEST_ID)
                .header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps").isEmpty());
    }

    // --- POST /{requestId}/retry ---

    @Test
    void retry_validRequest_returns202WithBody() throws Exception {
        OnboardingRetryResponse response = new OnboardingRetryResponse(
            REQUEST_ID, "IN_PROGRESS", List.of("JIRA_GROUP_PROVISIONING"), CORRELATION_ID
        );
        when(retryService.retry(eq(REQUEST_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/onboarding/{requestId}/retry", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"steps\":[\"JIRA_GROUP_PROVISIONING\"]}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
            .andExpect(jsonPath("$.state").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.requeuedSteps[0]").value("JIRA_GROUP_PROVISIONING"))
            .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()));
    }

    @Test
    void retry_emptyStepsList_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/{requestId}/retry", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"steps\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void retry_stepNotRetryable_returns409() throws Exception {
        when(retryService.retry(eq(REQUEST_ID), any()))
            .thenThrow(new StepNotRetryableException("GITHUB_TEAM_PROVISIONING", OnboardingStepState.SUCCEEDED));

        mockMvc.perform(post("/api/v1/onboarding/{requestId}/retry", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"steps\":[\"GITHUB_TEAM_PROVISIONING\"]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STEP_NOT_RETRYABLE"))
            .andExpect(jsonPath("$.details.stepType").value("GITHUB_TEAM_PROVISIONING"));
    }

    @Test
    void retry_requestNotFound_returns404() throws Exception {
        when(retryService.retry(eq(REQUEST_ID), any()))
            .thenThrow(new ResourceNotFoundException("OnboardingRequest", REQUEST_ID));

        mockMvc.perform(post("/api/v1/onboarding/{requestId}/retry", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"steps\":[\"JIRA_GROUP_PROVISIONING\"]}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private OnboardingStatusResponse buildStatusResponse() {
        return new OnboardingStatusResponse(
            REQUEST_ID, CALLER_ID.toString(), "IN_PROGRESS", CORRELATION_ID, Instant.now(), List.of()
        );
    }
}
