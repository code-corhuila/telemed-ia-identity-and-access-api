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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

        RefreshToken refreshToken =
                new RefreshToken(
                        USER_ID,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        expiresAt,
                        false
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
                .isEqualTo(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

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
                new RefreshToken(
                        USER_ID,
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        Instant.parse(
                                "2026-10-08T17:00:00Z"
                        ),
                        false
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
}