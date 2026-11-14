package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountProvisioningException;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;

import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccountProvisioningException.Reason.IDEMPOTENCY_KEY_REUSED;

/**
 * An account creation whose Idempotency-Key was already used: it is a retry only when the key's
 * account is the one this same request would have created.
 */
final class AccountCreationRetry {

    private AccountCreationRetry() {
    }

    static void requireSameRequest(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            UUID userId,
            Role role,
            String fullName,
            String email,
            String identityDocument,
            String password
    ) {

        User account = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency-Key points to a missing account."
                ));

        boolean sameRequest = account.role() == role
                && account.fullName().equals(fullName)
                && account.email().equalsIgnoreCase(email)
                && account.identityDocument().equals(identityDocument)
                && passwordHasher.matches(password, account.passwordHash());

        if (!sameRequest) {
            throw new AccountProvisioningException(
                    IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used for a different request."
            );
        }
    }
}
