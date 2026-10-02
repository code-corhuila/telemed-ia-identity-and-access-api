package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.PasswordRecoveryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordRecoveryController.class)
@ContextConfiguration(classes = {
        PasswordRecoveryController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class PasswordRecoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasswordRecoveryUseCase passwordRecoveryUseCase;

    @Test
    void shouldAcceptPasswordRecoveryRequest()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/password-recovery")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": "patient@example.com"
                                        }
                                        """)
                )
                .andExpect(
                        status().isAccepted()
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "If the email is registered, password recovery instructions will be sent."
                                )
                );

        verify(passwordRecoveryUseCase)
                .requestRecovery(
                        "patient@example.com"
                );
    }

    @Test
    void shouldReturnSameResponseForUnknownEmail()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/password-recovery")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": "unknown@example.com"
                                        }
                                        """)
                )
                .andExpect(
                        status().isAccepted()
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "If the email is registered, password recovery instructions will be sent."
                                )
                );

        verify(passwordRecoveryUseCase)
                .requestRecovery(
                        "unknown@example.com"
                );
    }

    @Test
    void shouldRejectBlankEmailBeforeCallingUseCase()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/password-recovery")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": ""
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value(
                                        "VALIDATION_ERROR"
                                )
                );

        verify(passwordRecoveryUseCase, never())
                .requestRecovery(
                        anyString()
                );
    }

    @Test
    void shouldRejectInvalidEmailFormat()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/password-recovery")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": "not-an-email"
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(passwordRecoveryUseCase, never())
                .requestRecovery(
                        anyString()
                );
    }

    @Test
    void shouldPreserveCorrelationIdForValidationError()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/password-recovery")
                                .header(
                                        "X-Correlation-Id",
                                        "recovery-request-123"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": ""
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.traceId")
                                .value(
                                        "recovery-request-123"
                                )
                );
    }
}