package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.exception.AccountStatusConflictException;
import com.telemed.identityaccess.application.exception.AdministratorRequiredException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.ChangeAccountStatusUseCase;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;

import static com.telemed.identityaccess.application.exception.AccountStatusConflictException.Reason.LAST_ACTIVE_ADMIN;
import static com.telemed.identityaccess.application.exception.AccountStatusConflictException.Reason.SELF_DEACTIVATION;

/** Runs in one transaction, so the status and the revoked sessions change together. */
public class ChangeAccountStatusService implements ChangeAccountStatusUseCase {

    private final UserRepositoryPort users;
    private final RefreshTokenRepositoryPort refreshTokens;
    private final AccountDirectoryPort directory;

    public ChangeAccountStatusService(
            UserRepositoryPort users,
            RefreshTokenRepositoryPort refreshTokens,
            AccountDirectoryPort directory
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.directory = directory;
    }

    @Override
    public AccountView change(Command command) {

        // An access token outlives a blocked account by up to its TTL; the account decides.
        users.findById(command.requesterId())
                .filter(requester -> requester.active() && requester.role() == Role.ADMIN)
                .orElseThrow(AdministratorRequiredException::new);

        User account = users.findById(command.accountId())
                .orElseThrow(AccountNotFoundException::new);

        if (!command.active()) {
            refuseUnsafeDeactivation(command, account);
        }

        if (account.active() != command.active()) {
            users.updateActive(account.id(), command.active());

            if (!command.active()) {
                refreshTokens.revokeAllForUser(account.id());
            }
        }

        return directory.findById(account.id())
                .orElseThrow(AccountNotFoundException::new);
    }

    private void refuseUnsafeDeactivation(Command command, User account) {

        if (account.id().equals(command.requesterId())) {
            throw new AccountStatusConflictException(
                    SELF_DEACTIVATION,
                    "Administrators cannot deactivate their own account."
            );
        }

        // Locks the active admins, so two admins blocking each other cannot both succeed.
        if (account.role() == Role.ADMIN
                && account.active()
                && users.countActiveByRoleLocking(Role.ADMIN) <= 1) {
            throw new AccountStatusConflictException(
                    LAST_ACTIVE_ADMIN,
                    "The last active administrator cannot be deactivated."
            );
        }
    }
}
