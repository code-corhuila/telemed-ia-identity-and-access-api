package com.telemed.identityaccess.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record PasswordResetToken(
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        boolean used
) {

    private static final Pattern SHA_256_HEX =
            Pattern.compile("[0-9a-f]{64}");

    public PasswordResetToken {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "Password reset token user ID is required."
            );
        }

        if (tokenHash == null
                || !SHA_256_HEX.matcher(tokenHash).matches()) {

            throw new IllegalArgumentException(
                    "Password reset token hash must be a valid SHA-256 hexadecimal value."
            );
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "Password reset token expiration is required."
            );
        }
    }

    public static PasswordResetToken active(
            UUID userId,
            String tokenHash,
            Instant expiresAt
    ) {
        return new PasswordResetToken(
                userId,
                tokenHash,
                expiresAt,
                false
        );
    }
}