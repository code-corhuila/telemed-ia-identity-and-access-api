package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.application.usecase.BrowseAccountsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** FR-012: the administrator's view and management of user accounts. */
@Configuration
public class AccountAdministrationConfiguration {

    @Bean
    BrowseAccountsUseCase browseAccountsUseCase(
            AccountDirectoryPort directory
    ) {
        return new BrowseAccountsService(directory);
    }
}
