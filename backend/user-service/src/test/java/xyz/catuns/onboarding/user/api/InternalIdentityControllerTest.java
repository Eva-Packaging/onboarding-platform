package xyz.catuns.onboarding.user.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.catuns.onboarding.user.api.dto.IdentityLinkResponse;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.ProviderKey;
import xyz.catuns.onboarding.user.repository.ExternalIdentityRepository;
import xyz.catuns.onboarding.user.service.IdentityLinkService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalIdentityControllerTest {

    private MockMvc mockMvc;
    private ExternalIdentityRepository externalIdentityRepository;
    private IdentityLinkService identityLinkService;

    @BeforeEach
    void setUp() {
        externalIdentityRepository = Mockito.mock(ExternalIdentityRepository.class);
        identityLinkService = Mockito.mock(IdentityLinkService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new InternalIdentityController(externalIdentityRepository, identityLinkService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getExternalIdentity_atlassianMatchByEmail_returns200WithSummary() throws Exception {
        UUID identityId = UUID.randomUUID();
        ExternalIdentity identity = new ExternalIdentity();
        identity.setId(identityId);
        identity.setExternalUserId("atl-account-001");
        identity.setEmail("student@example.com");

        when(externalIdentityRepository.findByProvider_ProviderKeyAndEmail(eq(ProviderKey.ATLASSIAN), eq("student@example.com")))
            .thenReturn(Optional.of(identity));

        mockMvc.perform(get("/api/v1/internal/external-identities")
                .param("provider", "ATLASSIAN")
                .param("email", "student@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(identityId.toString()))
            .andExpect(jsonPath("$.accountId").value("atl-account-001"))
            .andExpect(jsonPath("$.email").value("student@example.com"))
            .andExpect(jsonPath("$.matchState").value("MATCHED"));
    }

    @Test
    void getExternalIdentity_noMatch_returns404() throws Exception {
        when(externalIdentityRepository.findByProvider_ProviderKeyAndEmail(eq(ProviderKey.ATLASSIAN), eq("missing@example.com")))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/internal/external-identities")
                .param("provider", "ATLASSIAN")
                .param("email", "missing@example.com"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getExternalIdentity_githubMatchByUserProfileId_returns200WithSummary() throws Exception {
        UUID identityId = UUID.randomUUID();
        UUID userProfileId = UUID.randomUUID();
        ExternalIdentity identity = new ExternalIdentity();
        identity.setId(identityId);
        identity.setExternalUserId("12345678");
        identity.setEmail("student@example.com");

        when(externalIdentityRepository.findByProvider_ProviderKeyAndUserProfile_Id(eq(ProviderKey.GITHUB), eq(userProfileId)))
            .thenReturn(Optional.of(identity));

        mockMvc.perform(get("/api/v1/internal/external-identities")
                .param("provider", "GITHUB")
                .param("userProfileId", userProfileId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(identityId.toString()))
            .andExpect(jsonPath("$.accountId").value("12345678"))
            .andExpect(jsonPath("$.matchState").value("MATCHED"));
    }

    @Test
    void createIdentityLink_validRequest_returns201WithBody() throws Exception {
        UUID userProfileId = UUID.randomUUID();
        UUID githubIdentityId = UUID.randomUUID();
        UUID atlassianIdentityId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        when(identityLinkService.createIdentityLink(any())).thenReturn(new IdentityLinkResponse(
            linkId, userProfileId, githubIdentityId, atlassianIdentityId, "EMAIL_EXACT", BigDecimal.ONE, createdAt
        ));

        String body = """
            {
              "userProfileId": "%s",
              "githubIdentityId": "%s",
              "atlassianIdentityId": "%s",
              "matchStrategy": "EMAIL_EXACT",
              "confidenceScore": 1.00
            }
            """.formatted(userProfileId, githubIdentityId, atlassianIdentityId);

        mockMvc.perform(post("/api/v1/internal/identity-links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(linkId.toString()))
            .andExpect(jsonPath("$.userProfileId").value(userProfileId.toString()))
            .andExpect(jsonPath("$.matchStrategy").value("EMAIL_EXACT"))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createIdentityLink_missingFields_returns400() throws Exception {
        String body = """
            {
              "matchStrategy": "EMAIL_EXACT"
            }
            """;

        mockMvc.perform(post("/api/v1/internal/identity-links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}