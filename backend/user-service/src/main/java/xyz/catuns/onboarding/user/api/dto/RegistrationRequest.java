package xyz.catuns.onboarding.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RegistrationRequest {

    @NotBlank
    private String githubUserId;

    @NotBlank
    private String githubLogin;

    @Email
    private String primaryEmail;

    @NotBlank
    private String displayName;

    private String avatarUrl;

    private List<String> roleKeys;
}
