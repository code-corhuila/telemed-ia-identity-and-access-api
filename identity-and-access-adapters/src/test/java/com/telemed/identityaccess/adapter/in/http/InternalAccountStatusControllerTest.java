package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccountNotFoundException;
import com.telemed.identityaccess.application.port.in.ChangeProfessionalAccountStatusUseCase;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Internal only: the onboarding saga activates the professional account or deactivates it to compensate. */
@WebMvcTest(
        controllers = InternalAccountStatusController.class,
        properties = "security.account-status.clients=workflow"
)
@ContextConfiguration(classes = {
        InternalAccountStatusController.class,
        ServiceCallerFilter.class,
        ApiErrorWriter.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class InternalAccountStatusControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChangeProfessionalAccountStatusUseCase changeStatus;

    @MockBean
    private ServiceTokenVerifierPort serviceTokens;

    @BeforeEach
    void callers() {
        when(serviceTokens.verify("workflow-token"))
                .thenReturn(new ServiceCaller("workflow", "SERVICE"));
        when(serviceTokens.verify("professional-management-token"))
                .thenReturn(new ServiceCaller("professional-management", "SERVICE"));
        when(serviceTokens.verify("admin-token"))
                .thenReturn(new ServiceCaller(ACCOUNT_ID.toString(), "ADMIN"));
    }

    @Test
    void activatesTheAccountWithoutABody() throws Exception {
        mockMvc.perform(statusRequest(ACCOUNT_ID.toString(), "{\"active\":true}", "workflow-token"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(changeStatus).change(ACCOUNT_ID, true);
    }

    @Test
    void answersNotFoundForAnAccountThatIsNotAProfessional() throws Exception {
        doThrow(new AccountNotFoundException()).when(changeStatus).change(ACCOUNT_ID, false);

        mockMvc.perform(statusRequest(ACCOUNT_ID.toString(), "{\"active\":false}", "workflow-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void rejectsAMissingActiveAndAnIdThatIsNotAUuid() throws Exception {
        mockMvc.perform(statusRequest(ACCOUNT_ID.toString(), "{}", "workflow-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(statusRequest("not-a-uuid", "{\"active\":true}", "workflow-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("id"));

        verify(changeStatus, never()).change(any(), anyBoolean());
    }

    @Test
    void needsAToken() throws Exception {
        mockMvc.perform(put("/internal/accounts/" + ACCOUNT_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbidsUsersAndOtherServices() throws Exception {
        for (String token : List.of("admin-token", "professional-management-token")) {
            mockMvc.perform(statusRequest(ACCOUNT_ID.toString(), "{\"active\":true}", token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        verify(changeStatus, never()).change(any(), anyBoolean());
    }

    private static MockHttpServletRequestBuilder statusRequest(String id, String body, String token) {
        return put("/internal/accounts/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
