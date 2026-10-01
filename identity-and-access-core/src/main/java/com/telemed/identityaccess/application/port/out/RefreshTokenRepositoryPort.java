package com.telemed.identityaccess.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

    void save(
            UUID userId,
            String tokenHash,
            Instant expiresAt
    );
}