package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

    void save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(
            String tokenHash
    );

    void replace(
            String currentTokenHash,
            RefreshToken replacement
    );

    void revokeByTokenHash(
            String tokenHash
    );

    /** Ends every session of the user: none of their refresh tokens works any more. */
    void revokeAllForUser(UUID userId);
}