package com.telemed.identityaccess.application.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {

    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-25T15:00:00Z");

    @Test
    void shouldKeepThePayloadOutOfLogs() {

        OutboxEvent event = new OutboxEvent(EVENT_ID, "PasswordRecoveryRequested", USER_ID,
                "{\"resetUrl\":\"https://portal.test/reset-password#token=secret\"}", "trace-123", null, 1, OCCURRED_AT);

        assertThat(event.toString())
                .contains(EVENT_ID.toString(), "PasswordRecoveryRequested")
                .doesNotContain("secret", "resetUrl");
    }

    @Test
    void shouldAllowAnEventRecordedOutsideARequest() {

        OutboxEvent event = new OutboxEvent(EVENT_ID, "PasswordRecoveryRequested", USER_ID,
                "{}", null, null, 1, OCCURRED_AT);

        assertThat(event.correlationId()).isNull();
        assertThat(event.traceparent()).isNull();
    }

    @Test
    void shouldCarryTheTraceOfTheRequestThatRecordedIt() {

        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        OutboxEvent event = new OutboxEvent(EVENT_ID, "PasswordRecoveryRequested", USER_ID,
                "{}", "trace-123", traceparent, 1, OCCURRED_AT);

        assertThat(event.traceparent()).isEqualTo(traceparent);
        assertThat(event.toString()).contains(traceparent);
    }

    @Test
    void shouldRequireWhatTheEnvelopeCarries() {

        assertThatThrownBy(() -> new OutboxEvent(null, "PasswordRecoveryRequested", USER_ID,
                "{}", null, null, 1, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OutboxEvent(EVENT_ID, " ", USER_ID,
                "{}", null, null, 1, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OutboxEvent(EVENT_ID, "PasswordRecoveryRequested", USER_ID,
                null, null, null, 1, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OutboxEvent(EVENT_ID, "PasswordRecoveryRequested", USER_ID,
                "{}", null, null, 0, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
