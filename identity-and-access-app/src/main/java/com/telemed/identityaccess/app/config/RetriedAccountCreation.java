package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.application.exception.RegistrationException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs an account creation in one transaction. A retry that raced the first request with the same
 * Idempotency-Key loses on the unique email (PostgreSQL then aborts the transaction); running it
 * once more answers that account by its key. Any other conflict fails again the same way.
 */
final class RetriedAccountCreation {

    private RetriedAccountCreation() {
    }

    static <T> T run(TransactionTemplate transaction, Supplier<T> creation) {
        try {
            return transaction.execute(status -> creation.get());

        } catch (RegistrationException | DataAccessException conflict) {
            return transaction.execute(status -> creation.get());
        }
    }
}
