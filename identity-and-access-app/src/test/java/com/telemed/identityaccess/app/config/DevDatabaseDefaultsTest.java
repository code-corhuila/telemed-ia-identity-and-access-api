package com.telemed.identityaccess.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** Anexo J: one PostgreSQL database (telemed_ia, created by infra-postgres) and the domain's own user. */
class DevDatabaseDefaultsTest {

    @Test
    void devProfileUsesTheSharedDatabaseWithTheIdentityUser()
            throws IOException {

        MockEnvironment environment = new MockEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application-dev", new ClassPathResource("application-dev.yml"))) {
            environment.getPropertySources().addLast(source);
        }

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/telemed_ia");
        assertThat(environment.getProperty("spring.datasource.username"))
                .isEqualTo("identity_app");
    }
}
