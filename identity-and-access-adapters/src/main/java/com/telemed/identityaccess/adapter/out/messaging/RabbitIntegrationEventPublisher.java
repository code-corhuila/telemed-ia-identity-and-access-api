package com.telemed.identityaccess.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.exception.EventPublicationException;
import com.telemed.identityaccess.application.port.out.IntegrationEventPublisherPort;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes to the topic exchange identity.events, routing key = event type in
 * kebab case (PasswordRecoveryRequested -> password-recovery-requested), with the
 * ADR-011 envelope as body.
 *
 * <p>Needs a template with correlated publisher confirms, publisher returns and
 * mandatory on: an event counts as published only when a queue took it.
 */
public class RabbitIntegrationEventPublisher implements IntegrationEventPublisherPort {

    public static final String EXCHANGE = "identity.events";

    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    public RabbitIntegrationEventPublisher(
            RabbitTemplate rabbit,
            ObjectMapper objectMapper,
            Duration confirmTimeout
    ) {
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(OutboxEvent event) {

        CorrelationData confirmation = new CorrelationData(event.id().toString());

        try {
            rabbit.send(EXCHANGE, routingKey(event.eventType()), message(event), confirmation);

            CorrelationData.Confirm confirm =
                    confirmation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!confirm.isAck() || confirmation.getReturned() != null) {
                throw failure(event, null);
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(event, exception);

        } catch (AmqpException | ExecutionException | TimeoutException exception) {
            throw failure(event, exception);
        }
    }

    static String routingKey(String eventType) {
        return eventType.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }

    private Message message(OutboxEvent event) {

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", event.id().toString());
        envelope.put("eventType", event.eventType());
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("correlationId", event.correlationId());
        envelope.put("version", event.version());

        try {
            envelope.set("payload", objectMapper.readTree(event.payload()));

            return MessageBuilder.withBody(objectMapper.writeValueAsBytes(envelope))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .setMessageId(event.id().toString())
                    .setCorrelationId(event.correlationId())
                    .setType(event.eventType())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();

        } catch (JsonProcessingException exception) {
            // Not chained: the parser message may quote the payload and its resetUrl.
            throw failure(event, null);
        }
    }

    // Only the event identity: the payload may carry a resetUrl.
    private static EventPublicationException failure(OutboxEvent event, Throwable cause) {
        return new EventPublicationException(
                "Broker did not take " + event.eventType() + " " + event.id() + ".",
                cause
        );
    }
}
