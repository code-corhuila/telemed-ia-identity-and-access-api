package com.telemed.identityaccess.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapAdminDefaultsTest {

    @Test
    void readsTheBootstrapAdminFromItsEnvironmentVariables()
            throws IOException {

        MockEnvironment environment = environment()
                .withProperty("BOOTSTRAP_ADMIN_FULL_NAME", "Platform Admin")
                .withProperty("BOOTSTRAP_ADMIN_EMAIL", "admin@telemed.test")
                .withProperty("BOOTSTRAP_ADMIN_IDENTITY_DOCUMENT", "ADM-0001")
                .withProperty("BOOTSTRAP_ADMIN_PASSWORD", "Admin-Secret-1");

        assertThat(environment.getProperty("security.bootstrap-admin.full-name"))
                .isEqualTo("Platform Admin");
        assertThat(environment.getProperty("security.bootstrap-admin.email"))
                .isEqualTo("admin@telemed.test");
        assertThat(environment.getProperty("security.bootstrap-admin.identity-document"))
                .isEqualTo("ADM-0001");
        assertThat(environment.getProperty("security.bootstrap-admin.password"))
                .isEqualTo("Admin-Secret-1");
    }

    @Test
    void createsNoAdminByDefault() throws IOException {

        MockEnvironment environment = environment();

        assertThat(environment.getProperty("security.bootstrap-admin.email"))
                .isEmpty();
        assertThat(environment.getProperty("security.bootstrap-admin.password"))
                .isEmpty();
    }

    private static MockEnvironment environment() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
