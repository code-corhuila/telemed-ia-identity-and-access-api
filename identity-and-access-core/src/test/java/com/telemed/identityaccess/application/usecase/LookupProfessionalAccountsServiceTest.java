package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.LookupProfessionalAccountsUseCase;
import com.telemed.identityaccess.application.port.in.LookupProfessionalAccountsUseCase.ProfessionalAccount;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Professional management shows the name of the professionals it lists; identity owns it. */
class LookupProfessionalAccountsServiceTest {

    private static final UUID DOCTOR_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final AccountDirectoryPort directory = mock(AccountDirectoryPort.class);

    private final LookupProfessionalAccountsUseCase useCase =
            new LookupProfessionalAccountsService(directory);

    @Test
    void shouldAnswerOnlyTheNameAndEmailOfProfessionals() {

        when(directory.findByIdsAndRole(Set.of(DOCTOR_ID), Role.PROFESSIONAL))
                .thenReturn(List.of(new AccountView(DOCTOR_ID, "Andrés Molina", "andres@telemed.co", "79876543",
                        Role.PROFESSIONAL, true, Instant.parse("2026-10-24T15:00:00Z"), null)));

        assertThat(useCase.lookup(Set.of(DOCTOR_ID)))
                .containsExactly(new ProfessionalAccount(DOCTOR_ID, "Andrés Molina", "andres@telemed.co"));
    }

    @Test
    void shouldNeverAskForAnotherRole() {

        useCase.lookup(Set.of(DOCTOR_ID));

        verify(directory, never()).findByIdsAndRole(any(), eq(Role.PATIENT));
        verify(directory, never()).findByIdsAndRole(any(), eq(Role.ADMIN));
    }

    @Test
    void shouldAcceptOneToOneHundredIds() {

        assertThatThrownBy(() -> useCase.lookup(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);

        Set<UUID> tooMany = IntStream.range(0, 101)
                .mapToObj(index -> UUID.randomUUID())
                .collect(java.util.stream.Collectors.toSet());

        assertThatThrownBy(() -> useCase.lookup(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids must contain 1 to 100 account ids.");
    }
}
