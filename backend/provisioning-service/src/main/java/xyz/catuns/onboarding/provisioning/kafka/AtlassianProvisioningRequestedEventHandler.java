package xyz.catuns.onboarding.provisioning.kafka;

import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningRequestedV1;
import xyz.catuns.onboarding.provisioning.service.ProvisioningEventService;

@Component
public class AtlassianProvisioningRequestedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AtlassianProvisioningRequestedEventHandler.class);

    private final ProvisioningEventService eventService;

    public AtlassianProvisioningRequestedEventHandler(ProvisioningEventService eventService) {
        this.eventService = eventService;
    }

    @KafkaListener(topics = "${app.kafka.topics.atlassian-provisioning}")
    public void consume(SpecificRecord event) {
        if (event instanceof AtlassianProvisioningRequestedV1 requested) {
            log.info("Received AtlassianProvisioningRequestedV1 for userId={}", requested.getUserId());
            eventService.handleAtlassianProvisioningRequested(requested);
        }
        // AtlassianProvisioningCompletedV1 produced by this service — silently ignored
    }
}