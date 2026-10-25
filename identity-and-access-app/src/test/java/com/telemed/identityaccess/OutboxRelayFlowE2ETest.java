package com.telemed.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03: an event recorded in the outbox reaches a queue bound to identity.events, without operator action. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.liquibase.enabled=false",
                "security.jwt.issuer=telemed-ia-identity-and-access",
                "security.jwt.access-token-ttl=PT1H",
                "security.refresh-token.ttl=P7D",
                "identity.outbox-relay.enabled=true",
                "identity.outbox-relay.interval=PT0.2S"
        }
)
class OutboxRelayFlowE2ETest {

    private static final String QUEUE = "e2e.identity-events";

    private static final KeyPair RSA_KEY_PAIR = generateRsaKeyPair();

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_identity_relay_test")
                    .withUsername("telemed_test")
                    .withPassword("telemed_test");

    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.12-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        RABBITMQ.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("security.jwt.private-key", () -> pem("PRIVATE KEY", RSA_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("security.jwt.public-key", () -> pem("PUBLIC KEY", RSA_KEY_PAIR.getPublic().getEncoded()));
    }

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPublishAPendingEventAndDropItsResetUrl() throws Exception {

        // Plays notifications: binding fails unless identity declared its exchange.
        Queue queue = new Queue(QUEUE, false);
        rabbitAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue)
                .to(new TopicExchange("identity.events"))
                .with("password-recovery-requested");
        rabbitAdmin.declareBinding(binding);

        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO identity_outbox_event (id, event_type, aggregate_id, payload, correlation_id)
                        VALUES (?, 'PasswordRecoveryRequested', ?, CAST(? AS jsonb), 'e2e-relay-trace')
                        """,
                eventId,
                UUID.randomUUID(),
                "{\"email\":\"patient@example.com\","
                        + "\"resetUrl\":\"https://portal.e2e.test/reset-password#token=raw-token\"}");

        Message message = rabbit.receive(QUEUE, 15_000);

        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());

        JsonNode envelope = objectMapper.readTree(message.getBody());
        assertThat(envelope.get("correlationId").asText()).isEqualTo("e2e-relay-trace");
        assertThat(envelope.get("payload").get("resetUrl").asText()).endsWith("#token=raw-token");

        Map<String, Object> row = awaitPublished(eventId);
        assertThat(row.get("has_reset_url")).isEqualTo(false);
    }

    private Map<String, Object> awaitPublished(UUID eventId) throws InterruptedException {
        // The broker confirms before the relay's transaction commits.
        for (int attempt = 0; attempt < 50; attempt++) {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                            SELECT published_at, (payload -> 'resetUrl') IS NOT NULL AS has_reset_url
                            FROM identity_outbox_event
                            WHERE id = ?
                            """,
                    eventId);
            if (row.get("published_at") != null) {
                return row;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The relay did not mark the event published.");
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate RSA test keys.", exception);
        }
    }
}
