package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.PasswordResetException;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenHasherPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.PasswordResetToken;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final String TOKEN_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final Instant NOW =
            Instant.parse("2026-10-02T15:00:00Z");

    private static final String NEW_PASSWORD =
            "NewSecret123!";

    @Mock
    private PasswordResetTokenRepositoryPort resetTokens;

    @Mock
    private PasswordResetTokenHasherPort tokenHasher;

    @Mock
    private UserRepositoryPort users;

    @Mock
    private PasswordHasherPort passwordHasher;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {

        service = new PasswordResetService(
                resetTokens,
                tokenHasher,
                users,
                passwordHasher,
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    void shouldResetPasswordUsingValidToken() {

        PasswordResetToken token =
                PasswordResetToken.active(
                        USER_ID,
                        TOKEN_HASH,
                        NOW.plusSeconds(1800)
                );

        when(tokenHasher.hash(
                "plain-reset-token"
        )).thenReturn(
                TOKEN_HASH
        );

        when(resetTokens.findByTokenHash(
                TOKEN_HASH
        )).thenReturn(
                Optional.of(token)
        );

        when(passwordHasher.hash(
                NEW_PASSWORD
        )).thenReturn(
                "$2a$10$newPasswordHash"
        );

        service.reset(
                "plain-reset-token",
                NEW_PASSWORD
        );

        verify(users)
                .updatePasswordHash(
                        USER_ID,
                        "$2a$10$newPasswordHash"
                );

        verify(resetTokens)
                .markUsedByTokenHash(
                        TOKEN_HASH
                );
    }

    @Test
    void shouldRejectUnknownResetToken() {

        when(tokenHasher.hash(
                "unknown-reset-token"
        )).thenReturn(
                TOKEN_HASH
        );

        when(resetTokens.findByTokenHash(
                TOKEN_HASH
        )).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () -> service.reset(
                        "unknown-reset-token",
                        NEW_PASSWORD
                )
        )
                .isInstanceOf(
                        PasswordResetException.class
                );

        verify(passwordHasher, never())
                .hash(
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(users, never())
                .updatePasswordHash(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    @Test
    void shouldRejectAlreadyUsedResetToken() {

        PasswordResetToken usedToken =
                new PasswordResetToken(
                        USER_ID,
                        TOKEN_HASH,
                        NOW.plusSeconds(1800),
                        true
                );

        when(tokenHasher.hash(
                "used-reset-token"
        )).thenReturn(
                TOKEN_HASH
        );

        when(resetTokens.findByTokenHash(
                TOKEN_HASH
        )).thenReturn(
                Optional.of(usedToken)
        );

        assertThatThrownBy(
                () -> service.reset(
                        "used-reset-token",
                        NEW_PASSWORD
                )
        )
                .isInstanceOf(
                        PasswordResetException.class
                );

        verify(users, never())
                .updatePasswordHash(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(resetTokens, never())
                .markUsedByTokenHash(
                        TOKEN_HASH
                );
    }

    @Test
    void shouldRejectExpiredResetToken() {

        PasswordResetToken expiredToken =
                PasswordResetToken.active(
                        USER_ID,
                        TOKEN_HASH,
                        NOW.minusSeconds(1)
                );

        when(tokenHasher.hash(
                "expired-reset-token"
        )).thenReturn(
                TOKEN_HASH
        );

        when(resetTokens.findByTokenHash(
                TOKEN_HASH
        )).thenReturn(
                Optional.of(expiredToken)
        );

        assertThatThrownBy(
                () -> service.reset(
                        "expired-reset-token",
                        NEW_PASSWORD
                )
        )
                .isInstanceOf(
                        PasswordResetException.class
                );

        verify(users, never())
                .updatePasswordHash(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(resetTokens, never())
                .markUsedByTokenHash(
                        TOKEN_HASH
                );
    }

    @Test
    void shouldRejectBlankResetToken() {

        assertThatThrownBy(
                () -> service.reset(
                        " ",
                        NEW_PASSWORD
                )
        )
                .isInstanceOf(
                        PasswordResetException.class
                );

        verify(tokenHasher, never())
                .hash(
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    @Test
    void shouldRejectBlankNewPassword() {

        assertThatThrownBy(
                () -> service.reset(
                        "plain-reset-token",
                        " "
                )
        )
                .isInstanceOf(
                        PasswordResetException.class
                );

        verify(tokenHasher, never())
                .hash(
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(passwordHasher, never())
                .hash(
                        org.mockito.ArgumentMatchers.anyString()
                );
    }
}