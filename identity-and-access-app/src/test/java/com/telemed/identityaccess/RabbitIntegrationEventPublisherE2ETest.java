package com.telemed.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.adapter.out.messaging.RabbitIntegrationEventPublisher;
import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.exception.EventPublicationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-03: identity publishes its outbox events to RabbitMQ and waits for the broker. */
class RabbitIntegrationEventPublisherE2ETest {

    private static final String QUEUE = "e2e.password-recovery-requested";

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.12-alpine");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbit;
    private static RabbitAdmin admin;

    @BeforeAll
    static void startBroker() {
        RABBITMQ.start();

        connectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        connectionFactory.setUsername(RABBITMQ.getAdminUsername());
        connectionFactory.setPassword(RABBITMQ.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        rabbit = new RabbitTemplate(connectionFactory);
        rabbit.setMandatory(true);

        admin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(RabbitIntegrationEventPublisher.EXCHANGE);
        admin.declareExchange(exchange);
        Queue queue = new Queue(QUEUE);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("password-recovery-requested"));
    }

    @AfterAll
    static void stopBroker() {
        connectionFactory.destroy();
        RABBITMQ.stop();
    }

    @Test
    void shouldPublishTheAdr011EnvelopeWithTheEventIdentity() throws Exception {

        OutboxEvent event = event("PasswordRecoveryRequested", "trace-123");

        publisher(rabbit).publish(event);

        Message message = rabbit.receive(QUEUE, 5_000);
        assertThat(message).isNotNull();

        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.id().toString());
        assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("trace-123");
        assertThat(message.getMessageProperties().getType()).isEqualTo("PasswordRecoveryRequested");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getReceivedDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);

        JsonNode envelope = OBJECT_MAPPER.readTree(message.getBody());
        assertThat(envelope.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "eventId", "eventType", "occurredAt", "correlationId", "version", "payload");
        assertThat(envelope.get("eventId").asText()).isEqualTo(event.id().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo("PasswordRecoveryRequested");
        assertThat(envelope.get("occurredAt").asText()).isEqualTo("2026-10-25T15:00:00Z");
        assertThat(envelope.get("correlationId").asText()).isEqualTo("trace-123");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("payload").get("email").asText()).isEqualTo("patient@example.com");
        assertThat(envelope.get("payload").get("resetUrl").asText())
                .isEqualTo("https://portal.e2e.test/reset-password#token=raw-token");
    }

    @Test
    void shouldPublishAnEventRecordedOutsideARequestWithANullCorrelationId() throws Exception {

        publisher(rabbit).publish(event("PasswordRecoveryRequested", null));

        Message message = rabbit.receive(QUEUE, 5_000);
        assertThat(message).isNotNull();
        assertThat(OBJECT_MAPPER.readTree(message.getBody()).get("correlationId").isNull()).isTrue();
    }

    @Test
    void shouldKeepTheEventPendingWhenNoQueueTakesIt() {

        // No queue is bound to this routing key, so the broker returns the message.
        assertThatThrownBy(() -> publisher(rabbit).publish(event("PatientRegistered", null)))
                .isInstanceOf(EventPublicationException.class);
    }

    @Test
    void shouldKeepTheEventPendingWhenTheBrokerIsUnreachable() {

        CachingConnectionFactory unreachable = new CachingConnectionFactory("localhost", 1);
        unreachable.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        unreachable.setPublisherReturns(true);
        RabbitTemplate template = new RabbitTemplate(unreachable);
        template.setMandatory(true);

        try {
            assertThatThrownBy(() -> publisher(template).publish(event("PasswordRecoveryRequested", null)))
                    .isInstanceOf(EventPublicationException.class)
                    .message().doesNotContain("raw-token");
        } finally {
            unreachable.destroy();
        }
    }

    private static RabbitIntegrationEventPublisher publisher(RabbitTemplate template) {
        return new RabbitIntegrationEventPublisher(template, OBJECT_MAPPER, CONFIRM_TIMEOUT);
    }

    private static OutboxEvent event(String eventType, String correlationId) {
        return new OutboxEvent(
                UUID.randomUUID(),
                eventType,
                UUID.randomUUID(),
                "{\"email\":\"patient@example.com\","
                        + "\"resetUrl\":\"https://portal.e2e.test/reset-password#token=raw-token\"}",
                correlationId,
                1,
                Instant.parse("2026-10-25T15:00:00Z")
        );
    }
}
