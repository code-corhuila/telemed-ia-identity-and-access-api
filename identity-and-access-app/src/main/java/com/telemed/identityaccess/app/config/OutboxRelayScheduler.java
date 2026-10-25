package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.application.port.in.RelayOutboxEventsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the relay with a fixed pause; a failed run is logged and the next one retries. */
public class OutboxRelayScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final RelayOutboxEventsUseCase relay;

    public OutboxRelayScheduler(RelayOutboxEventsUseCase relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${identity.outbox-relay.interval}")
    public void relayPending() {
        try {
            int published = relay.relayPending();
            if (published > 0) {
                LOGGER.info("Outbox relay published {} event(s).", published);
            }
        } catch (RuntimeException exception) {
            // Type only: the events stay pending, and their payload must not reach the log.
            LOGGER.warn("Outbox relay run failed: {}", exception.getClass().getSimpleName());
        }
    }
}
