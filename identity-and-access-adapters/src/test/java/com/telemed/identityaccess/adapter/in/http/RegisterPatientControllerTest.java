package com.telemed.identityaccess.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.identityaccess.application.port.in.RegisterPatientUseCase;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegisterPatientController.class)
@ContextConfiguration(classes = {
        RegisterPatientController.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilter.class
})
class RegisterPatientControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "33333333-3333-4333-8333-333333333333"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisterPatientUseCase registerPatientUseCase;

    @Test
    void shouldReturnCreatedPatientWithUuid()
            throws Exception {

        when(registerPatientUseCase.register(any()))
                .thenReturn(
                        new RegisterPatientUseCase.Result(
                                USER_ID,
                                Role.PATIENT
                        )
                );

        RegisterPatientRequest request =
                new RegisterPatientRequest(
                        "Maria Patient",
                        "patient@example.com",
                        "123456789",
                        "Secret123!"
                );

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.userId")
                                .value(USER_ID.toString())
                )
                .andExpect(
                        jsonPath("$.role")
                                .value("PATIENT")
                );

        verify(registerPatientUseCase)
                .register(any());
    }

    @Test
    void shouldRejectPasswordShorterThanEightCharacters()
            throws Exception {

        String payload = """
                {
                  "fullName": "Maria Patient",
                  "email": "patient@example.com",
                  "identityDocument": "123456789",
                  "password": "Ab1!xyz"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.error")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Request validation failed.")
                )
                .andExpect(
                        jsonPath("$.details[0].field")
                                .value("password")
                )
                .andExpect(
                        jsonPath("$.details[0].message")
                                .value(
                                        "Password must contain between 8 and 72 characters."
                                )
                );

        verify(registerPatientUseCase, never())
                .register(any());
    }

    @Test
    void shouldRejectPasswordLongerThanSeventyTwoCharacters()
            throws Exception {

        String oversizedPassword = "A".repeat(73);

        RegisterPatientRequest request =
                new RegisterPatientRequest(
                        "Maria Patient",
                        "patient@example.com",
                        "123456789",
                        oversizedPassword
                );

        mockMvc.perform(
                        post("/api/v1/auth/register")
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
                )
                .andExpect(
                        jsonPath("$.details[0].field")
                                .value("password")
                )
                .andExpect(
                        jsonPath("$.details[0].message")
                                .value(
                                        "Password must contain between 8 and 72 characters."
                                )
                );

        verify(registerPatientUseCase, never())
                .register(any());
    }

    @Test
    void shouldReuseCorrelationIdForValidationError()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .header(
                                        "X-Correlation-Id",
                                        "register-request-123"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fullName": "Maria Patient",
                                          "email": "patient@example.com",
                                          "identityDocument": "123456789",
                                          "password": "short"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                "register-request-123"
                        )
                )
                .andExpect(
                        jsonPath("$.traceId")
                                .value("register-request-123")
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.details")
                                .isArray()
                );

        verify(registerPatientUseCase, never())
                .register(any());

        assertNull(
                org.slf4j.MDC.get(
                        CorrelationContext.MDC_KEY
                )
        );
    }

    @Test
    void shouldGenerateCorrelationIdForInvalidRegistration()
            throws Exception {

        var result = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fullName": "Maria Patient",
                                          "email": "invalid-email",
                                          "identityDocument": "123456789",
                                          "password": "Secret123!"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.error")
                                .value("VALIDATION_ERROR")
                )
                .andReturn();

        String traceId =
                result.getResponse()
                        .getHeader("X-Correlation-Id");

        assertNotNull(traceId);

        assertEquals(
                traceId,
                UUID.fromString(traceId).toString()
        );

        assertEquals(
                traceId,
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString()
                ).get("traceId").asText()
        );

        verify(registerPatientUseCase, never())
                .register(any());

        assertNull(
                org.slf4j.MDC.get(
                        CorrelationContext.MDC_KEY
                )
        );
    }
}