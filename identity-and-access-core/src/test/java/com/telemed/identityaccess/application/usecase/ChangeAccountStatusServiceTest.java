package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.exception.AccountStatusConflictException;
import com.telemed.identityaccess.application.exception.AdministratorRequiredException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.ChangeAccountStatusUseCase;
import com.telemed.identityaccess.application.port.in.ChangeAccountStatusUseCase.Command;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccountStatusConflictException.Reason.LAST_ACTIVE_ADMIN;
import static com.telemed.identityaccess.application.exception.AccountStatusConflictException.Reason.SELF_DEACTIVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FR-012: the administrator blocks or restores the access of an account. */
class ChangeAccountStatusServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID OTHER_ADMIN_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PATIENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final RefreshTokenRepositoryPort refreshTokens = mock(RefreshTokenRepositoryPort.class);
    private final AccountDirectoryPort directory = mock(AccountDirectoryPort.class);

    private final ChangeAccountStatusUseCase useCase =
            new ChangeAccountStatusService(users, refreshTokens, directory);

    @BeforeEach
    void accounts() {
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, Role.ADMIN, true)));
        when(directory.findById(PATIENT_ID)).thenReturn(Optional.of(view(PATIENT_ID, false)));
    }

    @Test
    void shouldBlockTheAccountAndCloseItsSessions() {

        when(users.findById(PATIENT_ID)).thenReturn(Optional.of(user(PATIENT_ID, Role.PATIENT, true)));

        AccountView result = useCase.change(new Command(ADMIN_ID, PATIENT_ID, false));

        verify(users).updateActive(PATIENT_ID, false);
        verify(refreshTokens).revokeAllForUser(PATIENT_ID);
        assertThat(result).isEqualTo(view(PATIENT_ID, false));
    }

    @Test
    void shouldRestoreTheAccess() {

        when(users.findById(PATIENT_ID)).thenReturn(Optional.of(user(PATIENT_ID, Role.PATIENT, false)));

        useCase.change(new Command(ADMIN_ID, PATIENT_ID, true));

        verify(users).updateActive(PATIENT_ID, true);
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void shouldChangeNothingWhenTheAccountIsAlreadyInThatState() {

        when(users.findById(PATIENT_ID)).thenReturn(Optional.of(user(PATIENT_ID, Role.PATIENT, false)));

        useCase.change(new Command(ADMIN_ID, PATIENT_ID, false));

        verify(users, never()).updateActive(any(), anyBoolean());
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void shouldNeverLetAnAdminBlockThemselves() {

        assertThatThrownBy(() -> useCase.change(new Command(ADMIN_ID, ADMIN_ID, false)))
                .isInstanceOf(AccountStatusConflictException.class)
                .satisfies(exception -> assertThat(((AccountStatusConflictException) exception).reason())
                        .isEqualTo(SELF_DEACTIVATION));

        verify(users, never()).updateActive(any(), anyBoolean());
    }

    @Test
    void shouldKeepAtLeastOneActiveAdmin() {

        when(users.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(user(OTHER_ADMIN_ID, Role.ADMIN, true)));
        when(users.countActiveByRoleLocking(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> useCase.change(new Command(ADMIN_ID, OTHER_ADMIN_ID, false)))
                .isInstanceOf(AccountStatusConflictException.class)
                .satisfies(exception -> assertThat(((AccountStatusConflictException) exception).reason())
                        .isEqualTo(LAST_ACTIVE_ADMIN));

        verify(users, never()).updateActive(any(), anyBoolean());
    }

    @Test
    void shouldBlockAnotherAdminWhileOneStaysActive() {

        when(users.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(user(OTHER_ADMIN_ID, Role.ADMIN, true)));
        when(users.countActiveByRoleLocking(Role.ADMIN)).thenReturn(2L);
        when(directory.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(view(OTHER_ADMIN_ID, false)));

        useCase.change(new Command(ADMIN_ID, OTHER_ADMIN_ID, false));

        verify(users).updateActive(OTHER_ADMIN_ID, false);
        verify(refreshTokens).revokeAllForUser(OTHER_ADMIN_ID);
    }

    @Test
    void shouldReportAnUnknownAccount() {

        when(users.findById(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.change(new Command(ADMIN_ID, PATIENT_ID, false)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void shouldRefuseAnAdminWhoseAccountWasBlocked() {

        // Their access token may still be valid for up to an hour; the account is the authority.
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, Role.ADMIN, false)));
        when(users.findById(PATIENT_ID)).thenReturn(Optional.of(user(PATIENT_ID, Role.PATIENT, true)));

        assertThatThrownBy(() -> useCase.change(new Command(ADMIN_ID, PATIENT_ID, false)))
                .isInstanceOf(AdministratorRequiredException.class);

        verify(users, never()).updateActive(any(), anyBoolean());
    }

    private static User user(UUID id, Role role, boolean active) {
        return new User(id, "Some Name", id + "@telemed.test", "DOC-" + id, "$2a$10$hash", role, active, true);
    }

    private static AccountView view(UUID id, boolean active) {
        return new AccountView(id, "Some Name", id + "@telemed.test", "DOC-" + id, Role.PATIENT, active,
                Instant.parse("2026-10-20T14:00:00Z"), null);
    }
}
