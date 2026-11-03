package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.adapter.CapturedLogs;
import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.application.port.in.PasswordRecoveryUseCase;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Week 11 log contract (CP2): what happened in the HU-02/HU-03 journeys, never who typed what. */
@WebMvcTest({LoginController.class, PasswordRecoveryController.class})
@ContextConfiguration(classes = {
        LoginController.class,
        PasswordRecoveryController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class AuthLogEventsTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String LOGIN = "{\"email\":\"patient@example.com\",\"password\":\"StrongPassword123!\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoginUseCase loginUseCase;

    @MockBean
    private PasswordRecoveryUseCase passwordRecoveryUseCase;

    @Test
    void logsASuccessfulLoginWithTheAccountIdOnly() throws Exception {
        when(loginUseCase.login(any())).thenReturn(new LoginUseCase.Result(USER_ID, Role.PATIENT,
                "signed-access-token", Instant.now(), "plain-refresh-token", Instant.now()));

        try (CapturedLogs logs = CapturedLogs.of(LoginController.class)) {
            postJson("/api/v1/auth/login", LOGIN).andExpect(status().isOk());

            assertThat(logs.events("auth.login.succeeded")).singleElement().satisfies(line ->
                    assertThat(line).containsEntry("level", "INFO")
                            .containsEntry("user_id", USER_ID.toString())
                            .containsEntry("role", "PATIENT"));
            assertThat(logs.everything()).doesNotContain("patient@example.com", "StrongPassword123!",
                    "signed-access-token", "plain-refresh-token");
        }
    }

    @Test
    void logsAFailedLoginWithItsReason() throws Exception {
        when(loginUseCase.login(any())).thenThrow(AuthenticationException.invalidCredentials());

        try (CapturedLogs logs = CapturedLogs.of(GlobalExceptionHandler.class)) {
            postJson("/api/v1/auth/login", LOGIN).andExpect(status().isUnauthorized());

            assertThat(logs.events("auth.login.failed")).singleElement().satisfies(line ->
                    assertThat(line).containsEntry("level", "WARN")
                            .containsEntry("reason", "INVALID_CREDENTIALS"));
            assertThat(logs.everything()).doesNotContain("patient@example.com", "StrongPassword123!");
        }
    }

    @Test
    void logsALockedAccountWithTheWaitTheClientGets() throws Exception {
        when(loginUseCase.login(any()))
                .thenThrow(AuthenticationException.accountTemporarilyLocked(Duration.ofSeconds(90)));

        try (CapturedLogs logs = CapturedLogs.of(GlobalExceptionHandler.class)) {
            postJson("/api/v1/auth/login", LOGIN).andExpect(status().isTooManyRequests());

            assertThat(logs.events("auth.login.locked")).singleElement().satisfies(line ->
                    assertThat(line).containsEntry("level", "WARN")
                            .containsEntry("retry_after_s", 90L));
        }
    }

    @Test
    void logsARecoveryRequestWithoutTheEmailOrWhetherItExists() throws Exception {
        try (CapturedLogs logs = CapturedLogs.of(PasswordRecoveryController.class)) {
            postJson("/api/v1/auth/password-recovery", "{\"email\":\"patient@example.com\"}")
                    .andExpect(status().isAccepted());

            assertThat(logs.events("password_recovery.requested")).singleElement().satisfies(line ->
                    assertThat(line).containsEntry("level", "INFO"));
            assertThat(logs.everything()).doesNotContain("patient@example.com");
        }
    }

    @Test
    void logsARejectedRequestWithTheFieldNamesButNotTheirValues() throws Exception {
        try (CapturedLogs logs = CapturedLogs.of(GlobalExceptionHandler.class)) {
            postJson("/api/v1/auth/login", "{\"email\":\"not-an-email\",\"password\":\"StrongPassword123!\"}")
                    .andExpect(status().isBadRequest());

            assertThat(logs.events("request.rejected")).singleElement().satisfies(line ->
                    assertThat(line).containsEntry("level", "INFO")
                            .containsEntry("error_code", "VALIDATION_ERROR")
                            .containsEntry("fields", "email"));
            assertThat(logs.everything()).doesNotContain("not-an-email", "StrongPassword123!");
        }
    }

    @Test
    void logsAnUnexpectedFailureAsAnError() throws Exception {
        doThrow(new IllegalStateException("Integration event could not be recorded."))
                .when(passwordRecoveryUseCase).requestRecovery(any());

        try (CapturedLogs logs = CapturedLogs.of(GlobalExceptionHandler.class)) {
            postJson("/api/v1/auth/password-recovery", "{\"email\":\"patient@example.com\"}")
                    .andExpect(status().isInternalServerError());

            assertThat(logs.events("request.failed")).singleElement().satisfies(line ->
                    assertThat(line).containsEntry("level", "ERROR")
                            .containsEntry("error_type", IllegalStateException.class.getName()));
        }
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
