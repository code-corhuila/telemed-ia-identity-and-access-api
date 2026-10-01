package com.telemed.identityaccess.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenPersistenceAdapterTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111"
    );

    @Mock
    private SpringDataRefreshTokenRepository repository;

    @Test
    void shouldPersistRefreshTokenUsingUuidUserIdentifier() {

        RefreshTokenPersistenceAdapter adapter =
                new RefreshTokenPersistenceAdapter(
                        repository
                );

        Instant expiresAt =
                Instant.parse("2026-10-08T17:00:00Z");

        adapter.save(
                USER_ID,
                "hashed-refresh-token",
                expiresAt
        );

        ArgumentCaptor<RefreshTokenJpaEntity> captor =
                ArgumentCaptor.forClass(
                        RefreshTokenJpaEntity.class
                );

        verify(repository)
                .saveAndFlush(
                        captor.capture()
                );

        RefreshTokenJpaEntity saved =
                captor.getValue();

        assertThat(saved.getUserId())
                .isEqualTo(USER_ID);

        assertThat(saved.getTokenHash())
                .isEqualTo("hashed-refresh-token");

        assertThat(saved.getExpiresAt())
                .isEqualTo(
                        OffsetDateTime.ofInstant(
                                expiresAt,
                                ZoneOffset.UTC
                        )
                );

        assertThat(saved.isRevoked())
                .isFalse();
    }
}