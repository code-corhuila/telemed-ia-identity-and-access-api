package com.telemed.identityaccess.application.port.in;

import java.util.UUID;

public interface ProvisionProfessionalAccountUseCase {

    Result provision(Command command);

    /**
     * active = false creates the account blocked (the onboarding saga activates it at the end).
     * A retry with the same Idempotency-Key does not compare it: the account may be active by then.
     */
    record Command(
            String idempotencyKey,
            String fullName,
            String email,
            String identityDocument,
            String password,
            boolean active
    ) {

        /** An active account, as professional management asks for it. */
        public Command(
                String idempotencyKey,
                String fullName,
                String email,
                String identityDocument,
                String password
        ) {
            this(idempotencyKey, fullName, email, identityDocument, password, true);
        }

        public Command {
            // Same rules and normalization as a patient registration, Idempotency-Key included.
            var account = new RegisterPatientUseCase.Command(
                    idempotencyKey,
                    fullName,
                    email,
                    identityDocument,
                    password
            );

            fullName = account.fullName();
            email = account.email();
            identityDocument = account.identityDocument();
        }

        @Override
        public String toString() {
            return "Command[idempotencyKey=%s, fullName=%s, email=%s, identityDocument=%s, password=***, active=%s]"
                    .formatted(
                            idempotencyKey,
                            fullName,
                            email,
                            identityDocument,
                            active
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
