package xyz.catuns.onboarding.service.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.catuns.onboarding.common.security.provider.ServicePrincipal;
import xyz.catuns.onboarding.common.security.provider.ServiceTokenProvider;
import xyz.catuns.onboarding.service.api.dto.ErrorResponse;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class InternalApiAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EXPECTED_CALLER = "user-service";
    private static final String EXPECTED_AUDIENCE = "onboarding-service";

    private final ServiceTokenProvider serviceTokenProvider;
    private final ObjectMapper objectMapper;

    public InternalApiAuthFilter(ServiceTokenProvider serviceTokenProvider, ObjectMapper objectMapper) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        ServicePrincipal principal;
        try {
            principal = serviceTokenProvider.validate(token);
        } catch (Exception e) {
            writeUnauthorized(response, "Invalid service token");
            return;
        }

        if (!EXPECTED_CALLER.equals(principal.serviceName())) {
            writeUnauthorized(response, "Unauthorized caller");
            return;
        }
        if (!EXPECTED_AUDIENCE.equals(principal.audience())) {
            writeUnauthorized(response, "Token audience mismatch");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        ErrorResponse error = ErrorResponse.of(HttpStatus.UNAUTHORIZED);
        error.setCode("UNAUTHORIZED");
        error.setDetail(detail);
        String rawCorrelationId = MDC.get("correlationId");
        if (rawCorrelationId != null) {
            try {
                error.setCorrelationId(UUID.fromString(rawCorrelationId));
            } catch (IllegalArgumentException ignored) {}
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}