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

            password = requireText(
                    password,
                    "Password is required."
            );
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