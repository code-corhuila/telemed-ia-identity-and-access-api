package com.telemed.identityaccess.application.usecase;

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
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
