package com.telemed.identityaccess.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@ContextConfiguration(classes = {
        LoginController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
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
                        jsonPath("$.error")
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
                        jsonPath("$.error")
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
                        jsonPath("$.error")
                                .value("VALIDATION_ERROR")
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
                        jsonPath("$.error")
                                .value("VALIDATION_ERROR")
                );

        verify(loginUseCase, never())
                .login(any());
    }

    @Test
    void shouldReuseCorrelationIdInErrorResponse() throws Exception {
        when(loginUseCase.login(any()))
                .thenThrow(AuthenticationException.invalidCredentials());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Correlation-Id", "request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "patient@example.com",
                                  "password": "WrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertEquals(
                        "request-123",
                        result.getResponse().getHeader("X-Correlation-Id")))
                .andExpect(jsonPath("$.traceId").value("request-123"))
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        assertNull(
        org.slf4j.MDC.get(CorrelationContext.MDC_KEY)
);
    }

    @Test
    void shouldGenerateCorrelationIdAndReturnValidationDetails()
            throws Exception {

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("email"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty())
                .andReturn();

        String traceId =
                result.getResponse().getHeader("X-Correlation-Id");

        assertNotNull(traceId);
        assertEquals(
                traceId,
                java.util.UUID.fromString(traceId).toString()
        );
        assertEquals(
                traceId,
                objectMapper.readTree(
                        result.getResponse().getContentAsString()
                ).get("traceId").asText()
        );
        assertNull(
        org.slf4j.MDC.get(CorrelationContext.MDC_KEY)
);

        verify(loginUseCase, never())
                .login(any());
    }

    @Test
    void shouldReplaceInvalidCorrelationId() throws Exception {

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Correlation-Id", "invalid id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "patient@example.com",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        String traceId =
                result.getResponse().getHeader("X-Correlation-Id");

        assertNotNull(traceId);
        assertEquals(
                traceId,
                java.util.UUID.fromString(traceId).toString()
        );
        assertEquals(
                traceId,
                objectMapper.readTree(
                        result.getResponse().getContentAsString()
                ).get("traceId").asText()
        );

        verify(loginUseCase, never())
                .login(any());
    }
}