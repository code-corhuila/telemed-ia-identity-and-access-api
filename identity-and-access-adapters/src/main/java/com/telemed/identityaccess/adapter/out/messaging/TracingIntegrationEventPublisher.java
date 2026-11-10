package com.telemed.identityaccess.adapter.out.messaging;

import com.telemed.identityaccess.adapter.observability.CurrentTrace;
import com.telemed.identityaccess.adapter.observability.TraceContext;
import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.exception.EventPublicationException;
import com.telemed.identityaccess.application.port.out.IntegrationEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Week 11 (CP3): publishes each outbox event inside the trace of the request that recorded it,
 * with the relay's own span, and logs the outcome. Only the event identity is logged: the
 * payload may carry a resetUrl.
 */
public class TracingIntegrationEventPublisher implements IntegrationEventPublisherPort {

    private static final Logger LOG = LoggerFactory.getLogger(TracingIntegrationEventPublisher.class);

    private final IntegrationEventPublisherPort delegate;

    public TracingIntegrationEventPublisher(IntegrationEventPublisherPort delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(OutboxEvent event) {

        Map<String, String> relayContext = MDC.getCopyOfContextMap();
        TraceContext trace = TraceContext.from(event.traceparent());
        MDC.put(CurrentTrace.TRACE_ID, trace.traceId());
        MDC.put(CurrentTrace.SPAN_ID, trace.spanId());
        if (event.correlationId() != null) {
            MDC.put(CurrentTrace.CORRELATION_ID, event.correlationId());
        }
        long started = System.nanoTime();

        try {
            delegate.publish(event);
            LOG.atInfo()
                    .addKeyValue("event", "outbox.event.published")
                    .addKeyValue("event_type", event.eventType())
                    .addKeyValue("event_id", event.id().toString())
                    .addKeyValue("duration_ms", (System.nanoTime() - started) / 1_000_000)
                    .log("outbox event published");

        } catch (EventPublicationException exception) {
            // The event stays pending and the next run retries it.
            LOG.atWarn()
                    .addKeyValue("event", "outbox.event.failed")
                    .addKeyValue("event_type", event.eventType())
                    .addKeyValue("event_id", event.id().toString())
                    .addKeyValue("error_type", exception.getClass().getName())
                    .addKeyValue("duration_ms", (System.nanoTime() - started) / 1_000_000)
                    .log("outbox event not published; it stays pending");
            throw exception;

        } finally {
            if (relayContext == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(relayContext);
            }
        }
    }
}
