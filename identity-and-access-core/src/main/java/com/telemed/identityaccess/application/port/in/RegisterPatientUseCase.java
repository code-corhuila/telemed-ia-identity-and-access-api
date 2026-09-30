package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.domain.model.Role;

import java.nio.charset.StandardCharsets;
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

        private static final int MIN_PASSWORD_CHARACTERS = 8;
        private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

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

            if (value == null || value.isBlank()) {
                throw new RegistrationException(
                        INVALID_REGISTRATION,
                        "Password is required."
                );
            }

            int characterCount = value.codePointCount(
                    0,
                    value.length()
            );

            if (characterCount < MIN_PASSWORD_CHARACTERS) {
                throw new RegistrationException(
                        INVALID_REGISTRATION,
                        "Password must contain at least 8 characters."
                );
            }

            int utf8ByteLength = value
                    .getBytes(StandardCharsets.UTF_8)
                    .length;

            if (utf8ByteLength > MAX_BCRYPT_PASSWORD_BYTES) {
                throw new RegistrationException(
                        INVALID_REGISTRATION,
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

    record Result(
            UUID userId,
            Role role
    ) {
    }
}