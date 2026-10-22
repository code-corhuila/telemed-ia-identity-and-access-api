package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.Role;
import java.util.UUID;
import java.time.Instant;

public interface AccessTokenProviderPort {

    /**
     * @param name full name shown to the user by the portals (standard {@code name} claim)
     */
    IssuedAccessToken issue(
            UUID userId,
            Role role,
            String name
    );

    record IssuedAccessToken(
            String value,
            Instant expiresAt
    ) {
    }
}