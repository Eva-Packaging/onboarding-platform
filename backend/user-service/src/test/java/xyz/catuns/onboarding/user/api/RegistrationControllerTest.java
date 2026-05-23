package xyz.catuns.onboarding.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.catuns.onboarding.user.api.dto.StepSummaryDto;
import xyz.catuns.onboarding.user.client.OnboardingInitResponse;
import xyz.catuns.onboarding.user.client.OnboardingServiceClient;
import xyz.catuns.onboarding.user.exception.DuplicateRegistrationException;
import xyz.catuns.onboarding.user.exception.OnboardingServiceUnavailableException;
import xyz.catuns.onboarding.user.service.RegistrationResult;
import xyz.catuns.onboarding.user.service.UserRegistrationService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationControllerTest {

    private MockMvc mockMvc;
    private UserRegistrationService registrationService;
    private OnboardingServiceClient onboardingClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registrationService = Mockito.mock(UserRegistrationService.class);
        onboardingClient = Mockito.mock(OnboardingServiceClient.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RegistrationController(registrationService, onboardingClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_validRequest_returns201WithBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID onboardingRequestId = UUID.randomUUID();

        when(registrationService.register(any())).thenReturn(new RegistrationResult(userId, correlationId));
        when(onboardingClient.createOnboardingRequest(any(), any(), any()))
                .thenReturn(new OnboardingInitResponse(
                        onboardingRequestId,
                        "IN_PROGRESS",
                        List.of(
                                new StepSummaryDto("IDENTITY_CORRELATION", "PENDING"),
                                new StepSummaryDto("GITHUB_TEAM_PROVISIONING", "PENDING"),
                                new StepSummaryDto("JIRA_GROUP_PROVISIONING", "PENDING")
                        )
                ));

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.onboardingRequestId").value(onboardingRequestId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()))
                .andExpect(jsonPath("$.steps").isArray())
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].type").value("IDENTITY_CORRELATION"))
                .andExpect(jsonPath("$.steps[0].state").value("PENDING"));
    }

    @Test
    void register_missingGithubUserId_returns400() throws Exception {
        String body = """
                {
                  "githubLogin": "student-dev",
                  "displayName": "Student Dev"
                }
                """;

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.githubUserId").exists());
    }

    @Test
    void register_duplicateActiveUser_returns409() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new DuplicateRegistrationException("gh-123"));

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REGISTRATION"))
                .andExpect(jsonPath("$.details.githubUserId").value("gh-123"));
    }

    @Test
    void register_onboardingServiceDown_returns503() throws Exception {
        when(registrationService.register(any()))
                .thenReturn(new RegistrationResult(UUID.randomUUID(), UUID.randomUUID()));
        when(onboardingClient.createOnboardingRequest(any(), any(), any()))
                .thenThrow(new OnboardingServiceUnavailableException(
                        "Onboarding service unavailable", new RuntimeException("connection refused")));

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ONBOARDING_SERVICE_UNAVAILABLE"));
    }

    @Test
    void register_correlationIdPresentInResponse() throws Exception {
        UUID correlationId = UUID.randomUUID();
        when(registrationService.register(any()))
                .thenReturn(new RegistrationResult(UUID.randomUUID(), correlationId));
        when(onboardingClient.createOnboardingRequest(any(), any(), any()))
                .thenReturn(new OnboardingInitResponse(UUID.randomUUID(), "IN_PROGRESS", List.of()));

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()));
    }

    private String validRequestBody() {
        return """
                {
                  "githubUserId": "12345678",
                  "githubLogin": "student-dev",
                  "primaryEmail": "student@example.com",
                  "displayName": "Student Dev",
                  "roleKeys": ["STUDENT"]
                }
                """;
    }
}