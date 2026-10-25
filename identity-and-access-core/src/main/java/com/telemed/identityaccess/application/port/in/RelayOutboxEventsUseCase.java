package com.telemed.identityaccess.application.port.in;

/**
 * HU-03: publishes the pending outbox events, oldest first. Delivery is at least
 * once, so consumers drop an event id they already processed.
 */
public interface RelayOutboxEventsUseCase {

    /** Returns how many events the broker took in this run. */
    int relayPending();
}
