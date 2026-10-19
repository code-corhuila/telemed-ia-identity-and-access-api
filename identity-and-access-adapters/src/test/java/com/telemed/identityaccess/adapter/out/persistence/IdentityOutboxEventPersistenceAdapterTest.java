package com.telemed.identityaccess.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.telemed.identityaccess.adapter.in.http.CorrelationContext;
import com.telemed.identityaccess.application.event.PasswordRecoveryRequested;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityOutboxEventPersistenceAdapterTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final UUID RECOVERY_TOKEN_ID =
            UUID.fromString(
                    "22222222-2222-4222-8222-222222222222"
            );

    private final ObjectMapper objectMapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(
                            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                    );

    @Mock
    private SpringDataIdentityOutboxEventRepository repository;

    @AfterEach
    void clearCorrelation() {
        MDC.remove(CorrelationContext.MDC_KEY);
    }

    @Test
    void shouldRecordPendingEventWithContractPayloadAndCorrelation()
            throws Exception {
        MDC.put(CorrelationContext.MDC_KEY, "trace-123");

        adapter().record(event());

        ArgumentCaptor<IdentityOutboxEventJpaEntity> captor =
                ArgumentCaptor.forClass(
                        IdentityOutboxEventJpaEntity.class
                );

        verify(repository).saveAndFlush(captor.capture());

        IdentityOutboxEventJpaEntity saved =
                captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventType())
                .isEqualTo("PasswordRecoveryRequested");
        assertThat(saved.getAggregateId()).isEqualTo(USER_ID);
        assertThat(saved.getCorrelationId()).isEqualTo("trace-123");
        assertThat(saved.getPublishedAt()).isNull();

        JsonNode payload =
                objectMapper.readTree(saved.getPayload());

        assertThat(payload.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "userId",
                        "email",
                        "recoveryTokenId",
                        "expiresAt",
                        "resetUrl"
                );
        assertThat(payload.get("userId").asText())
                .isEqualTo(USER_ID.toString());
        assertThat(payload.get("recoveryTokenId").asText())
                .isEqualTo(RECOVERY_TOKEN_ID.toString());
        assertThat(payload.get("expiresAt").asText())
                .isEqualTo("2026-10-19T15:30:00Z");
    }

    @Test
    void shouldRecordEventWithoutCorrelationOutsideRequests() {
        adapter().record(event());

        ArgumentCaptor<IdentityOutboxEventJpaEntity> captor =
                ArgumentCaptor.forClass(
                        IdentityOutboxEventJpaEntity.class
                );

        verify(repository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getCorrelationId()).isNull();
    }

    @Test
    void shouldFailWhenEventCannotBePersisted() {
        when(repository.saveAndFlush(any()))
                .thenThrow(
                        new DataIntegrityViolationException("boom")
                );

        assertThatThrownBy(() -> adapter().record(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Integration event could not be recorded.");
    }

    private IdentityOutboxEventPersistenceAdapter adapter() {
        return new IdentityOutboxEventPersistenceAdapter(
                repository,
                objectMapper
        );
    }

    private static PasswordRecoveryRequested event() {
        return new PasswordRecoveryRequested(
                USER_ID,
                "patient@example.com",
                RECOVERY_TOKEN_ID,
                Instant.parse("2026-10-19T15:30:00Z"),
                "https://telemed.example.com/reset-password#token=abc"
        );
    }
}
