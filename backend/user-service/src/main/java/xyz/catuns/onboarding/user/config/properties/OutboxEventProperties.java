package xyz.catuns.onboarding.user.config.properties;

import lombok.Data;

@Data
public class OutboxEventProperties {

    /**
     * Outbox Event Poll Interval ms
     * Will poll outbox event repo for missed events
     * at this interval
     */
    private Long pollIntervalMs;

}
