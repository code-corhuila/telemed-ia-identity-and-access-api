package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.LogoutException;
import com.telemed.identityaccess.application.port.out.RefreshTokenHasherPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.domain.model.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111"
    );

    private static final String TOKEN_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private RefreshTokenRepositoryPort refreshTokens;

    @Mock
    private RefreshTokenHasherPort refreshTokenHasher;

    private LogoutService service;

    @BeforeEach
    void setUp() {
        service = new LogoutService(
                refreshTokens,
                refreshTokenHasher
        );
    }

    @Test
    void shouldRevokeActiveRefreshToken() {

        RefreshToken storedToken =
                RefreshToken.active(
                        USER_ID,
                        TOKEN_HASH,
                        Instant.parse(
                                "2026-10-08T20:00:00Z"
                        )
                );

        when(refreshTokenHasher.hash(
                "current-refresh-token"
        )).thenReturn(TOKEN_HASH);

        when(refreshTokens.findByTokenHash(
                TOKEN_HASH
        )).thenReturn(
                Optional.of(storedToken)
        );

        service.logout(
                "current-refresh-token"
        );

        verify(refreshTokens)
                .revokeByTokenHash(
                        TOKEN_HASH
                );
    }

    @Test
    void shouldRejectUnknownRefreshToken() {

        when(refreshTokenHasher.hash(
                "unknown-refresh-token"
        )).thenReturn(TOKEN_HASH);

        when(refreshTokens.findByTokenHash(
                TOKEN_HASH
        )).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () -> service.logout(
                        "unknown-refresh-token"
                )
        )
                .isInstanceOf(
                        LogoutException.class
                );

        verify(refreshTokens, never())
                .revokeByTokenHash(
                        TOKEN_HASH
                );
    }

    @Test
    void shouldRejectAlreadyRevokedRefreshToken() {

        RefreshToken revokedToken =
                new RefreshToken(
                        USER_ID,
                        TOKEN_HASH,
                        Instant.parse(
                                "2026-10-08T20:00:00Z"
                        ),
                        true
                );

        when(refreshTokenHasher.hash(
                "revoked-refresh-token"
        )).thenReturn(TOKEN_HASH);

        when(refreshTokens.findByTokenHash(
                TOKEN_HASH
        )).thenReturn(
                Optional.of(revokedToken)
        );

        assertThatThrownBy(
                () -> service.logout(
                        "revoked-refresh-token"
                )
        )
                .isInstanceOf(
                        LogoutException.class
                );

        verify(refreshTokens, never())
                .revokeByTokenHash(
                        TOKEN_HASH
                );
    }

    @Test
    void shouldRejectBlankRefreshToken() {

        assertThatThrownBy(
                () -> service.logout(" ")
        )
                .isInstanceOf(
                        LogoutException.class
                );

        verify(refreshTokens, never())
                .findByTokenHash(
                        org.mockito.ArgumentMatchers.anyString()
                );
    }
}