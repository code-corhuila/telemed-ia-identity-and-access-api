package com.telemed.identityaccess.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.adapter.out.messaging.RabbitIntegrationEventPublisher;
import com.telemed.identityaccess.application.port.in.RelayOutboxEventsUseCase;
import com.telemed.identityaccess.application.port.out.IntegrationEventPublisherPort;
import com.telemed.identityaccess.application.port.out.OutboxEventsPort;
import com.telemed.identityaccess.application.usecase.RelayOutboxEventsService;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** HU-03: publishes the identity outbox to RabbitMQ (exchange identity.events, ADR-011). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxRelayProperties.class)
@ConditionalOnProperty(prefix = "identity.outbox-relay", name = "enabled", havingValue = "true")
public class OutboxRelayConfiguration {

    // Declared by its producer; consumers declare and bind their own queues.
    @Bean
    TopicExchange identityEventsExchange() {
        return new TopicExchange(RabbitIntegrationEventPublisher.EXCHANGE, true, false);
    }

    @Bean
    IntegrationEventPublisherPort integrationEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            OutboxRelayProperties properties
    ) {
        return new RabbitIntegrationEventPublisher(
                rabbitTemplate,
                objectMapper,
                properties.confirmTimeout()
        );
    }

    @Bean
    RelayOutboxEventsUseCase relayOutboxEventsUseCase(
            OutboxEventsPort outbox,
            IntegrationEventPublisherPort publisher,
            OutboxRelayProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        RelayOutboxEventsUseCase delegate = new RelayOutboxEventsService(
                outbox,
                publisher,
                properties.batchSize()
        );

        // Keeps the claimed rows locked until each one is marked.
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        return () -> transaction.execute(status -> delegate.relayPending());
    }

    @Bean
    OutboxRelayScheduler outboxRelayScheduler(RelayOutboxEventsUseCase relay) {
        return new OutboxRelayScheduler(relay);
    }
}
