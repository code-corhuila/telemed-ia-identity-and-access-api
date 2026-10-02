package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.LogoutException;
import com.telemed.identityaccess.application.port.in.LogoutUseCase;
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

@WebMvcTest(LogoutController.class)
@ContextConfiguration(classes = {
        LogoutController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class LogoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogoutUseCase logoutUseCase;

    @Test
    void shouldLogoutWithValidRefreshToken()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "current-refresh-token"
                                        }
                                        """)
                )
                .andExpect(
                        status().isNoContent()
                );

        verify(logoutUseCase)
                .logout(
                        "current-refresh-token"
                );
    }

    @Test
    void shouldReturnUnauthorizedForInvalidRefreshToken()
            throws Exception {

        doThrow(
                LogoutException.invalidRefreshToken()
        ).when(logoutUseCase)
                .logout(
                        "invalid-refresh-token"
                );

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "invalid-refresh-token"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value(
                                        "INVALID_REFRESH_TOKEN"
                                )
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Refresh token is invalid."
                                )
                );
    }

    @Test
    void shouldRejectBlankRefreshTokenBeforeCallingUseCase()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": ""
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

        verify(logoutUseCase, never())
                .logout(
                        anyString()
                );
    }

    @Test
    void shouldPreserveCorrelationIdForInvalidLogout()
            throws Exception {

        doThrow(
                LogoutException.invalidRefreshToken()
        ).when(logoutUseCase)
                .logout(
                        "invalid-refresh-token"
                );

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header(
                                        "X-Correlation-Id",
                                        "logout-request-123"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "invalid-refresh-token"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.traceId")
                                .value(
                                        "logout-request-123"
                                )
                );
    }
}