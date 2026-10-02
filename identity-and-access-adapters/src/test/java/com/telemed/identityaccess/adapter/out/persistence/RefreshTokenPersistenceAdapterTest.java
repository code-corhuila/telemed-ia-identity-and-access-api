package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.exception.RefreshTokenPersistenceException;
import com.telemed.identityaccess.domain.model.RefreshToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenPersistenceAdapterTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111"
    );

    private static final String CURRENT_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String NEW_HASH =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                    + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

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

        RefreshToken refreshToken =
                RefreshToken.active(
                        USER_ID,
                        CURRENT_HASH,
                        expiresAt
                );

        adapter.save(refreshToken);

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
                .isEqualTo(CURRENT_HASH);

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

    @Test
    void shouldTranslatePersistenceFailure() {

        RefreshTokenPersistenceAdapter adapter =
                new RefreshTokenPersistenceAdapter(
                        repository
                );

        RefreshToken refreshToken =
                RefreshToken.active(
                        USER_ID,
                        NEW_HASH,
                        Instant.parse(
                                "2026-10-08T17:00:00Z"
                        )
                );

        doThrow(
                new DataIntegrityViolationException(
                        "duplicate token hash"
                )
        ).when(repository)
                .saveAndFlush(any());

        assertThatThrownBy(
                () -> adapter.save(refreshToken)
        )
                .isInstanceOf(
                        RefreshTokenPersistenceException.class
                )
                .hasMessage(
                        "Refresh token could not be persisted."
                )
                .hasCauseInstanceOf(
                        DataIntegrityViolationException.class
                );
    }

    @Test
    void shouldFindRefreshTokenByHash() {

        RefreshTokenJpaEntity entity =
                RefreshTokenJpaEntity.fromDomain(
                        RefreshToken.active(
                                USER_ID,
                                CURRENT_HASH,
                                Instant.parse(
                                        "2026-10-08T17:00:00Z"
                                )
                        )
                );

        when(repository.findByTokenHash(
                CURRENT_HASH
        )).thenReturn(
                Optional.of(entity)
        );

        RefreshTokenPersistenceAdapter adapter =
                new RefreshTokenPersistenceAdapter(
                        repository
                );

        Optional<RefreshToken> result =
                adapter.findByTokenHash(
                        CURRENT_HASH
                );

        assertThat(result)
                .isPresent();

        assertThat(result.get().userId())
                .isEqualTo(USER_ID);

        assertThat(result.get().tokenHash())
                .isEqualTo(CURRENT_HASH);

        assertThat(result.get().revoked())
                .isFalse();
    }

    @Test
    void shouldReplaceRefreshTokenAtomically() {

        RefreshTokenPersistenceAdapter adapter =
                new RefreshTokenPersistenceAdapter(
                        repository
                );

        RefreshToken replacement =
                RefreshToken.active(
                        USER_ID,
                        NEW_HASH,
                        Instant.parse(
                                "2026-10-08T17:00:00Z"
                        )
                );

        when(repository.revokeByTokenHash(
                CURRENT_HASH
        )).thenReturn(1);

        adapter.replace(
                CURRENT_HASH,
                replacement
        );

        verify(repository)
                .revokeByTokenHash(
                        CURRENT_HASH
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
                .isEqualTo(NEW_HASH);

        assertThat(saved.isRevoked())
                .isFalse();
    }

    @Test
void shouldRevokeRefreshTokenByHash() {

    RefreshTokenPersistenceAdapter adapter =
            new RefreshTokenPersistenceAdapter(
                    repository
            );

    when(repository.revokeByTokenHash(
            CURRENT_HASH
    )).thenReturn(1);

    adapter.revokeByTokenHash(
            CURRENT_HASH
    );

    verify(repository)
            .revokeByTokenHash(
                    CURRENT_HASH
            );
}

@Test
void shouldFailWhenRefreshTokenCannotBeRevoked() {

    RefreshTokenPersistenceAdapter adapter =
            new RefreshTokenPersistenceAdapter(
                    repository
            );

    when(repository.revokeByTokenHash(
            CURRENT_HASH
    )).thenReturn(0);

    assertThatThrownBy(
            () -> adapter.revokeByTokenHash(
                    CURRENT_HASH
            )
    )
            .isInstanceOf(
                    RefreshTokenPersistenceException.class
            )
            .hasMessage(
                    "Refresh token could not be revoked."
            );
}
}