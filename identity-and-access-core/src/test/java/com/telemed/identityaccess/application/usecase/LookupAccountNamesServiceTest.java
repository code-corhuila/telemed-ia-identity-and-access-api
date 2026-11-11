package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNamesNotAllowedException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;
import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase.AccountName;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** DEC-013: a professional sees who its patients are; an administrator sees anyone's name. */
class LookupAccountNamesServiceTest {

    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DOCTOR_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final AccountDirectoryPort directory = mock(AccountDirectoryPort.class);

    private final LookupAccountNamesUseCase useCase = new LookupAccountNamesService(directory);

    @Test
    void shouldGiveAProfessionalOnlyTheNamesOfPatients() {

        when(directory.findByIdsAndRole(Set.of(PATIENT_ID, DOCTOR_ID), Role.PATIENT))
                .thenReturn(List.of(account(PATIENT_ID, "Laura Gómez", Role.PATIENT)));

        assertThat(useCase.lookup(Role.PROFESSIONAL, Set.of(PATIENT_ID, DOCTOR_ID)))
                .containsExactly(new AccountName(PATIENT_ID, "Laura Gómez"));
        verify(directory, never()).findByIds(any());
    }

    @Test
    void shouldGiveAnAdministratorTheNameOfAnyAccount() {

        when(directory.findByIds(Set.of(PATIENT_ID, DOCTOR_ID)))
                .thenReturn(List.of(
                        account(PATIENT_ID, "Laura Gómez", Role.PATIENT),
                        account(DOCTOR_ID, "Andrés Molina", Role.PROFESSIONAL)));

        assertThat(useCase.lookup(Role.ADMIN, Set.of(PATIENT_ID, DOCTOR_ID)))
                .containsExactlyInAnyOrder(
                        new AccountName(PATIENT_ID, "Laura Gómez"),
                        new AccountName(DOCTOR_ID, "Andrés Molina"));
    }

    @Test
    void shouldRefuseAPatient() {

        assertThatThrownBy(() -> useCase.lookup(Role.PATIENT, Set.of(DOCTOR_ID)))
                .isInstanceOf(AccountNamesNotAllowedException.class);
        verifyNoInteractions(directory);
    }

    @Test
    void shouldAcceptOneToFiftyIds() {

        assertThatThrownBy(() -> useCase.lookup(Role.ADMIN, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids must contain 1 to 50 account ids.");

        Set<UUID> tooMany = IntStream.range(0, 51)
                .mapToObj(index -> UUID.randomUUID())
                .collect(Collectors.toSet());

        assertThatThrownBy(() -> useCase.lookup(Role.ADMIN, tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids must contain 1 to 50 account ids.");
    }

    @Test
    void shouldGiveAServiceTheNameOfAnyAccount() {

        when(directory.findByIds(Set.of(PATIENT_ID, DOCTOR_ID)))
                .thenReturn(List.of(
                        account(PATIENT_ID, "Laura Gómez", Role.PATIENT),
                        account(DOCTOR_ID, "Andrés Molina", Role.PROFESSIONAL)));

        assertThat(useCase.lookupForService(Set.of(PATIENT_ID, DOCTOR_ID)))
                .containsExactlyInAnyOrder(
                        new AccountName(PATIENT_ID, "Laura Gómez"),
                        new AccountName(DOCTOR_ID, "Andrés Molina"));
    }

    @Test
    void shouldAcceptOneToFiftyIdsForAService() {

        assertThatThrownBy(() -> useCase.lookupForService(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids must contain 1 to 50 account ids.");
        verifyNoInteractions(directory);
    }

    private static AccountView account(UUID id, String fullName, Role role) {
        return new AccountView(id, fullName, fullName.toLowerCase().replace(' ', '.') + "@telemed.co",
                "DOC-" + id.toString().substring(0, 4), role, true,
                Instant.parse("2026-10-24T15:00:00Z"), null);
    }
}
