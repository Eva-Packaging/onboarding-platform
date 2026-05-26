package xyz.catuns.onboarding.provisioning.kafka;

import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import xyz.catuns.onboarding.common.events.GithubProvisioningRequestedV1;
import xyz.catuns.onboarding.provisioning.service.ProvisioningEventService;

@Component
public class GithubProvisioningRequestedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(GithubProvisioningRequestedEventHandler.class);

    private final ProvisioningEventService eventService;

    public GithubProvisioningRequestedEventHandler(ProvisioningEventService eventService) {
        this.eventService = eventService;
    }

    @KafkaListener(topics = "${app.kafka.topics.github-provisioning}")
    public void consume(SpecificRecord event) {
        if (event instanceof GithubProvisioningRequestedV1 requested) {
            log.info("Received GithubProvisioningRequestedV1 for userId={}", requested.getUserId());
            eventService.handleGithubProvisioningRequested(requested);
        }
        // GithubProvisioningCompletedV1 produced by this service — silently ignored
    }
}