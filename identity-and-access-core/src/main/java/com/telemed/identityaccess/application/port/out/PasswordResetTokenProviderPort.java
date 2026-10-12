package com.telemed.identityaccess.application.port.out;

import java.time.Instant;

public interface PasswordResetTokenProviderPort {

    IssuedPasswordResetToken issue();

    record IssuedPasswordResetToken(
            String value,
            String tokenHash,
            Instant expiresAt
    ) {
    }
}