package com.telemed.identityaccess;

import com.telemed.identityaccess.adapter.out.persistence.IdentityOutboxRelayPersistenceAdapter;
import com.telemed.identityaccess.application.event.OutboxEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03: the relay side of identity_outbox_event against the official identity-db schema. */
class OutboxRelayPersistenceE2ETest {

    private static final String RESET_URL = "https://portal.e2e.test/reset-password#token=raw-token";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_identity_outbox_test")
                    .withUsername("telemed_test")
                    .withPassword("telemed_test");

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private static IdentityOutboxRelayPersistenceAdapter outbox;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        outbox = new IdentityOutboxRelayPersistenceAdapter(jdbc);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void emptyOutbox() {
        // Isolated Testcontainers database: rows only come from these tests.
        jdbc.update("DELETE FROM identity_outbox_event");
    }

    @Test
    void shouldClaimPendingEventsOldestFirstWithWhatTheEnvelopeNeeds() {

        UUID newer = insertPending(Instant.parse("2026-10-25T15:05:00Z"), "trace-newer");
        UUID older = insertPending(Instant.parse("2026-10-25T15:00:00Z"), "trace-older");
        UUID published = insertPending(Instant.parse("2026-10-25T14:00:00Z"), null);
        jdbc.update("UPDATE identity_outbox_event SET published_at = now(), payload = payload - 'resetUrl' WHERE id = ?",
                published);

        List<OutboxEvent> claimed = claim(10);

        assertThat(claimed).extracting(OutboxEvent::id).containsExactly(older, newer);

        OutboxEvent first = claimed.get(0);
        assertThat(first.eventType()).isEqualTo("PasswordRecoveryRequested");
        assertThat(first.correlationId()).isEqualTo("trace-older");
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.occurredAt()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(first.payload()).contains("\"resetUrl\"", "raw-token");
    }

    @Test
    void shouldClaimNoMoreThanTheLimit() {

        insertPending(Instant.parse("2026-10-25T15:00:00Z"), null);
        insertPending(Instant.parse("2026-10-25T15:01:00Z"), null);
        insertPending(Instant.parse("2026-10-25T15:02:00Z"), null);

        assertThat(claim(2)).hasSize(2);
    }

    @Test
    void shouldDropTheResetUrlWhenMarkingPublished() {

        UUID eventId = insertPending(Instant.parse("2026-10-25T15:00:00Z"), null);

        transaction.executeWithoutResult(status -> outbox.markPublished(eventId));

        // payload -> 'resetUrl' instead of the ? operator, which JDBC reads as a parameter.
        Map<String, Object> row = jdbc.queryForMap("""
                        SELECT published_at,
                               payload ->> 'email' AS email,
                               (payload -> 'resetUrl') IS NOT NULL AS has_reset_url
                        FROM identity_outbox_event
                        WHERE id = ?
                        """,
                eventId);

        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("email")).isEqualTo("patient@example.com");
        assertThat(row.get("has_reset_url")).isEqualTo(false);
        assertThat(claim(10)).isEmpty();
    }

    @Test
    void shouldKeepAFailedEventPendingAndCountTheAttempt() {

        UUID eventId = insertPending(Instant.parse("2026-10-25T15:00:00Z"), null);

        transaction.executeWithoutResult(status -> outbox.markFailed(eventId, "BROKER_PUBLICATION_FAILED"));
        transaction.executeWithoutResult(status -> outbox.markFailed(eventId, "BROKER_PUBLICATION_FAILED"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT attempts, last_error, published_at FROM identity_outbox_event WHERE id = ?", eventId);

        assertThat(row.get("attempts")).isEqualTo(2);
        assertThat(row.get("last_error")).isEqualTo("BROKER_PUBLICATION_FAILED");
        assertThat(row.get("published_at")).isNull();
        assertThat(claim(10))
                .extracting(OutboxEvent::id).containsExactly(eventId);
    }

    @Test
    void shouldSkipEventsAnotherRelayHolds() throws Exception {

        UUID first = insertPending(Instant.parse("2026-10-25T15:00:00Z"), null);
        UUID second = insertPending(Instant.parse("2026-10-25T15:01:00Z"), null);

        CountDownLatch claimedByOther = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<List<OutboxEvent>> otherRelay = CompletableFuture.supplyAsync(() ->
                transaction.execute(status -> {
                    List<OutboxEvent> claimed = outbox.claimPending(1);
                    claimedByOther.countDown();
                    await(release);
                    return claimed;
                }));

        assertThat(claimedByOther.await(10, TimeUnit.SECONDS)).isTrue();

        List<OutboxEvent> claimedHere = claim(10);
        release.countDown();

        assertThat(otherRelay.get(10, TimeUnit.SECONDS)).extracting(OutboxEvent::id).containsExactly(first);
        assertThat(claimedHere).extracting(OutboxEvent::id).containsExactly(second);
    }

    private static List<OutboxEvent> claim(int limit) {
        return transaction.execute(status -> outbox.claimPending(limit));
    }

    private static UUID insertPending(Instant occurredAt, String correlationId) {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO identity_outbox_event
                            (id, event_type, aggregate_id, payload, correlation_id, occurred_at)
                        VALUES (?, 'PasswordRecoveryRequested', ?, CAST(? AS jsonb), ?, ?)
                        """,
                eventId,
                UUID.randomUUID(),
                "{\"email\":\"patient@example.com\",\"resetUrl\":\"" + RESET_URL + "\"}",
                correlationId,
                OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
        return eventId;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
