package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

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
    public void save(
            UUID userId,
            String tokenHash,
            Instant expiresAt
    ) {

        RefreshTokenJpaEntity entity =
                new RefreshTokenJpaEntity(
                        userId,
                        tokenHash,
                        OffsetDateTime.ofInstant(
                                expiresAt,
                                ZoneOffset.UTC
                        )
                );

        tokens.saveAndFlush(entity);
    }
}