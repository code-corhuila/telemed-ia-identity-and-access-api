package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.application.event.IntegrationEvent;

/**
 * Records integration events in the same transaction as the business change.
 */
public interface IntegrationEventOutboxPort {

    void record(
            IntegrationEvent event
    );
}
