package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.port.out.PasswordResetTokenProviderPort;
import com.telemed.identityaccess.application.port.out.PasswordResetTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.PasswordResetToken;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111"
    );

    private static final String TOKEN_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private UserRepositoryPort users;

    @Mock
    private PasswordResetTokenProviderPort tokenProvider;

    @Mock
    private PasswordResetTokenRepositoryPort resetTokens;

    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryService(
                users,
                tokenProvider,
                resetTokens
        );
    }

    @Test
    void shouldCreatePasswordResetTokenForExistingUser() {

        User user = activePatient();

        when(users.findByEmail(
                "patient@example.com"
        )).thenReturn(
                Optional.of(user)
        );

        Instant expiresAt =
                Instant.parse(
                        "2026-10-02T14:30:00Z"
                );

        when(tokenProvider.issue())
                .thenReturn(
                        new PasswordResetTokenProviderPort
                                .IssuedPasswordResetToken(
                                "plain-reset-token",
                                TOKEN_HASH,
                                expiresAt
                        )
                );

        service.requestRecovery(
                "patient@example.com"
        );

        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(
                        PasswordResetToken.class
                );

        verify(resetTokens)
                .save(
                        captor.capture()
                );

        PasswordResetToken saved =
                captor.getValue();

        assertThat(saved.userId())
                .isEqualTo(USER_ID);

        assertThat(saved.tokenHash())
                .isEqualTo(TOKEN_HASH);

        assertThat(saved.expiresAt())
                .isEqualTo(expiresAt);

        assertThat(saved.used())
                .isFalse();
    }

    @Test
    void shouldNotRevealUnknownEmail() {

        when(users.findByEmail(
                "unknown@example.com"
        )).thenReturn(
                Optional.empty()
        );

        service.requestRecovery(
                "unknown@example.com"
        );

        verify(tokenProvider, never())
                .issue();

        verify(resetTokens, never())
                .save(
                        org.mockito.ArgumentMatchers.any()
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