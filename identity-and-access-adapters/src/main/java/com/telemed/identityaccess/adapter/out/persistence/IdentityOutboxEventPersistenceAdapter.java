package com.telemed.identityaccess.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.adapter.in.http.CorrelationContext;
import com.telemed.identityaccess.application.event.IntegrationEvent;
import com.telemed.identityaccess.application.port.out.IntegrationEventOutboxPort;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityOutboxEventPersistenceAdapter
        implements IntegrationEventOutboxPort {

    private final SpringDataIdentityOutboxEventRepository events;
    private final ObjectMapper objectMapper;

    public IdentityOutboxEventPersistenceAdapter(
            SpringDataIdentityOutboxEventRepository events,
            ObjectMapper objectMapper
    ) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(
            IntegrationEvent event
    ) {
        try {
            // The record components are the payload; eventType and
            // aggregateId are not bean getters, so Jackson leaves them out.
            String payload =
                    objectMapper.writeValueAsString(event);

            events.saveAndFlush(
                    IdentityOutboxEventJpaEntity.pending(
                            event.eventType(),
                            event.aggregateId(),
                            payload,
                            MDC.get(CorrelationContext.MDC_KEY)
                    )
            );
        } catch (JsonProcessingException | DataAccessException exception) {
            throw new IllegalStateException(
                    "Integration event could not be recorded.",
                    exception
            );
        }
    }
}
