package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.domain.model.PasswordResetToken;
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
class PasswordResetTokenPersistenceAdapterTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final String TOKEN_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private SpringDataPasswordResetTokenRepository repository;

    @Test
    void shouldPersistPasswordResetTokenUsingUuidUserIdentifier() {

        PasswordResetTokenPersistenceAdapter adapter =
                new PasswordResetTokenPersistenceAdapter(
                        repository
                );

        Instant expiresAt =
                Instant.parse(
                        "2026-10-02T15:30:00Z"
                );

        PasswordResetToken token =
                PasswordResetToken.active(
                        USER_ID,
                        TOKEN_HASH,
                        expiresAt
                );

        adapter.save(token);

        ArgumentCaptor<PasswordResetTokenJpaEntity> captor =
                ArgumentCaptor.forClass(
                        PasswordResetTokenJpaEntity.class
                );

        verify(repository)
                .saveAndFlush(
                        captor.capture()
                );

        PasswordResetTokenJpaEntity saved =
                captor.getValue();

        assertThat(saved.getUserId())
                .isEqualTo(USER_ID);

        assertThat(saved.getTokenHash())
                .isEqualTo(TOKEN_HASH);

        assertThat(saved.getExpiresAt())
                .isEqualTo(
                        OffsetDateTime.ofInstant(
                                expiresAt,
                                ZoneOffset.UTC
                        )
                );

        assertThat(saved.isUsed())
                .isFalse();
    }
}