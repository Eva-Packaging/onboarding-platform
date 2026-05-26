package xyz.catuns.onboarding.service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.catuns.onboarding.common.security.provider.Payload;
import xyz.catuns.onboarding.common.security.provider.PayloadTokenProvider;

@Component
public class JwtPrincipalExtractor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final PayloadTokenProvider tokenProvider;

    public JwtPrincipalExtractor(PayloadTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public String extractUserId(HttpServletRequest request) {
        String token = extractBearerToken(request);
        Payload payload = tokenProvider.validate(token);
        String userId = payload.userId();
        if (userId == null) {
            throw new IllegalStateException("JWT is missing the required user claim");
        }

        return userId;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new IllegalStateException("Missing or malformed Authorization header");
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}