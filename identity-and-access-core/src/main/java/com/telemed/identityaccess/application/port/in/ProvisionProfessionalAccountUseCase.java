package com.telemed.identityaccess.application.port.in;

import java.util.UUID;

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
