package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.FindNotificationRecipientUseCase;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.domain.model.Role;

import java.util.UUID;

public class FindNotificationRecipientService implements FindNotificationRecipientUseCase {

    private final AccountDirectoryPort directory;

    public FindNotificationRecipientService(AccountDirectoryPort directory) {
        this.directory = directory;
    }

    @Override
    public NotificationRecipient find(UUID accountId) {

        return directory.findById(accountId)
                .filter(account -> account.role() == Role.PATIENT && account.active())
                .map(account -> new NotificationRecipient(account.id(), account.email()))
                .orElseThrow(AccountNotFoundException::new);
    }
}
