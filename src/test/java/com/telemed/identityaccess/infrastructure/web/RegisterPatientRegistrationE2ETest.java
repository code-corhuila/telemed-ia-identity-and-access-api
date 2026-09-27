package com.telemed.identityaccess.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterPatientRegistrationE2ETest {

    @SuppressWarnings("resource")
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("telemed_identity")
                    .withUsername("telemed_identity")
                    .withPassword("integration_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldRegisterPatientEndToEnd() throws Exception {

        var request = new RegisterPatientRequest(
                "Maria Patient",
                "  PATIENT@example.com ",
                "E2E-DOC-001",
                "Secret123!"
        );

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.role").value("PATIENT"));

        var user = jdbcTemplate.queryForMap(
                """
                SELECT
                    email,
                    identity_document,
                    password_hash,
                    verified,
                    active,
                    registration_date
                FROM users
                WHERE identity_document = ?
                """,
                "E2E-DOC-001"
        );

        assertEquals(
                "patient@example.com",
                user.get("email")
        );

        assertEquals(
                "E2E-DOC-001",
                user.get("identity_document")
        );

        assertEquals(
                Boolean.FALSE,
                user.get("verified")
        );

        assertEquals(
                Boolean.TRUE,
                user.get("active")
        );

        assertNotNull(
                user.get("registration_date")
        );

        String passwordHash =
                (String) user.get("password_hash");

        assertNotEquals(
                "Secret123!",
                passwordHash
        );

        assertTrue(
                new BCryptPasswordEncoder()
                        .matches("Secret123!", passwordHash)
        );
    }

    @Test
    void shouldRejectInvalidRegistrationRequest() throws Exception {

        var request = new RegisterPatientRequest(
                "",
                "invalid-email",
                "",
                "short"
        );

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.identityDocument").exists());
    }

    @Test
    void shouldReturnGenericConflictForDuplicateEmail() throws Exception {

        registerPatient(
                "First Patient",
                "duplicate@example.com",
                "E2E-DOC-002"
        );

        var duplicateRequest = new RegisterPatientRequest(
                "Second Patient",
                "DUPLICATE@example.com",
                "E2E-DOC-003",
                "Secret123!"
        );

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(duplicateRequest))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_CONFLICT"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Registration cannot be completed with the provided data.")
                )
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void shouldReturnGenericConflictForDuplicateIdentityDocument()
            throws Exception {

        registerPatient(
                "First Patient",
                "first@example.com",
                "E2E-DOC-004"
        );

        var duplicateRequest = new RegisterPatientRequest(
                "Second Patient",
                "second@example.com",
                "E2E-DOC-004",
                "Secret123!"
        );

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(duplicateRequest))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_CONFLICT"));
    }

    private void registerPatient(
            String fullName,
            String email,
            String identityDocument
    ) throws Exception {

        var request = new RegisterPatientRequest(
                fullName,
                email,
                identityDocument,
                "Secret123!"
        );

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated());
    }
}