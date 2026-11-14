package com.telemed.identityaccess.application.port.out;

import java.time.Instant;

public interface RefreshTokenProviderPort {

    IssuedRefreshToken issue();

    record IssuedRefreshToken(
            String value,
            String tokenHash,
            Instant expiresAt
    ) {
    }
}