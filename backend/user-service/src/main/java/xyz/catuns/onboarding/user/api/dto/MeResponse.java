package xyz.catuns.onboarding.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeResponse {

    private UUID userId;
    private String displayName;
    private String primaryEmail;
    private String status;
    private List<String> roles;
    private GitHubIdentitySummary github;
    private AtlassianIdentitySummary atlassian;
    private OnboardingSummary onboarding;
}