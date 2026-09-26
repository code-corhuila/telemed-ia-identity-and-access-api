package com.telemed.identityaccess.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import(GlobalExceptionHandler.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoginUseCase loginUseCase;

    @Test
    void shouldReturnAccessTokenForValidCredentials()
            throws Exception {

        Instant expiresAt =
                Instant.parse("2026-09-26T00:00:00Z");

        when(loginUseCase.login(any()))
                .thenReturn(
                        new LoginUseCase.Result(
                                10L,
                                Role.PATIENT,
                                "signed-access-token",
                                expiresAt
                        )
                );

        LoginRequest request = new LoginRequest(
                "patient@example.com",
                "StrongPassword123!"
        );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(
                        jsonPath("$.accessToken")
                                .value("signed-access-token")
                )
                .andExpect(
                        jsonPath("$.tokenType")
                                .value("Bearer")
                )
                .andExpect(
                        jsonPath("$.expiresAt")
                                .value(
                                        "2026-09-26T00:00:00Z"
                                )
                );

        verify(loginUseCase).login(any());
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCredentials()
            throws Exception {

        when(loginUseCase.login(any()))
                .thenThrow(
                        AuthenticationException
                                .invalidCredentials()
                );

        LoginRequest request = new LoginRequest(
                "patient@example.com",
                "WrongPassword"
        );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_CREDENTIALS")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Invalid email or password."
                                )
                );
    }

    @Test
void shouldDelegateLegacyLengthPasswordToUseCase()
        throws Exception {

    when(loginUseCase.login(any()))
            .thenThrow(
                    AuthenticationException
                            .invalidCredentials()
            );

    String payload = """
            {
              "email": "patient@example.com",
              "password": "old123"
            }
            """;

    mockMvc.perform(
                    post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                    jsonPath("$.code")
                            .value("INVALID_CREDENTIALS")
            );

    verify(loginUseCase).login(any());
}

    @Test
    void shouldRejectInvalidEmailBeforeCallingUseCase()
            throws Exception {

        LoginRequest request = new LoginRequest(
                "invalid-email",
                "StrongPassword123!"
        );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                );

        verify(loginUseCase, never())
                .login(any());
    }

    @Test
    void shouldRejectBlankPasswordBeforeCallingUseCase()
            throws Exception {

        String payload = """
                {
                  "email": "patient@example.com",
                  "password": ""
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                );

        verify(loginUseCase, never())
                .login(any());
    }
}