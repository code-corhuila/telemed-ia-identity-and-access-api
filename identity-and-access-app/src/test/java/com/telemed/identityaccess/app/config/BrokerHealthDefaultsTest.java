package com.telemed.identityaccess.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03: events wait in the outbox while RabbitMQ is down, so identity stays in rotation. */
class BrokerHealthDefaultsTest {

    @Test
    void brokerOutageDoesNotMakeIdentityUnhealthy()
            throws IOException {

        MockEnvironment environment = new MockEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }

        assertThat(environment.getProperty("management.health.rabbit.enabled", Boolean.class))
                .isFalse();
    }
}
