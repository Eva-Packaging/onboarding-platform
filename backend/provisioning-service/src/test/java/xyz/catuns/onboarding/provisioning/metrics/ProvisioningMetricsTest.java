package xyz.catuns.onboarding.provisioning.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningMetricsTest {

    private SimpleMeterRegistry registry;
    private ProvisioningMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ProvisioningMetrics(registry);
    }

    @Test
    void recordOutcome_incrementsCounterWithProviderAndResultTags() {
        metrics.recordOutcome("github", "success");
        metrics.recordOutcome("github", "success");
        metrics.recordOutcome("atlassian", "failed");

        assertThat(registry.counter("provisioning.outcomes", "provider", "github", "result", "success").count())
                .isEqualTo(2.0);
        assertThat(registry.counter("provisioning.outcomes", "provider", "atlassian", "result", "failed").count())
                .isEqualTo(1.0);
    }

    @Test
    void recordOutcome_differentProviders_areTrackedIndependently() {
        metrics.recordOutcome("github", "success");
        metrics.recordOutcome("atlassian", "success");

        assertThat(registry.counter("provisioning.outcomes", "provider", "github", "result", "success").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("provisioning.outcomes", "provider", "atlassian", "result", "success").count())
                .isEqualTo(1.0);
    }

    @Test
    void recordOutcome_sameProviderDifferentResults_areTrackedIndependently() {
        metrics.recordOutcome("github", "success");
        metrics.recordOutcome("github", "pending");
        metrics.recordOutcome("github", "failed");

        assertThat(registry.counter("provisioning.outcomes", "provider", "github", "result", "success").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("provisioning.outcomes", "provider", "github", "result", "pending").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("provisioning.outcomes", "provider", "github", "result", "failed").count())
                .isEqualTo(1.0);
    }
}
