package xyz.catuns.onboarding.user.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.catuns.onboarding.common.security.provider.ServicePrincipal;
import xyz.catuns.onboarding.common.security.provider.ServiceTokenProvider;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalApiAuthFilterTest {

    private static final String SECRET = "test-secret-key-for-internal-api-auth-filter-tests-0123456789AB";

    private MockMvc mockMvc;
    private ServiceTokenProvider serviceTokenProvider;

    @BeforeEach
    void setUp() {
        serviceTokenProvider = new ServiceTokenProvider(SECRET, Duration.ofMinutes(5), "test-issuer");
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PingController())
            .addFilters(new InternalApiAuthFilter(serviceTokenProvider, new ObjectMapper()))
            .build();
    }

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ping"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void malformedAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ping").header("Authorization", "not-a-bearer-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ping").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void wrongCaller_returns401() throws Exception {
        String token = serviceTokenProvider.generate(new ServicePrincipal("some-other-service", "user-service")).value();

        mockMvc.perform(get("/api/v1/internal/ping").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void wrongAudience_returns401() throws Exception {
        String token = serviceTokenProvider.generate(new ServicePrincipal("onboarding-service", "some-other-audience")).value();

        mockMvc.perform(get("/api/v1/internal/ping").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void validServiceToken_passesThrough() throws Exception {
        String token = serviceTokenProvider.generate(new ServicePrincipal("onboarding-service", "user-service")).value();

        mockMvc.perform(get("/api/v1/internal/ping").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void nonInternalPath_bypassesFilter() throws Exception {
        mockMvc.perform(get("/ping"))
            .andExpect(status().isOk());
    }

    @RestController
    static class PingController {
        @GetMapping("/api/v1/internal/ping")
        ResponseEntity<String> internalPing() {
            return ResponseEntity.ok("pong");
        }

        @GetMapping("/ping")
        ResponseEntity<String> ping() {
            return ResponseEntity.ok("pong");
        }
    }
}