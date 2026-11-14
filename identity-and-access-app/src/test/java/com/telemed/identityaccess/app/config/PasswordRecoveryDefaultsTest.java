package com.telemed.identityaccess.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRecoveryDefaultsTest {

    @Test
    void defaultResetUrlOpensTheIdentityPortalPageMountedByTheShell()
            throws IOException {

        MockEnvironment environment = new MockEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }

        assertThat(environment.getProperty(
                "security.password-recovery.reset-url"
        )).isEqualTo("http://localhost:4200/auth/reset-password");
    }
}
