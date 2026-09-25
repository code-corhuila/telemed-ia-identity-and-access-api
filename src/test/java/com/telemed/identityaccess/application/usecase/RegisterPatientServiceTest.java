package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.RegisterPatientUseCase;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.Test;

import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.IDENTITY_DOCUMENT_ALREADY_REGISTERED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.INVALID_REGISTRATION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegisterPatientServiceTest {

    @Test
    void shouldRegisterPatientWithNormalizedEmailAndHashedPassword() {

        UserRepositoryPort users = mock(UserRepositoryPort.class);
        PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);

        when(users.existsByEmail("patient@example.com"))
                .thenReturn(false);

        when(users.existsByIdentityDocument("123456789"))
                .thenReturn(false);

        when(passwordHasher.hash("Secret123!"))
                .thenReturn("$2a$10$hashed-password");

        when(users.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);

                    return new User(
                            1L,
                            user.fullName(),
                            user.email(),
                            user.identityDocument(),
                            user.passwordHash(),
                            user.role(),
                            user.active(),
                            user.verified()
                    );
                });

        RegisterPatientUseCase useCase =
                new RegisterPatientService(users, passwordHasher);

        var command = new RegisterPatientUseCase.Command(
                "Maria Patient",
                "  PATIENT@example.com ",
                "123456789",
                "Secret123!"
        );

        var result = useCase.register(command);

        assertEquals(1L, result.userId());
        assertEquals(Role.PATIENT, result.role());

        verify(passwordHasher).hash("Secret123!");

        verify(users).save(argThat(user ->
                user.email().equals("patient@example.com")
                        && user.passwordHash().equals("$2a$10$hashed-password")
                        && !user.passwordHash().equals("Secret123!")
                        && user.role() == Role.PATIENT
                        && user.active()
                        && !user.verified()
        ));
    }

    @Test
    void shouldRejectRegistrationWhenEmailAlreadyExists() {

        UserRepositoryPort users = mock(UserRepositoryPort.class);
        PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);

        when(users.existsByEmail("patient@example.com"))
                .thenReturn(true);

        RegisterPatientUseCase useCase =
                new RegisterPatientService(users, passwordHasher);

        var command = new RegisterPatientUseCase.Command(
                "Maria Patient",
                "PATIENT@example.com",
                "123456789",
                "Secret123!"
        );

        var exception = assertThrows(
                RegistrationException.class,
                () -> useCase.register(command)
        );

        assertEquals(
                EMAIL_ALREADY_REGISTERED,
                exception.reason()
        );

        assertEquals(
                "Email is already registered.",
                exception.getMessage()
        );

        verify(users, never())
                .existsByIdentityDocument(anyString());

        verify(users, never())
                .save(any());

        verify(passwordHasher, never())
                .hash(anyString());
    }

    @Test
    void shouldRejectRegistrationWhenIdentityDocumentAlreadyExists() {

        UserRepositoryPort users = mock(UserRepositoryPort.class);
        PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);

        when(users.existsByEmail("patient@example.com"))
                .thenReturn(false);

        when(users.existsByIdentityDocument("123456789"))
                .thenReturn(true);

        RegisterPatientUseCase useCase =
                new RegisterPatientService(users, passwordHasher);

        var command = new RegisterPatientUseCase.Command(
                "Maria Patient",
                "patient@example.com",
                "123456789",
                "Secret123!"
        );

        var exception = assertThrows(
                RegistrationException.class,
                () -> useCase.register(command)
        );

        assertEquals(
                IDENTITY_DOCUMENT_ALREADY_REGISTERED,
                exception.reason()
        );

        assertEquals(
                "Identity document is already registered.",
                exception.getMessage()
        );

        verify(users, never())
                .save(any());

        verify(passwordHasher, never())
                .hash(anyString());
    }

    @Test
    void shouldRejectBlankRegistrationData() {

        var exception = assertThrows(
                RegistrationException.class,
                () -> new RegisterPatientUseCase.Command(
                        "Maria Patient",
                        " ",
                        "123456789",
                        "Secret123!"
                )
        );

        assertEquals(
                INVALID_REGISTRATION,
                exception.reason()
        );

        assertEquals(
                "Email is required.",
                exception.getMessage()
        );
    }

    @Test
    void shouldRedactPasswordFromCommandToString() {

        var command = new RegisterPatientUseCase.Command(
                "Maria Patient",
                "patient@example.com",
                "123456789",
                "Secret123!"
        );

        String commandText = command.toString();

        assertFalse(
                commandText.contains("Secret123!")
        );

        assertTrue(
                commandText.contains("password=***")
        );
    }
}