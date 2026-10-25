package com.telemed.identityaccess.app.config;

import com.telemed.identityaccess.application.port.in.BootstrapAdminUseCase;
import com.telemed.identityaccess.application.port.in.BootstrapAdminUseCase.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminRunnerTest {

    private static final String PASSWORD = "Admin-Secret-1";

    private final BootstrapAdminUseCase useCase =
            mock(BootstrapAdminUseCase.class);

    @Test
    void doesNothingWhenNoVariableIsSet() throws Exception {

        runner(new BootstrapAdminProperties(null, "", " ", null)).run(null);

        verify(useCase, never()).bootstrap(any());
    }

    @Test
    void refusesToStartWhenOnlySomeVariablesAreSet(CapturedOutput output) {

        assertThatThrownBy(() -> runner(new BootstrapAdminProperties(
                "Platform Admin", "admin@telemed.test", "", PASSWORD
        )).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_IDENTITY_DOCUMENT")
                .hasMessageNotContaining(PASSWORD);

        verify(useCase, never()).bootstrap(any());
        assertThat(output).doesNotContain(PASSWORD);
    }

    @Test
    void refusesToStartWhenThePasswordBreaksThePolicy() {

        assertThatThrownBy(() -> runner(new BootstrapAdminProperties(
                "Platform Admin", "admin@telemed.test", "ADM-0001", "short"
        )).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 8 characters");
    }

    @Test
    void logsTheOutcomeWithoutThePassword(CapturedOutput output) throws Exception {

        when(useCase.bootstrap(any())).thenReturn(Outcome.CREATED);

        runner(complete()).run(null);

        assertThat(output).contains("bootstrap admin created");
        assertThat(output).doesNotContain(PASSWORD);
    }

    @Test
    void reportsAnExistingAdmin(CapturedOutput output) throws Exception {

        when(useCase.bootstrap(any())).thenReturn(Outcome.ALREADY_PRESENT);

        runner(complete()).run(null);

        assertThat(output).contains("bootstrap admin already present");
    }

    @Test
    void warnsWhenTheAccountDataBelongsToAnotherUser(CapturedOutput output) throws Exception {

        when(useCase.bootstrap(any())).thenReturn(Outcome.ACCOUNT_IN_USE);

        runner(complete()).run(null);

        assertThat(output).contains("WARN").contains("bootstrap admin skipped");
    }

    @Test
    void neverPrintsThePasswordOfTheProperties() {

        assertThat(complete().toString()).doesNotContain(PASSWORD);
    }

    private BootstrapAdminRunner runner(BootstrapAdminProperties properties) {
        return new BootstrapAdminRunner(properties, useCase);
    }

    private static BootstrapAdminProperties complete() {
        return new BootstrapAdminProperties(
                "Platform Admin", "admin@telemed.test", "ADM-0001", PASSWORD
        );
    }
}
