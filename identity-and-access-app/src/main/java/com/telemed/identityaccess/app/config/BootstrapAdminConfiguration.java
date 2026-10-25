package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.application.port.in.BootstrapAdminUseCase;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.application.usecase.BootstrapAdminService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Public registration only creates patients, so the first ADMIN comes from the environment. */
@Configuration
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminConfiguration {

    @Bean
    BootstrapAdminUseCase bootstrapAdminUseCase(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher
    ) {
        return new BootstrapAdminService(
                users,
                passwordHasher
        );
    }

    @Bean
    BootstrapAdminRunner bootstrapAdminRunner(
            BootstrapAdminProperties properties,
            BootstrapAdminUseCase bootstrapAdminUseCase
    ) {
        return new BootstrapAdminRunner(
                properties,
                bootstrapAdminUseCase
        );
    }
}
