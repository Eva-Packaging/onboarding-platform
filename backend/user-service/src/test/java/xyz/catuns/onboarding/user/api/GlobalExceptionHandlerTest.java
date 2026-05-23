package xyz.catuns.onboarding.user.api;

import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.catuns.onboarding.user.api.dto.RegistrationRequest;
import xyz.catuns.onboarding.user.service.DuplicateRegistrationException;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @RestController
    @RequestMapping("/test")
    static class StubController {

        @PostMapping("/validate")
        void validate(@Valid @RequestBody RegistrationRequest request) {
        }

        @PostMapping("/duplicate")
        void duplicate() {
            throw new DuplicateRegistrationException("gh-123");
        }

        @PostMapping("/not-found")
        void notFound() {
            throw new NoSuchElementException("resource not found");
        }

        @PostMapping("/error")
        void unexpectedError() {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void missingRequiredField_returns400WithErrorEnvelope() throws Exception {
        String body = """
                {
                  "githubLogin": "student-dev",
                  "displayName": "Student Dev"
                }
                """;

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.details.githubUserId").exists());
    }

    @Test
    void duplicateRegistration_returns409WithErrorEnvelope() throws Exception {
        mockMvc.perform(post("/test/duplicate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REGISTRATION"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.details.githubUserId").value("gh-123"));
    }

    @Test
    void notFound_returns404WithErrorEnvelope() throws Exception {
        mockMvc.perform(post("/test/not-found")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void unhandledException_returns500WithErrorEnvelope() throws Exception {
        mockMvc.perform(post("/test/error")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void errorResponse_doesNotContainStackTrace() throws Exception {
        mockMvc.perform(post("/test/error")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}