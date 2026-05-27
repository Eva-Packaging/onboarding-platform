package xyz.catuns.onboarding.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import xyz.catuns.onboarding.apigateway.config.properties.JwtProperties;
import xyz.catuns.onboarding.common.security.provider.Payload;
import xyz.catuns.spring.jwt.core.provider.TokenProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenProvider<Payload> tokenProvider;

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private JwtProperties jwtProperties;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getPublicPaths()).thenReturn(List.of(
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/actuator/**",
                "/error/**",
                "/api/v1/docs/**"
        ));
        filter = new JwtAuthenticationFilter(tokenProvider, jwtProperties);
    }

    @Test
    void getOrder_returnsHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void filter_publicPath_swaggerUi_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/swagger-ui/index.html").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(tokenProvider);
        verify(chain).filter(exchange);
    }

    @Test
    void filter_publicPath_actuatorHealth_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(tokenProvider);
        verify(chain).filter(exchange);
    }

    @Test
    void filter_missingAuthorizationHeader_returns401WithMessage() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("Missing or malformed Authorization header"))
                .verifyComplete();
        verifyNoInteractions(tokenProvider);
        verifyNoInteractions(chain);
    }

    @Test
    void filter_authHeaderWithoutBearerPrefix_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(tokenProvider);
        verifyNoInteractions(chain);
    }

    @Test
    void filter_validToken_withCorrelationId_forwardsUserIdAndCorrelationIdHeaders() {
        String token = "valid.jwt.token";
        Payload payload = new Payload("user-123", "corr-abc");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(tokenProvider.validate(token)).thenReturn(payload);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange mutated = captor.getValue();
        assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-123");
        assertThat(mutated.getRequest().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-abc");
    }

    @Test
    void filter_validToken_withNullCorrelationId_generatesRandomUuidAsCorrelationId() {
        String token = "valid.jwt.token";
        Payload payload = new Payload("user-456", null);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/onboarding/status")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(tokenProvider.validate(token)).thenReturn(payload);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange mutated = captor.getValue();
        assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-456");
        String correlationId = mutated.getRequest().getHeaders().getFirst("X-Correlation-Id");
        assertThat(correlationId).isNotNull().isNotEmpty();
        assertThatNoException().isThrownBy(() -> UUID.fromString(correlationId));
    }

    @Test
    void filter_invalidToken_tokenProviderThrows_returns401WithMessage() {
        String token = "expired.jwt.token";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(tokenProvider.validate(token)).thenThrow(new RuntimeException("Token expired"));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("Invalid or expired token"))
                .verifyComplete();
        verifyNoInteractions(chain);
    }

    @Test
    void filter_tokenWithSurroundingSpaces_trimsBeforeValidation() {
        String rawToken = "  trimmed.jwt.token  ";
        String trimmedToken = "trimmed.jwt.token";
        Payload payload = new Payload("user-789", "corr-xyz");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/onboarding/status")
                .header("Authorization", "Bearer" + rawToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(tokenProvider.validate(trimmedToken)).thenReturn(payload);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(tokenProvider).validate(trimmedToken);
    }
}