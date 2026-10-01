package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.exception.RefreshTokenPersistenceException;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.domain.model.RefreshToken;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
}