package xyz.catuns.onboarding.service.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.catuns.onboarding.service.api.dto.OnboardingStatusResponse;
import xyz.catuns.onboarding.service.exception.OnboardingAccessDeniedException;
import xyz.catuns.onboarding.service.exception.ResourceNotFoundException;
import xyz.catuns.onboarding.service.security.JwtPrincipalExtractor;
import xyz.catuns.onboarding.service.service.OnboardingStatusService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingControllerTest {

    private MockMvc mockMvc;
    private OnboardingStatusService statusService;
    private JwtPrincipalExtractor principalExtractor;

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        statusService = Mockito.mock(OnboardingStatusService.class);
        principalExtractor = Mockito.mock(JwtPrincipalExtractor.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new OnboardingController(statusService, principalExtractor))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        when(principalExtractor.extractUserId(any())).thenReturn(CALLER_ID);
    }

    @Test
    void getStatus_ownerRequest_returns200WithBody() throws Exception {
        when(statusService.findById(eq(REQUEST_ID), eq(CALLER_ID))).thenReturn(buildResponse());

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
        when(statusService.findById(eq(REQUEST_ID), eq(CALLER_ID)))
            .thenThrow(new ResourceNotFoundException("OnboardingRequest", REQUEST_ID));

        mockMvc.perform(get("/api/v1/onboarding/{requestId}", REQUEST_ID)
                .header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getStatus_nonOwner_returns403WithErrorEnvelope() throws Exception {
        when(statusService.findById(eq(REQUEST_ID), eq(CALLER_ID)))
            .thenThrow(new OnboardingAccessDeniedException(REQUEST_ID));

        mockMvc.perform(get("/api/v1/onboarding/{requestId}", REQUEST_ID)
                .header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getStatus_emptySteps_returnsEmptyArray() throws Exception {
        OnboardingStatusResponse response = new OnboardingStatusResponse(
            REQUEST_ID, CALLER_ID, "COMPLETED", CORRELATION_ID, Instant.now(), List.of()
        );
        when(statusService.findById(eq(REQUEST_ID), eq(CALLER_ID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/onboarding/{requestId}", REQUEST_ID)
                .header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps").isEmpty());
    }

    private OnboardingStatusResponse buildResponse() {
        return new OnboardingStatusResponse(
            REQUEST_ID, CALLER_ID, "IN_PROGRESS", CORRELATION_ID, Instant.now(), List.of()
        );
    }
}
