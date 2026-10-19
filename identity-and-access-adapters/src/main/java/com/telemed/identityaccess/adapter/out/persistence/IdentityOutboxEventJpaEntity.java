package com.telemed.identityaccess.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "identity_outbox_event")
public class IdentityOutboxEventJpaEntity {

    private static final short CURRENT_EVENT_VERSION = 1;

    @Id
    private UUID id;

    @Column(
            name = "event_type",
            nullable = false,
            length = 100
    )
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "event_version", nullable = false)
    private short eventVersion;

    @Column(
            name = "occurred_at",
            insertable = false,
            updatable = false
    )
    private OffsetDateTime occurredAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected IdentityOutboxEventJpaEntity() {
    }

    private IdentityOutboxEventJpaEntity(
            UUID id,
            String eventType,
            UUID aggregateId,
            String payload,
            String correlationId
    ) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.correlationId = correlationId;
        this.eventVersion = CURRENT_EVENT_VERSION;
        this.attempts = 0;
    }

    public static IdentityOutboxEventJpaEntity pending(
            String eventType,
            UUID aggregateId,
            String payload,
            String correlationId
    ) {
        return new IdentityOutboxEventJpaEntity(
                UUID.randomUUID(),
                eventType,
                aggregateId,
                payload,
                correlationId
        );
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public short getEventVersion() {
        return eventVersion;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
