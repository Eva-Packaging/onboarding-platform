package xyz.catuns.onboarding.user.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.catuns.spring.jwt.auth.AuthTokenProvider;

import java.util.Map;

@Component
public class JwtPrincipalExtractor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenProvider tokenProvider;

    public JwtPrincipalExtractor(AuthTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public String extractGithubUserId(HttpServletRequest request) {
        String token = extractBearerToken(request);
        Map<String, Object> claims = tokenProvider.getClaims(token);
        Object userId = claims.get(AuthTokenProvider.USER_CLAIM_KEY);
        if (userId == null) {
            throw new IllegalStateException("JWT is missing the required user claim");
        }
        return String.valueOf(userId);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new IllegalStateException("Missing or malformed Authorization header");
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}