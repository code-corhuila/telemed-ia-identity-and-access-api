package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.adapter.out.security.JwtProperties;
import com.telemed.identityaccess.adapter.out.security.JwtServiceTokenVerifier;
import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.LookupProfessionalAccountsUseCase;
import com.telemed.identityaccess.application.port.in.ProvisionProfessionalAccountUseCase;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.application.port.out.IdempotencyKeyRepositoryPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.application.usecase.LookupProfessionalAccountsService;
import com.telemed.identityaccess.application.usecase.ProvisionProfessionalAccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** /internal/accounts: professional management creates professional accounts and reads their names. */
@Configuration
public class AccountProvisioningConfiguration {

    @Bean
    ServiceTokenVerifierPort serviceTokenVerifier(
            JwtProperties properties
    ) {
        return new JwtServiceTokenVerifier(properties);
    }

    @Bean
    LookupProfessionalAccountsUseCase lookupProfessionalAccountsUseCase(
            AccountDirectoryPort directory
    ) {
        return new LookupProfessionalAccountsService(directory);
    }

    @Bean
    ProvisionProfessionalAccountUseCase provisionProfessionalAccountUseCase(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            IdempotencyKeyRepositoryPort idempotencyKeys,
            PlatformTransactionManager transactionManager
    ) {
        ProvisionProfessionalAccountUseCase delegate = new ProvisionProfessionalAccountService(
                users,
                passwordHasher,
                idempotencyKeys
        );

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        return command -> {
            try {
                return transaction.execute(status -> delegate.provision(command));

            } catch (RegistrationException | DataAccessException conflict) {
                // A retry that raced the first request loses on the unique email (PostgreSQL
                // then aborts the transaction); running again answers that account by its key.
                return transaction.execute(status -> delegate.provision(command));
            }
        };
    }
}
