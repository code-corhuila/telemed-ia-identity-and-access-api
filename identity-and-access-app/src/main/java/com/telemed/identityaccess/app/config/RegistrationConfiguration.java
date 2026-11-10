package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.application.port.in.RegisterPatientUseCase;
import com.telemed.identityaccess.application.port.out.IdempotencyKeyRepositoryPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.application.usecase.RegisterPatientService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class RegistrationConfiguration {

    @Bean
    RegisterPatientUseCase registerPatientUseCase(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            IdempotencyKeyRepositoryPort idempotencyKeys,
            PlatformTransactionManager transactionManager
    ) {
        RegisterPatientUseCase delegate = new RegisterPatientService(
                users,
                passwordHasher,
                idempotencyKeys
        );

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        return command -> RetriedAccountCreation.run(transaction, () -> delegate.register(command));
    }
}
