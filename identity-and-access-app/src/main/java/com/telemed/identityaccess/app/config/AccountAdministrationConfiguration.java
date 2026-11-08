package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase;
import com.telemed.identityaccess.application.port.in.ChangeAccountStatusUseCase;
import com.telemed.identityaccess.application.port.in.ChangeProfessionalAccountStatusUseCase;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.application.usecase.BrowseAccountsService;
import com.telemed.identityaccess.application.usecase.ChangeAccountStatusService;
import com.telemed.identityaccess.application.usecase.ChangeProfessionalAccountStatusService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-012: the administrator's view and management of user accounts; the saga's account activation. */
@Configuration
public class AccountAdministrationConfiguration {

    @Bean
    BrowseAccountsUseCase browseAccountsUseCase(
            AccountDirectoryPort directory
    ) {
        return new BrowseAccountsService(directory);
    }

    @Bean
    ChangeAccountStatusUseCase changeAccountStatusUseCase(
            UserRepositoryPort users,
            RefreshTokenRepositoryPort refreshTokens,
            AccountDirectoryPort directory,
            PlatformTransactionManager transactionManager
    ) {
        ChangeAccountStatusUseCase delegate = new ChangeAccountStatusService(
                users,
                refreshTokens,
                directory
        );

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        return command -> transaction.execute(status -> delegate.change(command));
    }

    @Bean
    ChangeProfessionalAccountStatusUseCase changeProfessionalAccountStatusUseCase(
            UserRepositoryPort users,
            RefreshTokenRepositoryPort refreshTokens,
            PlatformTransactionManager transactionManager
    ) {
        ChangeProfessionalAccountStatusUseCase delegate =
                new ChangeProfessionalAccountStatusService(users, refreshTokens);

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        return (accountId, active) -> transaction.executeWithoutResult(status -> delegate.change(accountId, active));
    }
}
