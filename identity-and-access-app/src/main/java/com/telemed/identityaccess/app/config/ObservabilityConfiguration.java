package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.app.observability.IdentityRequestMetricsFilter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

/** Week 11 metrics (CP4), scraped from the management port, which deploy publishes on 127.0.0.1 only. */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {

    // Inside the request observation (+1), after the trace context (+2) and before authentication,
    // so rejected requests (401, 403) are counted too.
    @Bean
    FilterRegistrationBean<IdentityRequestMetricsFilter> identityRequestMetrics(MeterRegistry registry) {
        FilterRegistrationBean<IdentityRequestMetricsFilter> registration =
                new FilterRegistrationBean<>(new IdentityRequestMetricsFilter(registry));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registration;
    }

    /**
     * Week 11 traces (CP3): the outbox relay runs every few seconds, mostly with nothing to publish.
     * Each run would be a trace of its own in Jaeger; only its publications are traced, under the
     * request that recorded each event (TracingIntegrationEventPublisher).
     */
    @Bean
    ObservationPredicate noScheduledTaskObservations() {
        return (name, context) -> !"tasks.scheduled.execution".equals(name);
    }

    /**
     * Saturation and freshness of the async hop: events the relay has not published yet, and how
     * long the oldest one has waited. A stopped relay shows here before any e-mail is missed.
     */
    @Bean
    MeterBinder outboxMetrics(ObjectProvider<JdbcTemplate> jdbc) {
        return registry -> jdbc.ifAvailable(template -> bindOutbox(registry, template));
    }

    private static void bindOutbox(MeterRegistry registry, JdbcTemplate jdbc) {
        Gauge.builder("identity.outbox.pending.events", () -> jdbc.queryForObject(
                        "SELECT count(*) FROM identity_outbox_event WHERE published_at IS NULL", Long.class))
                .description("Outbox events waiting to be published")
                .register(registry);
        Gauge.builder("identity.outbox.oldest.pending.age", () -> jdbc.queryForObject("""
                        SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(occurred_at)), 0)
                        FROM identity_outbox_event
                        WHERE published_at IS NULL
                        """, Double.class))
                .description("Seconds the oldest pending outbox event has waited")
                .baseUnit("seconds")
                .register(registry);
    }
}
