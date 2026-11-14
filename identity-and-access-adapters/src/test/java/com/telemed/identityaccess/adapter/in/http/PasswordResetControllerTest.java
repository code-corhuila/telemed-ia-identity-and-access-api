package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.PasswordResetException;
import com.telemed.identityaccess.application.port.in.PasswordResetUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordResetController.class)
@ContextConfiguration(classes = {
        PasswordResetController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasswordResetUseCase passwordResetUseCase;

    @Test
    void shouldResetPasswordWithValidRequest()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/password-reset")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "token": "plain-reset-token",
                                          "newPassword": "NewSecret123!"
                                        }
                                        """)
                )
                .andExpect(
                        status().isNoContent()
                );

        verify(passwordResetUseCase)
                .reset(
                        "plain-reset-token",
                        "NewSecret123!"
                );
    }

    @Test
    void shouldRejectBlankToken()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/password-reset")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "token": "",
                                          "newPassword": "NewSecret123!"
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("VALIDATION_ERROR")
                );

        verify(passwordResetUseCase, never())
                .reset(
                        anyString(),
                        anyString()
                );
    }

    @Test
    void shouldRejectBlankNewPassword()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/password-reset")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "token": "plain-reset-token",
                                          "newPassword": ""
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(passwordResetUseCase, never())
                .reset(
                        anyString(),
                        anyString()
                );
    }

    @Test
    void shouldReturnUnauthorizedForInvalidResetToken()
            throws Exception {

        doThrow(
                PasswordResetException.invalidResetToken()
        ).when(passwordResetUseCase)
                .reset(
                        "invalid-reset-token",
                        "NewSecret123!"
                );

        mockMvc.perform(
                        post("/api/v1/auth/password-reset")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "token": "invalid-reset-token",
                                          "newPassword": "NewSecret123!"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("INVALID_RESET_TOKEN")
                );
    }

    @Test
    void shouldPreserveCorrelationIdForInvalidResetToken()
            throws Exception {

        doThrow(
                PasswordResetException.invalidResetToken()
        ).when(passwordResetUseCase)
                .reset(
                        "invalid-reset-token",
                        "NewSecret123!"
                );

        mockMvc.perform(
                        post("/api/v1/auth/password-reset")
                                .header(
                                        "X-Correlation-Id",
                                        "password-reset-123"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "token": "invalid-reset-token",
                                          "newPassword": "NewSecret123!"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.traceId")
                                .value("password-reset-123")
                );
    }
}