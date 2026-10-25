package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.exception.EventPublicationException;
import com.telemed.identityaccess.application.port.in.RelayOutboxEventsUseCase;
import com.telemed.identityaccess.application.port.out.IntegrationEventPublisherPort;
import com.telemed.identityaccess.application.port.out.OutboxEventsPort;

/** Runs in one transaction, so the claimed rows stay locked until they are marked. */
public class RelayOutboxEventsService implements RelayOutboxEventsUseCase {

    // Fixed code: broker messages are not needed to retry and stay out of the table.
    static final String PUBLICATION_FAILED = "BROKER_PUBLICATION_FAILED";

    private final OutboxEventsPort outbox;
    private final IntegrationEventPublisherPort publisher;
    private final int batchSize;

    public RelayOutboxEventsService(
            OutboxEventsPort outbox,
            IntegrationEventPublisherPort publisher,
            int batchSize
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Relay batch size must be positive.");
        }

        this.outbox = outbox;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    @Override
    public int relayPending() {

        int published = 0;

        for (OutboxEvent event : outbox.claimPending(batchSize)) {
            try {
                publisher.publish(event);
            } catch (EventPublicationException exception) {
                // The next run retries it; one failing event does not hold back the rest.
                outbox.markFailed(event.id(), PUBLICATION_FAILED);
                continue;
            }

            outbox.markPublished(event.id());
            published++;
        }

        return published;
    }
}
