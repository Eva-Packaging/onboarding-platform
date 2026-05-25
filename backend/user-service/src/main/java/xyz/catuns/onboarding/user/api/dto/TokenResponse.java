package xyz.catuns.onboarding.user.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponse {
    private String token;
    private long expiresIn;
}
