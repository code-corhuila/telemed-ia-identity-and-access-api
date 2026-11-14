package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

public interface LoginUseCase {

    Result login(Command command);

    record Command(
            String email,
            String password
    ) {

        public Command {
            email = normalizeEmail(email);
            requireText(email, "Email is required.");
            requireText(password, "Password is required.");
        }

        private static String normalizeEmail(String value) {
            return value == null
                    ? null
                    : value.trim();
        }

        private static void requireText(
                String value,
                String message
        ) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    record Result(
            UUID userId,
            Role role,
            String accessToken,
            Instant expiresAt,
            String refreshToken,
            Instant refreshExpiresAt
    ) {
    }
}