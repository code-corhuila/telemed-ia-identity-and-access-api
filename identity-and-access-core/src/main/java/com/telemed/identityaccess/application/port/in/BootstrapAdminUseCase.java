package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.domain.model.PasswordPolicy;

import java.util.Locale;

public interface BootstrapAdminUseCase {

    Outcome bootstrap(Command command);

    enum Outcome {
        CREATED,
        ALREADY_PRESENT,
        /** The email or the identity document belongs to another account, which is never promoted. */
        ACCOUNT_IN_USE
    }

    /** Validated like a registration; an invalid value is a configuration error. */
    record Command(
            String fullName,
            String email,
            String identityDocument,
            String password
    ) {

        public Command {
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
                throw new IllegalArgumentException(message);
            }

            return value.trim();
        }

        private static String requirePassword(String value) {

            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Password is required."
                );
            }

            if (PasswordPolicy.isTooShort(value)) {
                throw new IllegalArgumentException(
                        "Password must contain at least 8 characters."
                );
            }

            if (PasswordPolicy.isTooLong(value)) {
                throw new IllegalArgumentException(
                        "Password must not exceed 72 UTF-8 bytes."
                );
            }

            return value;
        }

        @Override
        public String toString() {
            return "Command[fullName=%s, email=%s, identityDocument=%s, password=***]"
                    .formatted(
                            fullName,
                            email,
                            identityDocument
                    );
        }
    }
}
