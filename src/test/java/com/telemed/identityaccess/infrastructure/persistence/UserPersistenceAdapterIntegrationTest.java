package com.telemed.identityaccess.infrastructure.persistence;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.IDENTITY_DOCUMENT_ALREADY_REGISTERED;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class UserPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("telemed_identity")
                    .withUsername("telemed_identity")
                    .withPassword("integration_test");

    @Autowired
    private UserPersistenceAdapter adapter;

    @Autowired
    private SpringDataUserRepository users;

    @BeforeEach
    void cleanUsers() {
        users.deleteAll();
    }

    @Test
    void shouldPersistPatientUsingRealPostgreSQL() {

        User patient = User.registerPatient(
                "Maria Patient",
                "patient@example.com",
                "TEST-DOC-001",
                "$2a$10$integration-test-hash"
        );

        User saved = adapter.save(patient);

        assertNotNull(saved.id());
        assertEquals("patient@example.com", saved.email());
        assertEquals("TEST-DOC-001", saved.identityDocument());
        assertEquals(Role.PATIENT, saved.role());
        assertTrue(saved.active());
        assertFalse(saved.verified());
    }

    @Test
    void shouldFindEmailIgnoringCase() {

        adapter.save(User.registerPatient(
                "Maria Patient",
                "patient@example.com",
                "TEST-DOC-002",
                "$2a$10$integration-test-hash"
        ));

        assertTrue(
                adapter.existsByEmail("PATIENT@EXAMPLE.COM")
        );
    }

    @Test
    void shouldFindExistingIdentityDocument() {

        adapter.save(User.registerPatient(
                "Maria Patient",
                "document@example.com",
                "TEST-DOC-003",
                "$2a$10$integration-test-hash"
        ));

        assertTrue(
                adapter.existsByIdentityDocument("TEST-DOC-003")
        );
    }

    @Test
    void shouldTranslateDuplicateEmailIntoRegistrationConflict() {

        adapter.save(User.registerPatient(
                "First Patient",
                "duplicate@example.com",
                "TEST-DOC-004",
                "$2a$10$integration-test-hash"
        ));

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> adapter.save(User.registerPatient(
                        "Second Patient",
                        "DUPLICATE@example.com",
                        "TEST-DOC-005",
                        "$2a$10$integration-test-hash"
                ))
        );

        assertEquals(
                EMAIL_ALREADY_REGISTERED,
                exception.reason()
        );
    }

    @Test
void shouldRejectDuplicateEmailIgnoringCase() {

    adapter.save(User.registerPatient(
            "First Patient",
            "patient@example.com",
            "TEST-DOC-CASE-001",
            "$2a$10$integration-test-hash"
    ));

    RegistrationException exception = assertThrows(
            RegistrationException.class,
            () -> adapter.save(User.registerPatient(
                    "Second Patient",
                    "PATIENT@EXAMPLE.COM",
                    "TEST-DOC-CASE-002",
                    "$2a$10$integration-test-hash"
            ))
    );

    assertEquals(
            EMAIL_ALREADY_REGISTERED,
            exception.reason()
    );
}

    @Test
    void shouldTranslateDuplicateIdentityDocumentIntoRegistrationConflict() {

        adapter.save(User.registerPatient(
                "First Patient",
                "first@example.com",
                "TEST-DOC-006",
                "$2a$10$integration-test-hash"
        ));

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> adapter.save(User.registerPatient(
                        "Second Patient",
                        "second@example.com",
                        "TEST-DOC-006",
                        "$2a$10$integration-test-hash"
                ))
        );

        assertEquals(
                IDENTITY_DOCUMENT_ALREADY_REGISTERED,
                exception.reason()
        );
    }
}