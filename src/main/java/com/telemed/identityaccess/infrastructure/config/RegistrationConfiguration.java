package com.telemed.identityaccess.infrastructure.config;

import com.telemed.identityaccess.application.port.in.RegisterPatientUseCase;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.application.usecase.RegisterPatientService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationConfiguration {

    @Bean
    RegisterPatientUseCase registerPatientUseCase(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher
    ) {
        return new RegisterPatientService(
                users,
                passwordHasher
        );
    }
}