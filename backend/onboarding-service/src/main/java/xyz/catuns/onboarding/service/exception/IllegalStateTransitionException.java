package xyz.catuns.onboarding.service.exception;

import java.util.UUID;

public class IllegalStateTransitionException extends RuntimeException {

    private final UUID entityId;
    private final Object from;
    private final Object to;

    public IllegalStateTransitionException(UUID entityId, Object from, Object to) {
        super(String.format("Illegal transition [%s] %s -> %s", entityId, from, to));
        this.entityId = entityId;
        this.from = from;
        this.to = to;
    }

    public UUID getEntityId() { return entityId; }
    public Object getFrom() { return from; }
    public Object getTo() { return to; }
}