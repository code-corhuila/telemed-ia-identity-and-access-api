package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AccountProvisioningException;
import com.telemed.identityaccess.application.exception.RegistrationException;
import com.telemed.identityaccess.application.port.in.ProvisionProfessionalAccountUseCase;
import com.telemed.identityaccess.application.port.in.ProvisionProfessionalAccountUseCase.Command;
import com.telemed.identityaccess.application.port.out.IdempotencyKeyRepositoryPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccountProvisioningException.Reason.IDEMPOTENCY_KEY_REUSED;
import static com.telemed.identityaccess.application.exception.AccountProvisioningException.Reason.INVALID_IDEMPOTENCY_KEY;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.EMAIL_ALREADY_REGISTERED;
import static com.telemed.identityaccess.application.exception.RegistrationException.Reason.IDENTITY_DOCUMENT_ALREADY_REGISTERED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Professional management creates the account of each professional it registers. */
class ProvisionProfessionalAccountServiceTest {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final String KEY = "register-professional-0001";

    private final UserRepositoryPort users =
            mock(UserRepositoryPort.class);

    private final PasswordHasherPort passwordHasher =
            mock(PasswordHasherPort.class);

    private final IdempotencyKeyRepositoryPort idempotencyKeys =
            mock(IdempotencyKeyRepositoryPort.class);

    private final ProvisionProfessionalAccountUseCase useCase =
            new ProvisionProfessionalAccountService(
                    users,
                    passwordHasher,
                    idempotencyKeys
            );

    @Test
    void shouldCreateAnActiveProfessionalAndRememberTheKey() {

        when(passwordHasher.hash("Doctor-Secret-1"))
                .thenReturn("$2a$10$hashed-password");
        when(users.save(any(User.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0)));

        var result = useCase.provision(command());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        verify(idempotencyKeys).save(KEY, ACCOUNT_ID);

        assertEquals(ACCOUNT_ID, result.userId());
        assertFalse(result.replayed());
        assertEquals(Role.PROFESSIONAL, saved.getValue().role());
        assertEquals("doctor@telemed.test", saved.getValue().email());
        assertEquals("$2a$10$hashed-password", saved.getValue().passwordHash());
        assertTrue(saved.getValue().active());
    }

    @Test
    void shouldAnswerTheSameAccountWhenTheSameRequestIsRetried() {

        when(idempotencyKeys.findUserId(KEY))
                .thenReturn(Optional.of(ACCOUNT_ID));
        when(users.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(storedProfessional()));
        when(passwordHasher.matches("Doctor-Secret-1", "$2a$10$hashed-password"))
                .thenReturn(true);

        var result = useCase.provision(command());

        assertEquals(ACCOUNT_ID, result.userId());
        assertTrue(result.replayed());
        verify(users, never()).save(any());
    }

    @Test
    void shouldRejectAKeyReusedForAnotherRequest() {

        when(idempotencyKeys.findUserId(KEY))
                .thenReturn(Optional.of(ACCOUNT_ID));
        when(users.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(storedProfessional()));
        when(passwordHasher.matches("Doctor-Secret-1", "$2a$10$hashed-password"))
                .thenReturn(true);

        AccountProvisioningException exception = assertThrows(
                AccountProvisioningException.class,
                () -> useCase.provision(new Command(
                        KEY,
                        "Another Doctor",
                        "another@telemed.test",
                        "DOC-0002",
                        "Doctor-Secret-1"
                ))
        );

        assertEquals(IDEMPOTENCY_KEY_REUSED, exception.reason());
        verify(users, never()).save(any());
    }

    @Test
    void shouldRejectAKeyReusedWithAnotherPassword() {

        when(idempotencyKeys.findUserId(KEY))
                .thenReturn(Optional.of(ACCOUNT_ID));
        when(users.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(storedProfessional()));

        AccountProvisioningException exception = assertThrows(
                AccountProvisioningException.class,
                () -> useCase.provision(command())
        );

        assertEquals(IDEMPOTENCY_KEY_REUSED, exception.reason());
    }

    @Test
    void shouldRejectAnEmailThatAlreadyHasAnAccount() {

        when(users.existsByEmail("doctor@telemed.test"))
                .thenReturn(true);

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> useCase.provision(command())
        );

        assertEquals(EMAIL_ALREADY_REGISTERED, exception.reason());
        verify(users, never()).save(any());
        verify(idempotencyKeys, never()).save(any(), any());
    }

    @Test
    void shouldRejectAnIdentityDocumentThatAlreadyHasAnAccount() {

        when(users.existsByIdentityDocument("DOC-0001"))
                .thenReturn(true);

        RegistrationException exception = assertThrows(
                RegistrationException.class,
                () -> useCase.provision(command())
        );

        assertEquals(IDENTITY_DOCUMENT_ALREADY_REGISTERED, exception.reason());
    }

    @Test
    void shouldRequireAnIdempotencyKeyOf8To128Characters() {

        for (String key : new String[]{null, " ", "short", "k".repeat(129)}) {
            AccountProvisioningException exception = assertThrows(
                    AccountProvisioningException.class,
                    () -> new Command(
                            key,
                            "Doctor Name",
                            "doctor@telemed.test",
                            "DOC-0001",
                            "Doctor-Secret-1"
                    )
            );

            assertEquals(INVALID_IDEMPOTENCY_KEY, exception.reason());
        }
    }

    @Test
    void shouldApplyTheRegistrationRulesToTheAccount() {

        assertThrows(
                RegistrationException.class,
                () -> new Command(KEY, "Doctor Name", "doctor@telemed.test", "DOC-0001", "short")
        );

        Command command = new Command(KEY, " Doctor Name ", " Doctor@Telemed.TEST ", " DOC-0001 ", "Doctor-Secret-1");

        assertEquals("Doctor Name", command.fullName());
        assertEquals("doctor@telemed.test", command.email());
        assertEquals("DOC-0001", command.identityDocument());
        assertFalse(command.toString().contains("Doctor-Secret-1"));
    }

    private static Command command() {
        return new Command(
                KEY,
                "Doctor Name",
                "doctor@telemed.test",
                "DOC-0001",
                "Doctor-Secret-1"
        );
    }

    private static User storedProfessional() {
        return new User(
                ACCOUNT_ID,
                "Doctor Name",
                "doctor@telemed.test",
                "DOC-0001",
                "$2a$10$hashed-password",
                Role.PROFESSIONAL,
                true,
                false
        );
    }

    private static User withId(User user) {
        return new User(
                ACCOUNT_ID,
                user.fullName(),
                user.email(),
                user.identityDocument(),
                user.passwordHash(),
                user.role(),
                user.active(),
                user.verified()
        );
    }
}
