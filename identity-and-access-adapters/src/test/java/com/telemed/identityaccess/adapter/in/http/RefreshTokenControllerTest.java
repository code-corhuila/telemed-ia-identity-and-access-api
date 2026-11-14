package com.telemed.identityaccess.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.application.exception.RefreshSessionException;
import com.telemed.identityaccess.application.port.in.RefreshSessionUseCase;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RefreshTokenController.class)
@ContextConfiguration(classes = {
        RefreshTokenController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class RefreshTokenControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefreshSessionUseCase refreshSessionUseCase;

    @Test
    void shouldReturnRotatedTokensForValidRefreshToken()
            throws Exception {

        when(refreshSessionUseCase.refresh(
                "current-refresh-token"
        )).thenReturn(
                new RefreshSessionUseCase.Result(
                        USER_ID,
                        Role.PATIENT,
                        "new-access-token",
                        Instant.parse(
                                "2026-10-01T20:00:00Z"
                        ),
                        "new-refresh-token",
                        Instant.parse(
                                "2026-10-08T19:00:00Z"
                        )
                )
        );

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        "current-refresh-token"
                );

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.userId")
                                .value(USER_ID.toString())
                )
                .andExpect(
                        jsonPath("$.role")
                                .value("PATIENT")
                )
                .andExpect(
                        jsonPath("$.accessToken")
                                .value("new-access-token")
                )
                .andExpect(
                        jsonPath("$.tokenType")
                                .value("Bearer")
                )
                .andExpect(
                        jsonPath("$.expiresAt")
                                .value(
                                        "2026-10-01T20:00:00Z"
                                )
                )
                .andExpect(
                        jsonPath("$.refreshToken")
                                .value("new-refresh-token")
                )
                .andExpect(
                        jsonPath("$.refreshExpiresAt")
                                .value(
                                        "2026-10-08T19:00:00Z"
                                )
                );

        verify(refreshSessionUseCase)
                .refresh(
                        "current-refresh-token"
                );
    }

    @Test
    void shouldReturnUnauthorizedForInvalidRefreshToken()
            throws Exception {

        when(refreshSessionUseCase.refresh(
                "invalid-refresh-token"
        )).thenThrow(
                RefreshSessionException
                        .invalidRefreshToken()
        );

        RefreshTokenRequest request =
                new RefreshTokenRequest(
                        "invalid-refresh-token"
                );

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.error")
                                .value(
                                        "INVALID_REFRESH_TOKEN"
                                )
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Refresh token is invalid or expired."
                                )
                );
    }

    @Test
    void shouldRejectBlankRefreshTokenBeforeCallingUseCase()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.error")
                                .value("VALIDATION_ERROR")
                );

        verify(refreshSessionUseCase, never())
                .refresh(anyString());
    }

    @Test
    void shouldPreserveCorrelationIdForInvalidRefreshToken()
            throws Exception {

        when(refreshSessionUseCase.refresh(
                "invalid-refresh-token"
        )).thenThrow(
                RefreshSessionException
                        .invalidRefreshToken()
        );

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .header(
                                        "X-Correlation-Id",
                                        "refresh-request-123"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken":
                                            "invalid-refresh-token"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.traceId")
                                .value(
                                        "refresh-request-123"
                                )
                );
    }
}