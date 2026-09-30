package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.domain.model.Role;

import java.util.Locale;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.INVALID_REGISTRATION;

public interface RegisterPatientUseCase {

    Result register(Command command);

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
                throw new RegistrationException(
                        INVALID_REGISTRATION,
                        message
                );
            }

            return value.trim();
        }

        private static String requirePassword(String value) {

            String password = requireText(
                    value,
                    "Password is required."
            );

            if (password.length() < 8) {
                throw new RegistrationException(
                        INVALID_REGISTRATION,
                        "Password must contain at least 8 characters."
                );
            }

            return password;
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

    record Result(
            UUID userId,
            Role role
    ) {
    }
}