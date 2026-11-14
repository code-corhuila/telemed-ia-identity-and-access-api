package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.ChangeProfessionalAccountStatusUseCase;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;

import java.util.UUID;

/** Runs in one transaction, so the status and the revoked sessions change together. */
public class ChangeProfessionalAccountStatusService implements ChangeProfessionalAccountStatusUseCase {

    private final UserRepositoryPort users;
    private final RefreshTokenRepositoryPort refreshTokens;

    public ChangeProfessionalAccountStatusService(
            UserRepositoryPort users,
            RefreshTokenRepositoryPort refreshTokens
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @Override
    public void change(UUID accountId, boolean active) {

        User account = users.findById(accountId)
                .filter(user -> user.role() == Role.PROFESSIONAL)
                .orElseThrow(AccountNotFoundException::new);

        if (account.active() == active) {
            return;
        }

        users.updateActive(account.id(), active);

        if (!active) {
            refreshTokens.revokeAllForUser(account.id());
        }
    }
}
