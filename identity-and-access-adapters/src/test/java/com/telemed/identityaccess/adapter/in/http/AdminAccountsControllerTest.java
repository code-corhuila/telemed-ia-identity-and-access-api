package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.exception.AccountStatusConflictException;
import com.telemed.identityaccess.application.exception.AdministratorRequiredException;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountPage;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.AccountView;
import com.telemed.identityaccess.application.port.in.BrowseAccountsUseCase.Query;
import com.telemed.identityaccess.application.port.in.ChangeAccountStatusUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort.VerifiedAccessToken;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.EXPIRED;
import static com.telemed.identityaccess.application.exception.AccountStatusConflictException.Reason.SELF_DEACTIVATION;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-012 / HU-05 AC4 and AC5: only an administrator lists and views the user accounts. */
@WebMvcTest(AdminAccountsController.class)
@ContextConfiguration(classes = {
        AdminAccountsController.class,
        AuthFilter.class,
        ApiErrorWriter.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class AdminAccountsControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static final UUID ACCOUNT_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BrowseAccountsUseCase browseAccounts;

    @MockBean
    private ChangeAccountStatusUseCase changeAccountStatus;

    @MockBean
    private AccessTokenVerifierPort accessTokens;

    @BeforeEach
    void tokens() {
        Instant expiresAt = Instant.parse("2026-10-24T18:00:00Z");
        when(accessTokens.verify("admin-token"))
                .thenReturn(new VerifiedAccessToken(ADMIN_ID, Role.ADMIN, "jti-admin", expiresAt));
        when(accessTokens.verify("patient-token"))
                .thenReturn(new VerifiedAccessToken(ACCOUNT_ID, Role.PATIENT, "jti-patient", expiresAt));
        when(accessTokens.verify("expired-token"))
                .thenThrow(new AccessTokenVerificationException(EXPIRED, "Access token has expired."));
    }

    @Test
    void listsAccountsWithTheContractPagination() throws Exception {
        when(browseAccounts.browse(new Query("doctor", Role.PROFESSIONAL, true, 2, 10)))
                .thenReturn(new AccountPage(List.of(account()), 2, 10, 11));

        mockMvc.perform(get("/api/v1/auth/admin/accounts")
                        .header("Authorization", "Bearer admin-token")
                        .param("q", "doctor")
                        .param("role", "PROFESSIONAL")
                        .param("active", "true")
                        .param("page", "2")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.data[0].fullName").value("Doctor Name"))
                .andExpect(jsonPath("$.data[0].email").value("doctor@telemed.test"))
                .andExpect(jsonPath("$.data[0].identityDocument").value("DOC-0001"))
                .andExpect(jsonPath("$.data[0].role").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].registrationDate").value("2026-10-20T14:00:00Z"))
                .andExpect(jsonPath("$.data[0].lastAccess").value("2026-10-24T13:30:00Z"))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.limit").value(10))
                .andExpect(jsonPath("$.meta.total").value(11))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void usesTheFirstPageOfTwentyByDefault() throws Exception {
        when(browseAccounts.browse(new Query(null, null, null, 1, 20)))
                .thenReturn(new AccountPage(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/auth/admin/accounts")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void rejectsInvalidQueryParameters() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/accounts")
                        .header("Authorization", "Bearer admin-token")
                        .param("limit", "101")
                        .param("page", "zero")
                        .param("role", "ROOT")
                        .param("active", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field == 'limit')].message")
                        .value("limit must be between 1 and 100."))
                .andExpect(jsonPath("$.details[?(@.field == 'page')].message")
                        .value("page must be a number from 1."))
                .andExpect(jsonPath("$.details[?(@.field == 'role')].message")
                        .value("role must be PATIENT, PROFESSIONAL or ADMIN."))
                .andExpect(jsonPath("$.details[?(@.field == 'active')].message")
                        .value("active must be true or false."));

        verify(browseAccounts, never()).browse(any());
    }

    @Test
    void showsOneAccount() throws Exception {
        when(browseAccounts.view(ACCOUNT_ID)).thenReturn(account());

        mockMvc.perform(get("/api/v1/auth/admin/accounts/" + ACCOUNT_ID)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.role").value("PROFESSIONAL"));
    }

    @Test
    void answersNotFoundForAnUnknownOrMalformedId() throws Exception {
        when(browseAccounts.view(ACCOUNT_ID)).thenThrow(new AccountNotFoundException());

        mockMvc.perform(get("/api/v1/auth/admin/accounts/" + ACCOUNT_ID)
                        .header("Authorization", "Bearer admin-token")
                        .header("X-Correlation-Id", "trace-0404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-0404"));

        mockMvc.perform(get("/api/v1/auth/admin/accounts/not-a-uuid")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void needsAValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/auth/admin/accounts")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbidsEveryRoleButAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/accounts")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/auth/admin/accounts/" + ACCOUNT_ID)
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isForbidden());

        verify(browseAccounts, never()).browse(any());
        verify(browseAccounts, never()).view(any());
    }

    @Test
    void changesTheStatusAsTheSignedInAdmin() throws Exception {
        when(changeAccountStatus.change(new ChangeAccountStatusUseCase.Command(ADMIN_ID, ACCOUNT_ID, false)))
                .thenReturn(account());

        mockMvc.perform(put("/api/v1/auth/admin/accounts/" + ACCOUNT_ID + "/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()));
    }

    @Test
    void needsTheNewStatus() throws Exception {
        mockMvc.perform(put("/api/v1/auth/admin/accounts/" + ACCOUNT_ID + "/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("active"));

        verify(changeAccountStatus, never()).change(any());
    }

    @Test
    void answersConflictWhenTheChangeIsNotAllowed() throws Exception {
        when(changeAccountStatus.change(any())).thenThrow(new AccountStatusConflictException(
                SELF_DEACTIVATION, "Administrators cannot deactivate their own account."));

        mockMvc.perform(put("/api/v1/auth/admin/accounts/" + ADMIN_ID + "/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ACCOUNT_STATUS_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Administrators cannot deactivate their own account."));
    }

    @Test
    void forbidsAnAdminWhoseAccountIsNoLongerActive() throws Exception {
        when(changeAccountStatus.change(any())).thenThrow(new AdministratorRequiredException());

        mockMvc.perform(put("/api/v1/auth/admin/accounts/" + ACCOUNT_ID + "/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void forbidsAPatientToChangeAStatus() throws Exception {
        mockMvc.perform(put("/api/v1/auth/admin/accounts/" + ACCOUNT_ID + "/status")
                        .header("Authorization", "Bearer patient-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        verify(changeAccountStatus, never()).change(any());
    }

    @Test
    void answersNotFoundForAMalformedIdOnStatus() throws Exception {
        mockMvc.perform(put("/api/v1/auth/admin/accounts/not-a-uuid/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isNotFound());
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
                Instant.parse("2026-10-24T13:30:00Z")
        );
    }
}
