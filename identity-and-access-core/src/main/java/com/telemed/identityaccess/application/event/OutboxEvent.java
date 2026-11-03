package com.telemed.identityaccess.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * An integration event as the outbox stored it, waiting to be published.
 *
 * <p>{@code traceparent} is the W3C trace context of the request that recorded it, or null
 * when no request did; the relay publishes the event in that trace.
 *
 * <p>{@code payload} is the event's JSON and may carry a {@code resetUrl}, so
 * {@link #toString()} leaves it out.
 */
public record OutboxEvent(
        UUID id,
        String eventType,
        UUID aggregateId,
        String payload,
        String correlationId,
        String traceparent,
        int version,
        Instant occurredAt
) {

    public OutboxEvent {

        if (id == null || aggregateId == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "Event ID, aggregate ID and occurrence time are required."
            );
        }

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Event type is required.");
        }

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Event payload is required.");
        }

        if (version < 1) {
            throw new IllegalArgumentException("Event version must be positive.");
        }
    }

    @Override
    public String toString() {
        return "OutboxEvent[id=" + id
                + ", eventType=" + eventType
                + ", aggregateId=" + aggregateId
                + ", correlationId=" + correlationId
                + ", traceparent=" + traceparent
                + ", version=" + version
                + ", occurredAt=" + occurredAt + "]";
    }
}
