package xyz.catuns.onboarding.provisioning.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProvisioningMetrics {

    private final MeterRegistry meterRegistry;

    public ProvisioningMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordOutcome(String provider, String result) {
        meterRegistry.counter("provisioning.outcomes",
                "provider", provider,
                "result", result
        ).increment();
    }
}
