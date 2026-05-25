package xyz.catuns.onboarding.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenExchangeRequest {

    @NotBlank
    private String githubAccessToken;
}
