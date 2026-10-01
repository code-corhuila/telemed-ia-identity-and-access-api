package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepositoryPort {

    void save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(
            String tokenHash
    );

    void replace(
            String currentTokenHash,
            RefreshToken replacement
    );
}