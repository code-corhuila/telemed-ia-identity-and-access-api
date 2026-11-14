package com.telemed.identityaccess.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record RefreshToken(
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        boolean revoked
) {

    private static final Pattern SHA_256_HEX =
            Pattern.compile("[0-9a-f]{64}");

    public RefreshToken {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "Refresh token user ID is required."
            );
        }

        if (tokenHash == null
                || !SHA_256_HEX.matcher(tokenHash).matches()) {

            throw new IllegalArgumentException(
                    "Refresh token hash must be a valid SHA-256 hexadecimal value."
            );
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "Refresh token expiration is required."
            );
        }
    }

    public static RefreshToken active(
            UUID userId,
            String tokenHash,
            Instant expiresAt
    ) {
        return new RefreshToken(
                userId,
                tokenHash,
                expiresAt,
                false
        );
    }
}