package xyz.catuns.onboarding.apigateway.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.catuns.onboarding.apigateway.config.properties.JwtProperties;
import xyz.catuns.onboarding.common.security.provider.Payload;
import xyz.catuns.spring.jwt.core.provider.TokenProvider;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_USER_ID = "X-User-Id";
    private static final String X_CORRELATION_ID = "X-Correlation-Id";

    private final TokenProvider<Payload> tokenProvider;
    private final JwtProperties jwtProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isBypassed(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Payload payload = tokenProvider.validate(token);
            String correlationId = payload.correlationId() != null
                    ? payload.correlationId()
                    : UUID.randomUUID().toString();

            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.set(X_USER_ID, payload.userId());
                        headers.set(X_CORRELATION_ID, correlationId);
                    }))
                    .build();

            return chain.filter(mutated);

        } catch (Exception e) {
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private boolean isBypassed(String path) {
        return jwtProperties.getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}