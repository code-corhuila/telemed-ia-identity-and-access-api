package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

public interface AccessTokenVerifierPort {

    VerifiedAccessToken verify(String token);

    record VerifiedAccessToken(UUID userId, Role role, Instant expiresAt) {
    }
}
