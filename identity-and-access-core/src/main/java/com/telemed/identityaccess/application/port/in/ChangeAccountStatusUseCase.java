package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;

import java.util.UUID;

/**
 * FR-012: block (active = false) or restore the access of an account. A blocked account
 * cannot sign in or refresh its session; this is not the professional profile status.
 */
public interface ChangeAccountStatusUseCase {

    AccountView change(Command command);

    /** requesterId is the administrator who asks, taken from their access token. */
    record Command(
            UUID requesterId,
            UUID accountId,
            boolean active
    ) {
    }
}
