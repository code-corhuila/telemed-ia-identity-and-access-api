package com.telemed.identityaccess.application.event;

import java.util.UUID;

/**
 * Business fact published to other bounded contexts through the outbox.
 *
 * <p>Implementations are records whose components form the event payload.
 */
public interface IntegrationEvent {

    String eventType();

    UUID aggregateId();
}
