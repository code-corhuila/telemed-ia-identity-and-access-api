package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.FindNotificationRecipientUseCase;
import com.telemed.identityaccess.application.port.in.FindNotificationRecipientUseCase.NotificationRecipient;
import com.telemed.identityaccess.application.port.in.LookupProfessionalAccountsUseCase;
import com.telemed.identityaccess.application.port.in.ProvisionProfessionalAccountUseCase;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Internal only: notifications reads the e-mail of the patient it writes to, nothing else. */
@WebMvcTest(
        controllers = {InternalNotificationRecipientsController.class, InternalAccountsController.class},
        properties = {
                "security.account-provisioning.clients=professional-management",
                "security.notification-recipients.clients=notifications"
        }
)
@ContextConfiguration(classes = {
        InternalNotificationRecipientsController.class,
        InternalAccountsController.class,
        ServiceCallerFilter.class,
        ApiErrorWriter.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class InternalNotificationRecipientsControllerTest {

    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FindNotificationRecipientUseCase findRecipient;

    @MockBean
    private ProvisionProfessionalAccountUseCase provisionAccount;

    @MockBean
    private LookupProfessionalAccountsUseCase lookupAccounts;

    @MockBean
    private ServiceTokenVerifierPort serviceTokens;

    @BeforeEach
    void callers() {
        when(serviceTokens.verify("notifications-token"))
                .thenReturn(new ServiceCaller("notifications", "SERVICE"));
        when(serviceTokens.verify("professional-management-token"))
                .thenReturn(new ServiceCaller("professional-management", "SERVICE"));
        when(serviceTokens.verify("patient-token"))
                .thenReturn(new ServiceCaller(PATIENT_ID.toString(), "PATIENT"));
    }

    @Test
    void answersOnlyTheIdAndEmailOfThePatient() throws Exception {
        when(findRecipient.find(PATIENT_ID)).thenReturn(new NotificationRecipient(PATIENT_ID, "laura@telemed.co"));

        mockMvc.perform(get("/internal/notification-recipients/" + PATIENT_ID)
                        .header("Authorization", "Bearer notifications-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.email").value("laura@telemed.co"))
                .andExpect(jsonPath("$.fullName").doesNotExist())
                .andExpect(jsonPath("$.identityDocument").doesNotExist());
    }

    @Test
    void answersNotFoundForAnAccountThatIsNotAnActivePatient() throws Exception {
        when(findRecipient.find(PATIENT_ID)).thenThrow(new AccountNotFoundException());

        mockMvc.perform(get("/internal/notification-recipients/" + PATIENT_ID)
                        .header("Authorization", "Bearer notifications-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void rejectsAnIdThatIsNotAUuid() throws Exception {
        mockMvc.perform(get("/internal/notification-recipients/not-a-uuid")
                        .header("Authorization", "Bearer notifications-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void needsAToken() throws Exception {
        mockMvc.perform(get("/internal/notification-recipients/" + PATIENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void forbidsUsersAndOtherServices() throws Exception {
        for (String token : List.of("patient-token", "professional-management-token")) {
            mockMvc.perform(get("/internal/notification-recipients/" + PATIENT_ID)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        verify(findRecipient, never()).find(any());
    }

    @Test
    void doesNotLetNotificationsUseTheAccountsEndpoints() throws Exception {
        mockMvc.perform(get("/internal/accounts")
                        .header("Authorization", "Bearer notifications-token")
                        .param("ids", PATIENT_ID.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/accounts")
                        .header("Authorization", "Bearer notifications-token")
                        .header("Idempotency-Key", "register-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(lookupAccounts, never()).lookup(any());
        verify(provisionAccount, never()).provision(any());
    }
}
