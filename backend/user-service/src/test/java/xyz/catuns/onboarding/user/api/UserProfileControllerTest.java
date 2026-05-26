package xyz.catuns.onboarding.user.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.catuns.onboarding.user.api.dto.*;
import xyz.catuns.onboarding.user.security.JwtPrincipalExtractor;
import xyz.catuns.onboarding.user.service.UserProfileService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileControllerTest {

    private MockMvc mockMvc;
    private UserProfileService profileService;
    private JwtPrincipalExtractor principalExtractor;

    private static final String GITHUB_USER_ID = "12345678";
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        profileService = Mockito.mock(UserProfileService.class);
        principalExtractor = Mockito.mock(JwtPrincipalExtractor.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new UserProfileController(profileService, principalExtractor))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        when(principalExtractor.extractUserId(any())).thenReturn(USER_ID.toString());
    }

    @Test
    void getMe_baseProfile_returns200WithCoreFields() throws Exception {
        when(profileService.getMe(eq(USER_ID.toString()), any()))
            .thenReturn(baseProfile());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.displayName").value("Student Dev"))
            .andExpect(jsonPath("$.primaryEmail").value("student@example.com"))
            .andExpect(jsonPath("$.status").value("PENDING_ONBOARDING"))
            .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
            .andExpect(jsonPath("$.github").doesNotExist())
            .andExpect(jsonPath("$.onboarding").doesNotExist());
    }

    @Test
    void getMe_includeIdentities_addsGithubAndAtlassianBlocks() throws Exception {
        GitHubIdentitySummary github = new GitHubIdentitySummary(GITHUB_USER_ID, "student-dev", "student@example.com");
        AtlassianIdentitySummary atlassian = new AtlassianIdentitySummary(null, null, "PENDING");
        MeResponse response = MeResponse.builder()
            .userId(USER_ID).displayName("Student Dev").primaryEmail("student@example.com")
            .status("PENDING_ONBOARDING").roles(List.of("STUDENT"))
            .github(github).atlassian(atlassian)
            .build();

        when(profileService.getMe(eq(USER_ID.toString()), argThat(s -> s.contains("identities"))))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/me?include=identities").header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.github.userId").value(GITHUB_USER_ID))
            .andExpect(jsonPath("$.github.login").value("student-dev"))
            .andExpect(jsonPath("$.atlassian.matchState").value("PENDING"));
    }

    @Test
    void getMe_includeOnboarding_addsOnboardingBlock() throws Exception {
        UUID requestId = UUID.randomUUID();
        MeResponse response = MeResponse.builder()
            .userId(USER_ID).displayName("Student Dev").primaryEmail("student@example.com")
            .status("PENDING_ONBOARDING").roles(List.of("STUDENT"))
            .onboarding(new OnboardingSummary(requestId, "IN_PROGRESS"))
            .build();

        when(profileService.getMe(eq(USER_ID.toString()), argThat(s -> s.contains("onboarding"))))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/me?include=onboarding").header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onboarding.requestId").value(requestId.toString()))
            .andExpect(jsonPath("$.onboarding.state").value("IN_PROGRESS"));
    }

    @Test
    void getMe_unknownUser_returns404() throws Exception {
        when(profileService.getMe(eq(USER_ID.toString()), any()))
            .thenThrow(new NoSuchElementException("No user found for githubUserId: " + GITHUB_USER_ID));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getMe_includesBothIdentitiesAndOnboarding_returns200() throws Exception {
        UUID requestId = UUID.randomUUID();
        MeResponse response = MeResponse.builder()
            .userId(USER_ID).displayName("Student Dev").primaryEmail("student@example.com")
            .status("PENDING_ONBOARDING").roles(List.of("STUDENT"))
            .github(new GitHubIdentitySummary(GITHUB_USER_ID, "student-dev", "student@example.com"))
            .atlassian(new AtlassianIdentitySummary(null, null, "PENDING"))
            .onboarding(new OnboardingSummary(requestId, "IN_PROGRESS"))
            .build();

        when(profileService.getMe(eq(USER_ID.toString()), argThat(s -> s.contains("identities") && s.contains("onboarding"))))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/me?include=identities,onboarding").header("Authorization", "Bearer dummy-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.github").exists())
            .andExpect(jsonPath("$.atlassian").exists())
            .andExpect(jsonPath("$.onboarding").exists());
    }

    private MeResponse baseProfile() {
        return MeResponse.builder()
            .userId(USER_ID)
            .displayName("Student Dev")
            .primaryEmail("student@example.com")
            .status("PENDING_ONBOARDING")
            .roles(List.of("STUDENT"))
            .build();
    }
}