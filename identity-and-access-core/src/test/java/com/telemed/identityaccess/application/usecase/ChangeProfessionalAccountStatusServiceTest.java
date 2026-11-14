package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.ChangeProfessionalAccountStatusUseCase;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The onboarding saga activates the professional account at the end, or deactivates it to compensate. */
class ChangeProfessionalAccountStatusServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");

    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final RefreshTokenRepositoryPort refreshTokens = mock(RefreshTokenRepositoryPort.class);

    private final ChangeProfessionalAccountStatusUseCase useCase =
            new ChangeProfessionalAccountStatusService(users, refreshTokens);

    @Test
    void shouldActivateAnInactiveProfessionalAccount() {

        when(users.findById(ACCOUNT_ID)).thenReturn(Optional.of(user(Role.PROFESSIONAL, false)));

        useCase.change(ACCOUNT_ID, true);

        verify(users).updateActive(ACCOUNT_ID, true);
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void shouldDeactivateTheAccountAndCloseItsSessions() {

        when(users.findById(ACCOUNT_ID)).thenReturn(Optional.of(user(Role.PROFESSIONAL, true)));

        useCase.change(ACCOUNT_ID, false);

        verify(users).updateActive(ACCOUNT_ID, false);
        verify(refreshTokens).revokeAllForUser(ACCOUNT_ID);
    }

    /** Idempotent: a retried step or compensation finds the account already in that state. */
    @Test
    void shouldChangeNothingWhenTheAccountIsAlreadyInThatState() {

        when(users.findById(ACCOUNT_ID)).thenReturn(Optional.of(user(Role.PROFESSIONAL, false)));

        useCase.change(ACCOUNT_ID, false);

        verify(users, never()).updateActive(any(), anyBoolean());
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    /** A service may never block a patient or an administrator: those accounts are not visible here. */
    @Test
    void shouldAnswerNotFoundForAnAccountThatIsNotAProfessional() {

        when(users.findById(ACCOUNT_ID)).thenReturn(Optional.of(user(Role.ADMIN, true)));

        assertThatThrownBy(() -> useCase.change(ACCOUNT_ID, false))
                .isInstanceOf(AccountNotFoundException.class);

        verify(users, never()).updateActive(any(), anyBoolean());
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownAccount() {

        when(users.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.change(ACCOUNT_ID, true))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private static User user(Role role, boolean active) {
        return new User(ACCOUNT_ID, "Doctor", "doctor@telemed.co", "DOC-1", "hash", role, active, true);
    }
}
