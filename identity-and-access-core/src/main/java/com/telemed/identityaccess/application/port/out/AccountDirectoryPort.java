package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountPage;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.Query;

import java.util.Optional;
import java.util.UUID;

/** Read side of the accounts, newest registration first. */
public interface AccountDirectoryPort {

    AccountPage search(Query query);

    Optional<AccountView> findById(UUID accountId);
}
