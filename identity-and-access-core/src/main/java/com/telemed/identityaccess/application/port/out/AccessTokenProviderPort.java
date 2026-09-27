package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.Role;

import java.time.Instant;

public interface AccessTokenProviderPort {

    IssuedAccessToken issue(
            Long userId,
            Role role
    );

    record IssuedAccessToken(
            String value,
            Instant expiresAt
    ) {
    }
}