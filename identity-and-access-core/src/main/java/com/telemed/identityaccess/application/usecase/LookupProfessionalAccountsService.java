package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.port.in.LookupProfessionalAccountsUseCase;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.domain.model.Role;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LookupProfessionalAccountsService implements LookupProfessionalAccountsUseCase {

    private final AccountDirectoryPort directory;

    public LookupProfessionalAccountsService(AccountDirectoryPort directory) {
        this.directory = directory;
    }

    @Override
    public List<ProfessionalAccount> lookup(Set<UUID> accountIds) {

        if (accountIds.isEmpty() || accountIds.size() > MAX_IDS) {
            throw new IllegalArgumentException("ids must contain 1 to 100 account ids.");
        }

        return directory.findByIdsAndRole(accountIds, Role.PROFESSIONAL).stream()
                .map(account -> new ProfessionalAccount(account.id(), account.fullName(), account.email()))
                .toList();
    }
}
