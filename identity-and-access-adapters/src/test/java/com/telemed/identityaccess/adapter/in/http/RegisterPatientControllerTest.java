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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void shouldReturnCreatedPatient()
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
    void shouldReturnBadRequestBeforeInvokingUseCaseWhenPasswordExceedsBcryptLimit()
            throws Exception {

        RegisterPatientRequest request =
                new RegisterPatientRequest(
                        "Maria Patient",
                        "patient@example.com",
                        "123456789",
                        "ñ".repeat(37)
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
                                .value("INVALID_REGISTRATION")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Registration data is invalid."
                                )
                )
                .andExpect(
                        jsonPath("$.details")
                                .isArray()
                );

        verify(registerPatientUseCase, never())
                .register(any());
    }
}