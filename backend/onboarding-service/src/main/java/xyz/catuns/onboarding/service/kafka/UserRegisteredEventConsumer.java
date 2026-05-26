package xyz.catuns.onboarding.service.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import xyz.catuns.onboarding.common.events.UserRegisteredV1;
import xyz.catuns.onboarding.service.service.OnboardingEventService;

@Component
public class UserRegisteredEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventConsumer.class);

    private final OnboardingEventService onboardingEventService;

    public UserRegisteredEventConsumer(OnboardingEventService onboardingEventService) {
        this.onboardingEventService = onboardingEventService;
    }

    @KafkaListener(topics = "${app.kafka.topics.user-registered}")
    public void consume(UserRegisteredV1 event) {
        log.info("Received UserRegisteredV1 for userId={} onboardingRequestId={}",
                event.getUserId(), event.getOnboardingRequestId());
        onboardingEventService.handleUserRegistered(event);
    }
}