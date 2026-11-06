package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.application.exception.AccountProvisioningException;
import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.domain.model.PasswordPolicy;
import com.telemed.identityaccess.domain.model.Role;

import java.util.Locale;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccountProvisioningException.Reason.INVALID_IDEMPOTENCY_KEY;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.INVALID_REGISTRATION;

public interface RegisterPatientUseCase {

    Result register(Command command);

    /** idempotencyKey: the Idempotency-Key of the request (07-api/guidelines.md), 8 to 128 characters. */
    record Command(
            String idempotencyKey,
            String fullName,
            String email,
            String identityDocument,
            String password
    ) {

        public Command {
            idempotencyKey = requireIdempotencyKey(idempotencyKey);

            fullName = requireText(
                    fullName,
                    "Full name is required."
            );

            email = requireText(
                    email,
                    "Email is required."
            ).toLowerCase(Locale.ROOT);

            identityDocument = requireText(
                    identityDocument,
                    "Identity document is required."
            );

            password = requirePassword(password);
        }

        private static String requireText(
                String value,
                String message
        ) {
            if (value == null || value.isBlank()) {
                throw new RegistrationException(
                        INVALID_REGISTRATION,
                        message
                );
            }

            return value.trim();
        }

        private static String requirePassword(String value) {

            if (value == null || value.isBlank()) {
                throw new RegistrationException(
                        INVALID_REGISTRATION,
                        "Password is required."
                );
            }

            if (PasswordPolicy.isTooShort(value)) {
                throw new RegistrationException(
                        INVALID_REGISTRATION,
                        "Password must contain at least 8 characters."
                );
            }

            if (PasswordPolicy.isTooLong(value)) {
                throw new RegistrationException(
                        INVALID_REGISTRATION,
                        "Password must not exceed 72 UTF-8 bytes."
                );
            }

            return value;
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

    /** replayed: the key was already used for this same registration, so nothing new was created. */
    record Result(
            UUID userId,
            Role role,
            boolean replayed
    ) {
    }
}