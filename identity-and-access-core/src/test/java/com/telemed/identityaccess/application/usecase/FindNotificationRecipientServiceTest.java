package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.FindNotificationRecipientUseCase;
import com.telemed.identityaccess.application.port.in.FindNotificationRecipientUseCase.NotificationRecipient;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Notifications e-mails a patient about their appointments; identity owns the address. */
class FindNotificationRecipientServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final AccountDirectoryPort directory = mock(AccountDirectoryPort.class);

    private final FindNotificationRecipientUseCase useCase = new FindNotificationRecipientService(directory);

    @Test
    void shouldAnswerOnlyTheEmailOfAnActivePatient() {

        when(directory.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(Role.PATIENT, true)));

        assertThat(useCase.find(ACCOUNT_ID))
                .isEqualTo(new NotificationRecipient(ACCOUNT_ID, "laura@telemed.co"));
    }

    @Test
    void shouldNotAnswerAnInactivePatient() {

        when(directory.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(Role.PATIENT, false)));

        assertThatThrownBy(() -> useCase.find(ACCOUNT_ID)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void shouldNotAnswerProfessionalsOrAdministrators() {

        for (Role role : new Role[]{Role.PROFESSIONAL, Role.ADMIN}) {
            when(directory.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(role, true)));

            assertThatThrownBy(() -> useCase.find(ACCOUNT_ID)).isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Test
    void shouldNotAnswerAnUnknownAccount() {

        when(directory.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.find(ACCOUNT_ID)).isInstanceOf(AccountNotFoundException.class);
    }

    private static AccountView account(Role role, boolean active) {
        return new AccountView(ACCOUNT_ID, "Laura Gómez", "laura@telemed.co", "1012345678",
                role, active, Instant.parse("2026-10-24T15:00:00Z"), null);
    }
}
