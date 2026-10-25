package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.application.exception.AccountProvisioningException;

import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccountProvisioningException.Reason.INVALID_IDEMPOTENCY_KEY;

public interface ProvisionProfessionalAccountUseCase {

    Result provision(Command command);

    record Command(
            String idempotencyKey,
            String fullName,
            String email,
            String identityDocument,
            String password
    ) {

        public Command {
            idempotencyKey = requireIdempotencyKey(idempotencyKey);

            // Same rules and normalization as a patient registration.
            var account = new RegisterPatientUseCase.Command(
                    fullName,
                    email,
                    identityDocument,
                    password
            );

            fullName = account.fullName();
            email = account.email();
            identityDocument = account.identityDocument();
        }

        private static String requireIdempotencyKey(String value) {
            if (value == null
                    || value.isBlank()
                    || value.length() < 8
                    || value.length() > 128) {
                throw new AccountProvisioningException(
                        INVALID_IDEMPOTENCY_KEY,
                        "Idempotency-Key must contain 8 to 128 characters."
                );
            }

            return value;
        }

        @Override
        public String toString() {
            return "Command[idempotencyKey=%s, fullName=%s, email=%s, identityDocument=%s, password=***]"
                    .formatted(
                            idempotencyKey,
                            fullName,
                            email,
                            identityDocument
                    );
        }
    }

    /** replayed: the key was already used for this same request, so nothing new was created. */
    record Result(
            UUID userId,
            boolean replayed
    ) {
    }
}
