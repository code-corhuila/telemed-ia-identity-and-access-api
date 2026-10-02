package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenRepositoryPort {

    void save(
            PasswordResetToken passwordResetToken
    );

    Optional<PasswordResetToken> findByTokenHash(
            String tokenHash
    );

    void markUsedByTokenHash(
            String tokenHash
    );
}