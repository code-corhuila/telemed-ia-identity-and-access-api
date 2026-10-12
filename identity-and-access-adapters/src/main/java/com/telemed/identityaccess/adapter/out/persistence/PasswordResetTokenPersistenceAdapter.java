package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.domain.model.PasswordResetToken;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

    @Override
    public Optional<PasswordResetToken> findByTokenHash(
            String tokenHash
    ) {

        try {
            return tokens.findByTokenHash(
                            tokenHash
                    )
                    .map(this::toDomain);

        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Password reset token could not be retrieved.",
                    exception
            );
        }
    }

    @Override
    @Transactional
    public void markUsedByTokenHash(
            String tokenHash
    ) {

        try {
            int updatedRows =
                    tokens.markUsedByTokenHash(
                            tokenHash
                    );

            if (updatedRows != 1) {
                throw new IllegalStateException(
                        "Password reset token could not be marked as used."
                );
            }

        } catch (IllegalStateException exception) {
            throw exception;

        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Password reset token could not be marked as used.",
                    exception
            );
        }
    }

    private PasswordResetToken toDomain(
            PasswordResetTokenJpaEntity entity
    ) {

        return new PasswordResetToken(
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt().toInstant(),
                entity.isUsed()
        );
    }
}