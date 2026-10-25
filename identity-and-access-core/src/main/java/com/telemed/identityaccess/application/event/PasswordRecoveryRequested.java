package com.telemed.identityaccess.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Asks the notifications context to send the password recovery e-mail.
 *
 * <p>{@code resetUrl} carries the raw one-time token: the outbox relay must
 * strip it once the event is published.
 */
public record PasswordRecoveryRequested(
        UUID userId,
        String email,
        UUID recoveryTokenId,
        Instant expiresAt,
        String resetUrl
) implements IntegrationEvent {

    public static final String EVENT_TYPE =
            "PasswordRecoveryRequested";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public UUID aggregateId() {
        return userId;
    }
}
