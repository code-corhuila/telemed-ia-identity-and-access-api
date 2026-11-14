package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountPage;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.Query;
import com.telemed.identityaccess.application.port.out.AccountDirectoryPort;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** FR-012: the administrator views the registered user accounts. */
class BrowseAccountsServiceTest {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");

    private final AccountDirectoryPort directory =
            mock(AccountDirectoryPort.class);

    private final BrowseAccountsUseCase useCase =
            new BrowseAccountsService(directory);

    @Test
    void shouldAnswerThePageTheDirectoryFinds() {

        Query query = new Query("doctor", Role.PROFESSIONAL, true, 2, 10);
        AccountPage page = new AccountPage(List.of(account()), 2, 10, 11);
        when(directory.search(query)).thenReturn(page);

        assertThat(useCase.browse(query)).isEqualTo(page);
    }

    @Test
    void shouldCountThePages() {

        assertThat(new AccountPage(List.of(), 1, 20, 0).totalPages()).isZero();
        assertThat(new AccountPage(List.of(), 1, 20, 20).totalPages()).isEqualTo(1);
        assertThat(new AccountPage(List.of(), 1, 20, 21).totalPages()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreABlankSearchText() {

        assertThat(new Query("   ", null, null, 1, 20).text()).isNull();
        assertThat(new Query(" Ana ", null, null, 1, 20).text()).isEqualTo("Ana");
    }

    @Test
    void shouldOnlyAcceptTheContractPagination() {

        assertThatThrownBy(() -> new Query(null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Query(null, null, null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Query(null, null, null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAnswerOneAccount() {

        when(directory.findById(ACCOUNT_ID)).thenReturn(Optional.of(account()));

        assertThat(useCase.view(ACCOUNT_ID)).isEqualTo(account());
    }

    @Test
    void shouldReportAnUnknownAccount() {

        when(directory.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.view(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private static AccountView account() {
        return new AccountView(
                ACCOUNT_ID,
                "Doctor Name",
                "doctor@telemed.test",
                "DOC-0001",
                Role.PROFESSIONAL,
                true,
                Instant.parse("2026-10-20T14:00:00Z"),
                null
        );
    }
}
