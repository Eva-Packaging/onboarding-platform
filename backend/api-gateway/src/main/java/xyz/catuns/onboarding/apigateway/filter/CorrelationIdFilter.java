package xyz.catuns.onboarding.apigateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    static final String X_CORRELATION_ID = "X-Correlation-Id";

    private final Tracer tracer;

    public CorrelationIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public int getOrder() {
        // Runs just after JwtAuthenticationFilter (HIGHEST_PRECEDENCE) so it sees
        // the correlation ID already injected by JWT validation on authenticated paths.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(X_CORRELATION_ID);
        String correlationId = (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString();

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> h.set(X_CORRELATION_ID, correlationId)))
                .build();

        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(X_CORRELATION_ID, correlationId);
            return Mono.empty();
        });

        return chain.filter(mutated)
                .doFirst(() -> {
                    Span span = tracer.currentSpan();
                    if (span != null) {
                        span.tag("correlationId", correlationId);
                    }
                });
    }
}