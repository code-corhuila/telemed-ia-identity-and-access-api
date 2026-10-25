package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;

import java.util.UUID;

/** Only administrators reach it: the HTTP adapter answers 403 to every other role. */
public class BrowseAccountsService implements BrowseAccountsUseCase {

    private final AccountDirectoryPort directory;

    public BrowseAccountsService(AccountDirectoryPort directory) {
        this.directory = directory;
    }

    @Override
    public AccountPage browse(Query query) {
        return directory.search(query);
    }

    @Override
    public AccountView view(UUID accountId) {
        return directory.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);
    }
}
