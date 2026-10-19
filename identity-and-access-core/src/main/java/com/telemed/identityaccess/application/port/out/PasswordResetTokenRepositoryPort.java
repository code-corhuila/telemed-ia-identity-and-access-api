package com.telemed.identityaccess.application.port.out;

import com.telemed.identityaccess.domain.model.PasswordResetToken;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepositoryPort {

    /**
     * @return the identifier assigned to the stored token
     */
    UUID save(
            PasswordResetToken passwordResetToken
    );

    Optional<PasswordResetToken> findByTokenHash(
            String tokenHash
    );

    boolean markUsedByTokenHash(
            String tokenHash
    );
}