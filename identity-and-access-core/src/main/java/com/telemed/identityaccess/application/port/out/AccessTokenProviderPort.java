package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.Role;
import java.util.UUID;
import java.time.Instant;

public interface AccessTokenProviderPort {

    IssuedAccessToken issue(
            UUID userId,
            Role role
    );

    record IssuedAccessToken(
            String value,
            Instant expiresAt
    ) {
    }
}