package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.ServiceAuthenticationException;
import com.telemed.identityaccess.application.port.in.IssueServiceTokenUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Internal only: the gateway does not route /internal, services call identity-api inside the network. */
@WebMvcTest(ServiceTokenController.class)
@ContextConfiguration(classes = {
        ServiceTokenController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class ServiceTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IssueServiceTokenUseCase issueServiceToken;

    @Test
    void issuesAServiceTokenForValidClientCredentials() throws Exception {
        when(issueServiceToken.issue("document-generation", "right-secret"))
                .thenReturn(new IssueServiceTokenUseCase.Result("signed-service-token",
                        Instant.parse("2026-10-23T15:10:00Z")));

        mockMvc.perform(post("/internal/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"document-generation\",\"clientSecret\":\"right-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-service-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-23T15:10:00Z"));
    }

    @Test
    void answersAGenericInvalidClientWhateverFailed() throws Exception {
        when(issueServiceToken.issue("document-generation", "wrong"))
                .thenThrow(new ServiceAuthenticationException());

        mockMvc.perform(post("/internal/service-token")
                        .header("X-Correlation-Id", "trace-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"document-generation\",\"clientSecret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CLIENT"))
                .andExpect(jsonPath("$.traceId").value("trace-0001"));
    }

    @Test
    void answersBadRequestForAMalformedBody() throws Exception {
        mockMvc.perform(post("/internal/service-token")
                        .header("X-Correlation-Id", "trace-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").value("trace-0002"));
    }

    @Test
    void needsBothCredentials() throws Exception {
        mockMvc.perform(post("/internal/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"document-generation\"}"))
                .andExpect(status().isBadRequest());
    }
}
