package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNamesNotAllowedException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.domain.model.Role;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LookupAccountNamesService implements LookupAccountNamesUseCase {

    private final AccountDirectoryPort directory;

    public LookupAccountNamesService(AccountDirectoryPort directory) {
        this.directory = directory;
    }

    @Override
    public List<AccountName> lookup(Role callerRole, Set<UUID> accountIds) {
        if (callerRole != Role.PROFESSIONAL && callerRole != Role.ADMIN) {
            throw new AccountNamesNotAllowedException();
        }
        if (accountIds.isEmpty() || accountIds.size() > MAX_IDS) {
            throw new IllegalArgumentException("ids must contain 1 to 50 account ids.");
        }

        List<AccountView> accounts = callerRole == Role.ADMIN
                ? directory.findByIds(accountIds)
                : directory.findByIdsAndRole(accountIds, Role.PATIENT);

        return accounts.stream()
                .map(account -> new AccountName(account.id(), account.fullName()))
                .toList();
    }
}
