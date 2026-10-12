package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.exception.RefreshTokenPersistenceException;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.domain.model.RefreshToken;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class RefreshTokenPersistenceAdapter
        implements RefreshTokenRepositoryPort {

    private final SpringDataRefreshTokenRepository tokens;

    public RefreshTokenPersistenceAdapter(
            SpringDataRefreshTokenRepository tokens
    ) {
        this.tokens = tokens;
    }

    @Override
    public void save(RefreshToken refreshToken) {

        RefreshTokenJpaEntity entity =
                RefreshTokenJpaEntity.fromDomain(
                        refreshToken
                );

        try {
            tokens.saveAndFlush(entity);

        } catch (DataAccessException exception) {
            throw new RefreshTokenPersistenceException(
                    "Refresh token could not be persisted.",
                    exception
            );
        }
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(
            String tokenHash
    ) {

        try {
            return tokens.findByTokenHash(tokenHash)
                    .map(this::toDomain);

        } catch (DataAccessException exception) {
            throw new RefreshTokenPersistenceException(
                    "Refresh token could not be retrieved.",
                    exception
            );
        }
    }

    @Override
    @Transactional
    public void replace(
            String currentTokenHash,
            RefreshToken replacement
    ) {

        try {
            int revokedRows =
                    tokens.revokeByTokenHash(
                            currentTokenHash
                    );

            if (revokedRows != 1) {
                throw new RefreshTokenPersistenceException(
                        "Refresh token could not be replaced.",
                        null
                );
            }

            RefreshTokenJpaEntity replacementEntity =
                    RefreshTokenJpaEntity.fromDomain(
                            replacement
                    );

            tokens.saveAndFlush(
                    replacementEntity
            );

        } catch (RefreshTokenPersistenceException exception) {
            throw exception;

        } catch (DataAccessException exception) {
            throw new RefreshTokenPersistenceException(
                    "Refresh token could not be replaced.",
                    exception
            );
        }
    }

    private RefreshToken toDomain(
            RefreshTokenJpaEntity entity
    ) {

        return new RefreshToken(
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt().toInstant(),
                entity.isRevoked()
        );
    }
}