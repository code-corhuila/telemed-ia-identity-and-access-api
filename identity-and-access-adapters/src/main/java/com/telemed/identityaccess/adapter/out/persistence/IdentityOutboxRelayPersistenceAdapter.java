package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.event.OutboxEvent;
import com.telemed.identityaccess.application.port.out.OutboxEventsPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Relay side of identity_outbox_event, owned by the identity -db repository. Plain SQL
 * for the row locks; the caller's transaction keeps them until the events are marked.
 */
@Repository
public class IdentityOutboxRelayPersistenceAdapter implements OutboxEventsPort {

    // SKIP LOCKED lets two relay instances share the outbox without publishing an event twice.
    private static final String CLAIM_PENDING = """
            SELECT id, event_type, aggregate_id, payload::text AS payload,
                   correlation_id, event_version, occurred_at
            FROM identity_outbox_event
            WHERE published_at IS NULL
            ORDER BY occurred_at, id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    // The CHECK chk_identity_outbox_event_redacted_when_published refuses a published resetUrl.
    private static final String MARK_PUBLISHED = """
            UPDATE identity_outbox_event
            SET published_at = CURRENT_TIMESTAMP,
                payload = payload - 'resetUrl'
            WHERE id = ?
            """;

    private static final String MARK_FAILED = """
            UPDATE identity_outbox_event
            SET attempts = attempts + 1,
                last_error = ?
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    public IdentityOutboxRelayPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OutboxEvent> claimPending(int limit) {
        return jdbc.query(
                CLAIM_PENDING,
                (row, index) -> new OutboxEvent(
                        row.getObject("id", UUID.class),
                        row.getString("event_type"),
                        row.getObject("aggregate_id", UUID.class),
                        row.getString("payload"),
                        row.getString("correlation_id"),
                        row.getShort("event_version"),
                        row.getObject("occurred_at", OffsetDateTime.class).toInstant()
                ),
                limit
        );
    }

    @Override
    public void markPublished(UUID eventId) {
        jdbc.update(MARK_PUBLISHED, eventId);
    }

    @Override
    public void markFailed(UUID eventId, String errorCode) {
        jdbc.update(MARK_FAILED, errorCode, eventId);
    }
}
