package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase;
import com.telemed.identityaccess.application.port.in.LookupAccountNamesUseCase.AccountName;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort.ServiceCaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DEC-014: document generation reads the names for the patient's PDF; nobody else may. */
@WebMvcTest(
        controllers = InternalAccountNamesController.class,
        properties = "security.account-names.clients=document-generation"
)
@ContextConfiguration(classes = {
        InternalAccountNamesController.class,
        ServiceCallerFilter.class,
        ApiErrorWriter.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class InternalAccountNamesControllerTest {

    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DOCTOR_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LookupAccountNamesUseCase lookupAccountNames;

    @MockBean
    private ServiceTokenVerifierPort serviceTokens;

    @BeforeEach
    void callers() {
        when(serviceTokens.verify("document-generation-token"))
                .thenReturn(new ServiceCaller("document-generation", "SERVICE"));
        when(serviceTokens.verify("notifications-token"))
                .thenReturn(new ServiceCaller("notifications", "SERVICE"));
        when(serviceTokens.verify("admin-token"))
                .thenReturn(new ServiceCaller(DOCTOR_ID.toString(), "ADMIN"));
    }

    @Test
    void answersTheNamesOfThePatientAndTheProfessional() throws Exception {
        when(lookupAccountNames.lookupForService(Set.of(PATIENT_ID, DOCTOR_ID))).thenReturn(List.of(
                new AccountName(PATIENT_ID, "Laura Gómez"),
                new AccountName(DOCTOR_ID, "Andrés Molina")));

        mockMvc.perform(get(InternalAccountNamesController.PATH)
                        .header("Authorization", "Bearer document-generation-token")
                        .param("ids", PATIENT_ID + "," + DOCTOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data[0].fullName").value("Laura Gómez"))
                .andExpect(jsonPath("$.data[1].fullName").value("Andrés Molina"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    void rejectsIdsThatAreNotUuids() throws Exception {
        mockMvc.perform(get(InternalAccountNamesController.PATH)
                        .header("Authorization", "Bearer document-generation-token")
                        .param("ids", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("ids"));

        verify(lookupAccountNames, never()).lookupForService(any());
    }

    @Test
    void needsAToken() throws Exception {
        mockMvc.perform(get(InternalAccountNamesController.PATH).param("ids", PATIENT_ID.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void forbidsUsersAndOtherServices() throws Exception {
        for (String token : List.of("admin-token", "notifications-token")) {
            mockMvc.perform(get(InternalAccountNamesController.PATH)
                            .header("Authorization", "Bearer " + token)
                            .param("ids", PATIENT_ID.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        verify(lookupAccountNames, never()).lookupForService(any());
    }
}
