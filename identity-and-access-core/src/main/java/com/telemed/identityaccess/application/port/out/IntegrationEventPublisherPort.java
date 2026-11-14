package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.exception.EventPublicationException;

public interface IntegrationEventPublisherPort {

    /**
     * Returns once the broker confirmed the event.
     *
     * @throws EventPublicationException when it did not
     */
    void publish(OutboxEvent event);
}
