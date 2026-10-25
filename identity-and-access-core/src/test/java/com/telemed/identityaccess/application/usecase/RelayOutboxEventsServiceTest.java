package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.exception.EventPublicationException;
import com.telemed.identityaccess.application.port.in.RelayOutboxEventsUseCase;
import com.telemed.identityaccess.application.port.out.IntegrationEventPublisherPort;
import com.telemed.identityaccess.application.port.out.OutboxEventsPort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-03: the events recorded in the outbox reach the broker at least once. */
class RelayOutboxEventsServiceTest {

    private static final int BATCH_SIZE = 10;

    private final OutboxEventsPort outbox = mock(OutboxEventsPort.class);

    private final IntegrationEventPublisherPort publisher =
            mock(IntegrationEventPublisherPort.class);

    private final RelayOutboxEventsUseCase useCase =
            new RelayOutboxEventsService(outbox, publisher, BATCH_SIZE);

    @Test
    void shouldMarkEachEventPublishedOnlyAfterTheBrokerTookIt() {

        OutboxEvent first = event();
        OutboxEvent second = event();
        when(outbox.claimPending(BATCH_SIZE)).thenReturn(List.of(first, second));

        assertThat(useCase.relayPending()).isEqualTo(2);

        InOrder order = inOrder(publisher, outbox);
        order.verify(publisher).publish(first);
        order.verify(outbox).markPublished(first.id());
        order.verify(publisher).publish(second);
        order.verify(outbox).markPublished(second.id());
    }

    @Test
    void shouldKeepAFailedEventPendingAndRelayTheRest() {

        OutboxEvent failing = event();
        OutboxEvent next = event();
        when(outbox.claimPending(BATCH_SIZE)).thenReturn(List.of(failing, next));
        doThrow(new EventPublicationException("broker unavailable", new RuntimeException()))
                .when(publisher).publish(failing);

        assertThat(useCase.relayPending()).isEqualTo(1);

        verify(outbox, never()).markPublished(failing.id());
        verify(outbox).markFailed(failing.id(), "BROKER_PUBLICATION_FAILED");
        verify(outbox).markPublished(next.id());
    }

    @Test
    void shouldDoNothingWhenNoEventIsPending() {

        when(outbox.claimPending(anyInt())).thenReturn(List.of());

        assertThat(useCase.relayPending()).isZero();

        verify(publisher, never()).publish(any());
    }

    @Test
    void shouldRequireAPositiveBatchSize() {

        assertThatThrownBy(() -> new RelayOutboxEventsService(outbox, publisher, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OutboxEvent event() {
        return new OutboxEvent(
                UUID.randomUUID(),
                "PasswordRecoveryRequested",
                UUID.randomUUID(),
                "{\"resetUrl\":\"https://portal.test/reset-password#token=secret\"}",
                "trace-123",
                1,
                Instant.parse("2026-10-25T15:00:00Z")
        );
    }
}
