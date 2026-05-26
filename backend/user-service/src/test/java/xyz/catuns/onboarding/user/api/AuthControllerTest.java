package xyz.catuns.onboarding.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.catuns.onboarding.user.api.dto.TokenExchangeRequest;
import xyz.catuns.onboarding.user.api.dto.TokenResponse;
import xyz.catuns.onboarding.user.exception.GitHubAuthenticationException;
import xyz.catuns.onboarding.user.service.TokenExchangeService;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private TokenExchangeService tokenExchangeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tokenExchangeService = mock(TokenExchangeService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(tokenExchangeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exchangeToken_validRequest_returns200WithToken() throws Exception {
        when(tokenExchangeService.exchange(any())).thenReturn(
                TokenResponse.builder().token("signed.jwt.value").expiresIn(86400L).build());

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "githubAccessToken": "gho_valid" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.value"))
                .andExpect(jsonPath("$.expiresIn").value(86400));
    }

    @Test
    void exchangeToken_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.githubAccessToken").exists());
    }

    @Test
    void exchangeToken_invalidGitHubToken_returns401() throws Exception {
        when(tokenExchangeService.exchange(any()))
                .thenThrow(new GitHubAuthenticationException("Invalid or expired GitHub access token"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "githubAccessToken": "gho_bad" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GITHUB_AUTH_FAILED"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired GitHub access token"));
    }

    @Test
    void exchangeToken_unregisteredUser_returns404() throws Exception {
        when(tokenExchangeService.exchange(any()))
                .thenThrow(new NoSuchElementException("No registered user found for GitHub account: ghost-user"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "githubAccessToken": "gho_valid" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No registered user found for GitHub account: ghost-user"));
    }
}
