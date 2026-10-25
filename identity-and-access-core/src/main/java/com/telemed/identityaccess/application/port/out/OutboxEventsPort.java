package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.application.event.OutboxEvent;

import java.util.List;
import java.util.UUID;

/** Relay side of the outbox; the caller holds one transaction around a run. */
public interface OutboxEventsPort {

    /** Locks up to {@code limit} unpublished events, oldest first, skipping those another relay holds. */
    List<OutboxEvent> claimPending(int limit);

    /** Marks the event published and drops its resetUrl, which nobody may read again. */
    void markPublished(UUID eventId);

    /** Leaves the event pending and records one more failed attempt. */
    void markFailed(UUID eventId, String errorCode);
}
