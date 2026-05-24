package xyz.catuns.onboarding.apigateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private Tracer tracer;

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter(tracer);
    }

    @Test
    void getOrder_isHighestPrecedencePlusOne() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }

    @Test
    void filter_noCorrelationId_generatesUuidAndAddsToRequestAndResponse() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange mutated = captor.getValue();
        String requestCorrelationId = mutated.getRequest().getHeaders().getFirst(CorrelationIdFilter.X_CORRELATION_ID);
        assertThat(requestCorrelationId).isNotNull().isNotEmpty();
        assertThatNoException().isThrownBy(() -> UUID.fromString(requestCorrelationId));

        // Trigger beforeCommit handlers so response headers are written
        StepVerifier.create(mutated.getResponse().setComplete())
                .verifyComplete();
        assertThat(mutated.getResponse().getHeaders().getFirst(CorrelationIdFilter.X_CORRELATION_ID))
                .isEqualTo(requestCorrelationId);
    }

    @Test
    void filter_withExistingCorrelationId_preservesItOnRequestAndResponse() {
        String existingId = "existing-correlation-id";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/onboarding/status")
                .header(CorrelationIdFilter.X_CORRELATION_ID, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange mutated = captor.getValue();
        assertThat(mutated.getRequest().getHeaders().getFirst(CorrelationIdFilter.X_CORRELATION_ID))
                .isEqualTo(existingId);

        StepVerifier.create(mutated.getResponse().setComplete())
                .verifyComplete();
        assertThat(mutated.getResponse().getHeaders().getFirst(CorrelationIdFilter.X_CORRELATION_ID))
                .isEqualTo(existingId);
    }

    @Test
    void filter_withBlankCorrelationId_treatsAsAbsentAndGeneratesUuid() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(CorrelationIdFilter.X_CORRELATION_ID, "   ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        String generated = captor.getValue().getRequest().getHeaders().getFirst(CorrelationIdFilter.X_CORRELATION_ID);
        assertThat(generated).isNotBlank().isNotEqualTo("   ");
        assertThatNoException().isThrownBy(() -> UUID.fromString(generated));
    }

    @Test
    void filter_generatedIdsAreUniqueAcrossRequests() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerWebExchange exchange1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());
        MockServerWebExchange exchange2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());

        StepVerifier.create(filter.filter(exchange1, chain)).verifyComplete();
        StepVerifier.create(filter.filter(exchange2, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(2)).filter(captor.capture());

        String id1 = captor.getAllValues().get(0).getRequest().getHeaders().getFirst(CorrelationIdFilter.X_CORRELATION_ID);
        String id2 = captor.getAllValues().get(1).getRequest().getHeaders().getFirst(CorrelationIdFilter.X_CORRELATION_ID);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void filter_activeSpan_isTaggedWithCorrelationId() {
        Span span = mock(Span.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        String correlationId = "span-tag-test-id";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(CorrelationIdFilter.X_CORRELATION_ID, correlationId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(span).tag("correlationId", correlationId);
    }

    @Test
    void filter_noActiveSpan_doesNotThrow() {
        when(tracer.currentSpan()).thenReturn(null);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }
}