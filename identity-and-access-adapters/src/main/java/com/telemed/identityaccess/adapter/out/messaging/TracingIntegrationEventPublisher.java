package com.telemed.identityaccess.adapter.out.messaging;

import com.telemed.identityaccess.adapter.observability.CurrentTrace;
import com.telemed.identityaccess.adapter.observability.TraceContext;
import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.exception.EventPublicationException;
import com.telemed.identityaccess.application.port.out.IntegrationEventPublisherPort;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Week 11 (CP3): publishes each outbox event inside the trace of the request that recorded it,
 * with the relay's own span, and logs the outcome. With a tracer that span is exported, so Jaeger
 * shows the publication under the request. Only the event identity is logged: the payload may
 * carry a resetUrl.
 */
public class TracingIntegrationEventPublisher implements IntegrationEventPublisherPort {

    private static final Logger LOG = LoggerFactory.getLogger(TracingIntegrationEventPublisher.class);

    private final IntegrationEventPublisherPort delegate;
    // Null without a tracer: the relay's span id is then made here and only logged.
    private final Tracer tracer;

    public TracingIntegrationEventPublisher(IntegrationEventPublisherPort delegate) {
        this(delegate, null);
    }

    public TracingIntegrationEventPublisher(IntegrationEventPublisherPort delegate, Tracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public void publish(OutboxEvent event) {

        Map<String, String> relayContext = MDC.getCopyOfContextMap();
        Span span = startSpan(event);
        long started = System.nanoTime();

        try (Tracer.SpanInScope scope = span == null ? null : tracer.withSpan(span)) {
            TraceContext trace = span == null
                    ? TraceContext.from(event.traceparent())
                    : new TraceContext(span.context().traceId(), span.context().spanId());
            MDC.put(CurrentTrace.TRACE_ID, trace.traceId());
            MDC.put(CurrentTrace.SPAN_ID, trace.spanId());
            if (event.correlationId() != null) {
                MDC.put(CurrentTrace.CORRELATION_ID, event.correlationId());
            }

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
            if (span != null) {
                span.error(exception);
            }
            throw exception;

        } finally {
            if (span != null) {
                span.end();
            }
            if (relayContext == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(relayContext);
            }
        }
    }

    /** A producer span under the span of the request that recorded the event, or a new trace. */
    private Span startSpan(OutboxEvent event) {
        if (tracer == null) {
            return null;
        }
        Span.Builder span = tracer.spanBuilder()
                .name("identity.events publish")
                .kind(Span.Kind.PRODUCER)
                .tag("messaging.destination.name", "identity.events")
                .tag("event_type", event.eventType());
        TraceContext request = TraceContext.parse(event.traceparent());
        if (request == null) {
            span.setNoParent();
        } else {
            span.setParent(tracer.traceContextBuilder()
                    .traceId(request.traceId())
                    .spanId(request.spanId())
                    .sampled(true)
                    .build());
        }
        return span.start();
    }
}
