package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.exception.AccountProvisioningException;
import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.ProvisionProfessionalAccountUseCase;
import com.telemed.identityaccess.application.port.in.ProvisionProfessionalAccountUseCase.Result;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort.ServiceCaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.INVALID_SIGNATURE;
import static com.telemed.identityaccess.application.exception.AccountProvisioningException.Reason.IDEMPOTENCY_KEY_REUSED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Internal only: professional management creates the account of a professional it registers. */
@WebMvcTest(
        controllers = InternalAccountsController.class,
        properties = "security.account-provisioning.clients=professional-management"
)
@ContextConfiguration(classes = {
        InternalAccountsController.class,
        ServiceCallerFilter.class,
        ApiErrorWriter.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class InternalAccountsControllerTest {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final String BODY = """
            {"fullName":"Doctor Name","email":"doctor@telemed.test","identityDocument":"DOC-0001",
             "password":"Doctor-Secret-1","role":"PROFESSIONAL"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProvisionProfessionalAccountUseCase provisionAccount;

    @MockBean
    private ServiceTokenVerifierPort serviceTokens;

    @BeforeEach
    void callers() {
        when(serviceTokens.verify("professional-management-token"))
                .thenReturn(new ServiceCaller("professional-management", "SERVICE"));
        when(serviceTokens.verify("document-generation-token"))
                .thenReturn(new ServiceCaller("document-generation", "SERVICE"));
        when(serviceTokens.verify("patient-token"))
                .thenReturn(new ServiceCaller("11111111-1111-4111-8111-111111111111", "PATIENT"));
        when(serviceTokens.verify("forged-token"))
                .thenThrow(new AccessTokenVerificationException(INVALID_SIGNATURE, "Access token signature is invalid."));
    }

    @Test
    void createsTheAccountAndAnswersItsId() throws Exception {
        when(provisionAccount.provision(any())).thenReturn(new Result(ACCOUNT_ID, false));

        provision("professional-management-token", "register-0001", BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
                .andExpect(content().string(not(containsString("Doctor-Secret-1"))));
    }

    @Test
    void answersTheSameAccountForARetry() throws Exception {
        when(provisionAccount.provision(any())).thenReturn(new Result(ACCOUNT_ID, true));

        provision("professional-management-token", "register-0001", BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()));
    }

    @Test
    void needsAToken() throws Exception {
        mockMvc.perform(post("/internal/accounts")
                        .header("Idempotency-Key", "register-0001")
                        .header("X-Correlation-Id", "trace-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("trace-0001"));

        verify(provisionAccount, never()).provision(any());
    }

    @Test
    void checksTheCallerBeforeTheBody() throws Exception {
        mockMvc.perform(post("/internal/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnInvalidToken() throws Exception {
        provision("forged-token", "register-0001", BODY)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void forbidsUsersAndOtherServices() throws Exception {
        provision("patient-token", "register-0001", BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        provision("document-generation-token", "register-0001", BODY)
                .andExpect(status().isForbidden());

        verify(provisionAccount, never()).provision(any());
    }

    @Test
    void validatesEveryField() throws Exception {
        provision("professional-management-token", "register-0001", """
                {"fullName":"Doctor Name","email":"not-an-email","identityDocument":"DOC-0001",
                 "password":"Doctor-Secret-1","role":"ADMIN"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field == 'email')].message").value("Email format is invalid."))
                .andExpect(jsonPath("$.details[?(@.field == 'role')].message")
                        .value("Only PROFESSIONAL accounts can be provisioned."));
    }

    @Test
    void needsAValidIdempotencyKey() throws Exception {
        provision("professional-management-token", "short", BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("Idempotency-Key"));
    }

    @Test
    void rejectsAKeyReusedForAnotherRequest() throws Exception {
        when(provisionAccount.provision(any())).thenThrow(new AccountProvisioningException(
                IDEMPOTENCY_KEY_REUSED, "Idempotency-Key was already used for a different request."));

        provision("professional-management-token", "register-0001", BODY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void answersConflictForAnExistingEmail() throws Exception {
        when(provisionAccount.provision(any())).thenThrow(new RegistrationException(
                EMAIL_ALREADY_REGISTERED, "Email is already registered."));

        provision("professional-management-token", "register-0001", BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("REGISTRATION_CONFLICT"));
    }

    private ResultActions provision(String token, String idempotencyKey, String body) throws Exception {
        return mockMvc.perform(post("/internal/accounts")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
