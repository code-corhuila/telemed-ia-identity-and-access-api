package com.telemed.identityaccess.app.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param enabled        publish the outbox to RabbitMQ from this instance
 * @param interval       pause between two relay runs
 * @param batchSize      events claimed per run
 * @param confirmTimeout how long one event waits for the broker confirm
 */
@Validated
@ConfigurationProperties(prefix = "identity.outbox-relay")
public record OutboxRelayProperties(
        boolean enabled,
        @NotNull Duration interval,
        @Positive int batchSize,
        @NotNull Duration confirmTimeout
) {
}
