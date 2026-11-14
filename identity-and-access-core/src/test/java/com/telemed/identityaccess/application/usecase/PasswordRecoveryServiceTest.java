package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.event.PasswordRecoveryRequested;
import com.telemed.identityaccess.application.port.out.IntegrationEventOutboxPort;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final UUID RECOVERY_TOKEN_ID =
            UUID.fromString(
                    "22222222-2222-4222-8222-222222222222"
            );

    private static final String EMAIL =
            "patient@example.com";

    private static final String TOKEN_VALUE =
            "plain-reset-token";

    private static final String TOKEN_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final Instant EXPIRES_AT =
            Instant.parse("2026-10-19T15:30:00Z");

    private static final String RESET_URL =
            "https://telemed.example.com/reset-password";

    @Mock
    private UserRepositoryPort users;

    @Mock
    private PasswordResetTokenProviderPort tokenProvider;

    @Mock
    private PasswordResetTokenRepositoryPort resetTokens;

    @Mock
    private IntegrationEventOutboxPort outbox;

    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryService(
                users,
                tokenProvider,
                resetTokens,
                outbox,
                RESET_URL
        );
    }

    @Test
    void shouldRecordRecoveryEventForRegisteredUser() {
        when(users.findByEmail(EMAIL))
                .thenReturn(Optional.of(user()));

        when(tokenProvider.issue())
                .thenReturn(
                        new PasswordResetTokenProviderPort
                                .IssuedPasswordResetToken(
                                TOKEN_VALUE,
                                TOKEN_HASH,
                                EXPIRES_AT
                        )
                );

        when(resetTokens.save(any(PasswordResetToken.class)))
                .thenReturn(RECOVERY_TOKEN_ID);

        service.requestRecovery(EMAIL);

        ArgumentCaptor<PasswordRecoveryRequested> captor =
                ArgumentCaptor.forClass(
                        PasswordRecoveryRequested.class
                );

        verify(outbox).record(captor.capture());

        PasswordRecoveryRequested event =
                captor.getValue();

        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.recoveryTokenId())
                .isEqualTo(RECOVERY_TOKEN_ID);
        assertThat(event.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(event.resetUrl())
                .isEqualTo(RESET_URL + "#token=" + TOKEN_VALUE);
        assertThat(event.eventType())
                .isEqualTo("PasswordRecoveryRequested");
        assertThat(event.aggregateId()).isEqualTo(USER_ID);
    }

    @Test
    void shouldNotIssueTokenOrRecordEventForUnknownEmail() {
        when(users.findByEmail(EMAIL))
                .thenReturn(Optional.empty());

        service.requestRecovery(EMAIL);

        verifyNoInteractions(
                tokenProvider,
                resetTokens,
                outbox
        );
    }

    @Test
    void shouldRejectMissingResetUrl() {
        assertThatThrownBy(() ->
                new PasswordRecoveryService(
                        users,
                        tokenProvider,
                        resetTokens,
                        outbox,
                        " "
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private static User user() {
        return new User(
                USER_ID,
                "Recovery Patient",
                EMAIL,
                "DOC-100",
                "$2a$10$passwordHash",
                Role.PATIENT,
                true,
                true
        );
    }
}
