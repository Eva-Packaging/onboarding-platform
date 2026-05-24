package xyz.catuns.onboarding.user.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorrelationIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PingController())
            .addFilters(new CorrelationIdFilter(mock(Tracer.class)))
            .build();
    }

    @Test
    void inboundCorrelationId_isEchoedInResponseHeader() throws Exception {
        String correlationId = "cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77";

        mockMvc.perform(get("/ping").header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId))
            .andExpect(status().isOk())
            .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId));
    }

    @Test
    void missingCorrelationId_generatesNewUuidInResponseHeader() throws Exception {
        String responseHeader = mockMvc.perform(get("/ping"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(responseHeader).isNotNull().isNotBlank();
        assertThat(java.util.UUID.fromString(responseHeader)).isNotNull();
    }

    @Test
    void mdcIsCleared_afterRequestCompletes() throws Exception {
        mockMvc.perform(get("/ping").header(CorrelationIdFilter.CORRELATION_ID_HEADER, "some-id"))
            .andExpect(status().isOk());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void twoConsecutiveRequests_haveIndependentCorrelationIds() throws Exception {
        String first = "id-first-request";
        String second = "id-second-request";

        mockMvc.perform(get("/ping").header(CorrelationIdFilter.CORRELATION_ID_HEADER, first))
            .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, first));

        mockMvc.perform(get("/ping").header(CorrelationIdFilter.CORRELATION_ID_HEADER, second))
            .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, second));
    }

    @Test
    void activeSpan_isTaggedWithCorrelationId() throws Exception {
        Span span = mock(Span.class);
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(span);

        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new PingController())
            .addFilters(new CorrelationIdFilter(tracer))
            .build();

        mvc.perform(get("/ping").header(CorrelationIdFilter.CORRELATION_ID_HEADER, "trace-id-abc"))
            .andExpect(status().isOk());

        verify(span).tag("correlationId", "trace-id-abc");
    }

    @RestController
    static class PingController {
        @GetMapping("/ping")
        ResponseEntity<String> ping() {
            return ResponseEntity.ok("pong");
        }
    }
}