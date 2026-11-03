package com.telemed.identityaccess.adapter.out.messaging;

import com.telemed.identityaccess.adapter.CapturedLogs;
import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.exception.EventPublicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Week 11 (CP3): the relay publishes each event inside the trace of the request that recorded it. */
class TracingIntegrationEventPublisherTest {

    private static final String REQUEST_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String REQUEST_SPAN = "00f067aa0ba902b7";

    private final Map<String, String> seenByThePublisher = new HashMap<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void continuesTheTraceOfTheRequestWithItsOwnSpan() {
        TracingIntegrationEventPublisher publisher =
                new TracingIntegrationEventPublisher(event -> seenByThePublisher.putAll(MDC.getCopyOfContextMap()));

        publisher.publish(event("00-" + REQUEST_TRACE + "-" + REQUEST_SPAN + "-01"));

        assertThat(seenByThePublisher)
                .containsEntry("trace_id", REQUEST_TRACE)
                .containsEntry("correlation_id", "corr-0001");
        assertThat(seenByThePublisher.get("span_id")).matches("[0-9a-f]{16}").isNotEqualTo(REQUEST_SPAN);
    }

    @Test
    void startsANewTraceForAnEventRecordedOutsideARequest() {
        TracingIntegrationEventPublisher publisher =
                new TracingIntegrationEventPublisher(event -> seenByThePublisher.putAll(MDC.getCopyOfContextMap()));

        publisher.publish(event(null));

        assertThat(seenByThePublisher.get("trace_id")).matches("[0-9a-f]{32}");
    }

    @Test
    void logsThePublishedEventWithoutItsPayload() {
        OutboxEvent event = event("00-" + REQUEST_TRACE + "-" + REQUEST_SPAN + "-01");

        try (CapturedLogs logs = CapturedLogs.of(TracingIntegrationEventPublisher.class)) {
            new TracingIntegrationEventPublisher(published -> { }).publish(event);

            assertThat(logs.events("outbox.event.published")).singleElement().satisfies(line -> {
                assertThat(line).containsEntry("level", "INFO")
                        .containsEntry("event_type", "PasswordRecoveryRequested")
                        .containsEntry("event_id", event.id().toString())
                        .containsKey("duration_ms");
            });
            assertThat(logs.everything()).doesNotContain("patient@example.com", "raw-token");
        }
    }

    @Test
    void logsAFailedPublicationAndLeavesTheEventPending() {
        EventPublicationException failure = new EventPublicationException("Broker did not take it.", null);

        try (CapturedLogs logs = CapturedLogs.of(TracingIntegrationEventPublisher.class)) {
            assertThatThrownBy(() -> new TracingIntegrationEventPublisher(published -> {
                throw failure;
            }).publish(event(null))).isSameAs(failure);

            assertThat(logs.events("outbox.event.failed")).singleElement().satisfies(line ->
                    assertThat(line).containsEntry("level", "WARN")
                            .containsEntry("event_type", "PasswordRecoveryRequested")
                            .containsEntry("error_type", EventPublicationException.class.getName()));
        }
    }

    @Test
    void givesBackTheContextOfTheThreadThatRanTheRelay() {
        MDC.put("trace_id", "relay-run");

        new TracingIntegrationEventPublisher(published -> { }).publish(event(null));

        assertThat(MDC.get("trace_id")).isEqualTo("relay-run");
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("correlation_id")).isNull();
    }

    private static OutboxEvent event(String traceparent) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "PasswordRecoveryRequested",
                UUID.randomUUID(),
                "{\"email\":\"patient@example.com\","
                        + "\"resetUrl\":\"https://portal.test/reset-password#token=raw-token\"}",
                "corr-0001",
                traceparent,
                1,
                Instant.parse("2026-10-25T15:00:00Z")
        );
    }
}
