package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.BootstrapAdminUseCase;
import com.telemed.identityaccess.application.port.in.BootstrapAdminUseCase.Outcome;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The first ADMIN comes from the environment: there is no other way to create one. */
class BootstrapAdminServiceTest {

    private final UserRepositoryPort users =
            mock(UserRepositoryPort.class);

    private final PasswordHasherPort passwordHasher =
            mock(PasswordHasherPort.class);

    private final BootstrapAdminUseCase useCase =
            new BootstrapAdminService(
                    users,
                    passwordHasher
            );

    @Test
    void shouldCreateAnActiveVerifiedAdminWithAHashedPassword() {

        when(passwordHasher.hash("Admin-Secret-1"))
                .thenReturn("$2a$10$hashed-password");
        when(users.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Outcome outcome = useCase.bootstrap(command());

        ArgumentCaptor<User> saved =
                ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());

        assertEquals(Outcome.CREATED, outcome);
        assertEquals(Role.ADMIN, saved.getValue().role());
        assertEquals("Platform Admin", saved.getValue().fullName());
        assertEquals("admin@telemed.test", saved.getValue().email());
        assertEquals("ADM-0001", saved.getValue().identityDocument());
        assertEquals("$2a$10$hashed-password", saved.getValue().passwordHash());
        assertTrue(saved.getValue().active());
        assertTrue(saved.getValue().verified());
    }

    @Test
    void shouldDoNothingWhenAnAdminAlreadyExists() {

        when(users.existsByRole(Role.ADMIN))
                .thenReturn(true);

        assertEquals(
                Outcome.ALREADY_PRESENT,
                useCase.bootstrap(command())
        );
        verify(users, never()).save(any());
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void shouldNeverPromoteAnAccountThatAlreadyUsesTheEmail() {

        when(users.existsByEmail("admin@telemed.test"))
                .thenReturn(true);

        assertEquals(
                Outcome.ACCOUNT_IN_USE,
                useCase.bootstrap(command())
        );
        verify(users, never()).save(any());
    }

    @Test
    void shouldNeverPromoteAnAccountThatAlreadyUsesTheIdentityDocument() {

        when(users.existsByIdentityDocument("ADM-0001"))
                .thenReturn(true);

        assertEquals(
                Outcome.ACCOUNT_IN_USE,
                useCase.bootstrap(command())
        );
        verify(users, never()).save(any());
    }

    @Test
    void shouldReportAlreadyPresentWhenAnotherInstanceCreatedTheAdminFirst() {

        when(passwordHasher.hash("Admin-Secret-1"))
                .thenReturn("$2a$10$hashed-password");
        when(users.existsByRole(Role.ADMIN))
                .thenReturn(false, true);
        when(users.save(any(User.class)))
                .thenThrow(new RegistrationException(
                        EMAIL_ALREADY_REGISTERED,
                        "Email is already registered."
                ));

        assertEquals(
                Outcome.ALREADY_PRESENT,
                useCase.bootstrap(command())
        );
    }

    @Test
    void shouldNormalizeTheEmailLikeRegistration() {

        var command = new BootstrapAdminUseCase.Command(
                " Platform Admin ",
                "  ADMIN@Telemed.test ",
                " ADM-0001 ",
                "Admin-Secret-1"
        );

        assertEquals("admin@telemed.test", command.email());
        assertEquals("Platform Admin", command.fullName());
        assertEquals("ADM-0001", command.identityDocument());
    }

    @Test
    void shouldRejectAPasswordOutsideThePolicy() {

        IllegalArgumentException tooShort = assertThrows(
                IllegalArgumentException.class,
                () -> new BootstrapAdminUseCase.Command(
                        "Platform Admin",
                        "admin@telemed.test",
                        "ADM-0001",
                        "short"
                )
        );

        assertEquals(
                "Password must contain at least 8 characters.",
                tooShort.getMessage()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new BootstrapAdminUseCase.Command(
                        "Platform Admin",
                        "admin@telemed.test",
                        "ADM-0001",
                        "x".repeat(73)
                )
        );
    }

    @Test
    void shouldRejectABlankField() {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new BootstrapAdminUseCase.Command(
                        "Platform Admin",
                        "admin@telemed.test",
                        " ",
                        "Admin-Secret-1"
                )
        );

        assertEquals(
                "Identity document is required.",
                exception.getMessage()
        );
    }

    @Test
    void shouldNeverPrintThePassword() {

        assertFalse(command().toString().contains("Admin-Secret-1"));
    }

    private static BootstrapAdminUseCase.Command command() {
        return new BootstrapAdminUseCase.Command(
                "Platform Admin",
                "admin@telemed.test",
                "ADM-0001",
                "Admin-Secret-1"
        );
    }
}
