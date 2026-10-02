package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.domain.model.PasswordResetToken;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetTokenPersistenceAdapter
        implements PasswordResetTokenRepositoryPort {

    private final SpringDataPasswordResetTokenRepository tokens;

    public PasswordResetTokenPersistenceAdapter(
            SpringDataPasswordResetTokenRepository tokens
    ) {
        this.tokens = tokens;
    }

    @Override
    public void save(
            PasswordResetToken passwordResetToken
    ) {

        PasswordResetTokenJpaEntity entity =
                PasswordResetTokenJpaEntity.fromDomain(
                        passwordResetToken
                );

        try {
            tokens.saveAndFlush(entity);

        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Password reset token could not be persisted.",
                    exception
            );
        }
    }
}