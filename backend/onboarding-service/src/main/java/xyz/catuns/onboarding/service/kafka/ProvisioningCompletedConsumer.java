package xyz.catuns.onboarding.service.kafka;

import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningCompletedV1;
import xyz.catuns.onboarding.common.events.GithubProvisioningCompletedV1;
import xyz.catuns.onboarding.common.events.IdentityCorrelationCompletedV1;
import xyz.catuns.onboarding.common.events.IdentityCorrelationFailedV1;
import xyz.catuns.onboarding.common.events.IdentityCorrelationRequestedV1;
import xyz.catuns.onboarding.service.service.IdentityCorrelationService;
import xyz.catuns.onboarding.service.service.OnboardingEventService;

@Component
public class ProvisioningCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningCompletedConsumer.class);

    private final OnboardingEventService onboardingEventService;
    private final IdentityCorrelationService identityCorrelationService;

    public ProvisioningCompletedConsumer(OnboardingEventService onboardingEventService,
            IdentityCorrelationService identityCorrelationService) {
        this.onboardingEventService = onboardingEventService;
        this.identityCorrelationService = identityCorrelationService;
    }

    @KafkaListener(topics = "${app.kafka.topics.id-correlation}")
    public void consumeIdentityCorrelation(SpecificRecord event) {
        if (event instanceof IdentityCorrelationRequestedV1 requested) {
            log.info("Received IdentityCorrelationRequestedV1 for request={}",
                    requested.getOnboardingRequestId());
            identityCorrelationService.handleIdentityCorrelationRequested(requested);
        } else if (event instanceof IdentityCorrelationCompletedV1 completed) {
            log.info("Received IdentityCorrelationCompletedV1 for request={}",
                    completed.getOnboardingRequestId());
            onboardingEventService.handleIdentityCorrelationCompleted(completed);
        } else if (event instanceof IdentityCorrelationFailedV1 failed) {
            log.info("Received IdentityCorrelationFailedV1 for request={} reason={}",
                    failed.getOnboardingRequestId(), failed.getReasonCode());
            onboardingEventService.handleIdentityCorrelationFailed(failed);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.github-provisioning}")
    public void consumeGithubProvisioning(SpecificRecord event) {
        if (event instanceof GithubProvisioningCompletedV1 completed) {
            log.info("Received GithubProvisioningCompletedV1 for request={} success={}",
                    completed.getOnboardingRequestId(), completed.getSuccess());
            onboardingEventService.handleGithubProvisioningCompleted(completed);
        }
        // GithubProvisioningRequestedV1 produced by this service — silently ignored
    }

    @KafkaListener(topics = "${app.kafka.topics.atlassian-provisioning}")
    public void consumeAtlassianProvisioning(SpecificRecord event) {
        if (event instanceof AtlassianProvisioningCompletedV1 completed) {
            log.info("Received AtlassianProvisioningCompletedV1 for request={} success={}",
                    completed.getOnboardingRequestId(), completed.getSuccess());
            onboardingEventService.handleAtlassianProvisioningCompleted(completed);
        }
        // AtlassianProvisioningRequestedV1 produced by this service — silently ignored
    }
}