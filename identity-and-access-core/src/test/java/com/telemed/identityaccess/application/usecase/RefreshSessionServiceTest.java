package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.RefreshSessionException;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenHasherPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenProviderPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.RefreshToken;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111"
    );

    private static final String CURRENT_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String NEW_HASH =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                    + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static final Instant NOW =
            Instant.parse("2026-10-01T18:00:00Z");

    @Mock
    private RefreshTokenRepositoryPort refreshTokens;

    @Mock
    private RefreshTokenHasherPort refreshTokenHasher;

    @Mock
    private RefreshTokenProviderPort refreshTokenProvider;

    @Mock
    private AccessTokenProviderPort accessTokens;

    @Mock
    private UserRepositoryPort users;

    private RefreshSessionService service;

    @BeforeEach
    void setUp() {

        service = new RefreshSessionService(
                refreshTokens,
                refreshTokenHasher,
                refreshTokenProvider,
                accessTokens,
                users,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    void shouldRotateValidRefreshToken() {

        Instant currentExpiresAt =
                NOW.plusSeconds(3600);

        Instant accessExpiresAt =
                NOW.plusSeconds(3600);

        Instant newRefreshExpiresAt =
                NOW.plusSeconds(604800);

        RefreshToken currentToken =
                RefreshToken.active(
                        USER_ID,
                        CURRENT_HASH,
                        currentExpiresAt
                );

        User user = activePatient();

        when(refreshTokenHasher.hash("current-refresh-token"))
                .thenReturn(CURRENT_HASH);

        when(refreshTokens.findByTokenHash(CURRENT_HASH))
                .thenReturn(Optional.of(currentToken));

        when(users.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(accessTokens.issue(
                USER_ID,
                Role.PATIENT
        )).thenReturn(
                new AccessTokenProviderPort.IssuedAccessToken(
                        "new-access-token",
                        accessExpiresAt
                )
        );

        when(refreshTokenProvider.issue())
                .thenReturn(
                        new RefreshTokenProviderPort.IssuedRefreshToken(
                                "new-refresh-token",
                                NEW_HASH,
                                newRefreshExpiresAt
                        )
                );

        var result =
                service.refresh("current-refresh-token");

        RefreshToken replacement =
                RefreshToken.active(
                        USER_ID,
                        NEW_HASH,
                        newRefreshExpiresAt
                );

        verify(refreshTokens).replace(
                CURRENT_HASH,
                replacement
        );

        assertThat(result.userId())
                .isEqualTo(USER_ID);

        assertThat(result.role())
                .isEqualTo(Role.PATIENT);

        assertThat(result.accessToken())
                .isEqualTo("new-access-token");

        assertThat(result.accessExpiresAt())
                .isEqualTo(accessExpiresAt);

        assertThat(result.refreshToken())
                .isEqualTo("new-refresh-token");

        assertThat(result.refreshExpiresAt())
                .isEqualTo(newRefreshExpiresAt);
    }

    @Test
    void shouldRejectUnknownRefreshToken() {

        when(refreshTokenHasher.hash("unknown-refresh-token"))
                .thenReturn(CURRENT_HASH);

        when(refreshTokens.findByTokenHash(CURRENT_HASH))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.refresh(
                        "unknown-refresh-token"
                )
        )
                .isInstanceOf(
                        RefreshSessionException.class
                );

        verify(accessTokens, never())
                .issue(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void shouldRejectRevokedRefreshToken() {

        RefreshToken revokedToken =
                new RefreshToken(
                        USER_ID,
                        CURRENT_HASH,
                        NOW.plusSeconds(3600),
                        true
                );

        when(refreshTokenHasher.hash("revoked-refresh-token"))
                .thenReturn(CURRENT_HASH);

        when(refreshTokens.findByTokenHash(CURRENT_HASH))
                .thenReturn(
                        Optional.of(revokedToken)
                );

        assertThatThrownBy(
                () -> service.refresh(
                        "revoked-refresh-token"
                )
        )
                .isInstanceOf(
                        RefreshSessionException.class
                );
    }

    @Test
    void shouldRejectExpiredRefreshToken() {

        RefreshToken expiredToken =
                RefreshToken.active(
                        USER_ID,
                        CURRENT_HASH,
                        NOW.minusSeconds(1)
                );

        when(refreshTokenHasher.hash("expired-refresh-token"))
                .thenReturn(CURRENT_HASH);

        when(refreshTokens.findByTokenHash(CURRENT_HASH))
                .thenReturn(
                        Optional.of(expiredToken)
                );

        assertThatThrownBy(
                () -> service.refresh(
                        "expired-refresh-token"
                )
        )
                .isInstanceOf(
                        RefreshSessionException.class
                );
    }

    private User activePatient() {

        return new User(
                USER_ID,
                "Patient Test",
                "patient@example.com",
                "DOC-100",
                "$2a$10$encodedPassword",
                Role.PATIENT,
                true,
                false
        );
    }
}