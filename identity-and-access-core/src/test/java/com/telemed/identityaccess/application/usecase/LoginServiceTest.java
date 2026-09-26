package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.telemed.identityaccess.application.exception.AuthenticationException.Reason.INVALID_CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String DUMMY_HASH = "$dummy-bcrypt-hash";

    @Mock
    private UserRepositoryPort users;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private AccessTokenProviderPort accessTokens;

    private LoginService service;

    @BeforeEach
    void setUp() {
        when(passwordHasher.hash(anyString()))
                .thenReturn(DUMMY_HASH);

        service = new LoginService(
                users,
                passwordHasher,
                accessTokens
        );
    }

    @Test
    void shouldAuthenticateActiveUserWithValidCredentials() {

        User user = activePatient();

        when(users.findByEmail("patient@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                "StrongPassword123!",
                user.passwordHash()
        )).thenReturn(true);

        Instant expiresAt =
                Instant.parse("2026-09-25T22:00:00Z");

        when(accessTokens.issue(10L, Role.PATIENT))
                .thenReturn(
                        new AccessTokenProviderPort.IssuedAccessToken(
                                "access-token",
                                expiresAt
                        )
                );

        LoginUseCase.Result result = service.login(
                new LoginUseCase.Command(
                        "patient@example.com",
                        "StrongPassword123!"
                )
        );

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.role()).isEqualTo(Role.PATIENT);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void shouldExecutePasswordVerificationForUnknownEmail() {

        when(users.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        when(passwordHasher.matches(
                "AnyPassword123!",
                DUMMY_HASH
        )).thenReturn(false);

        assertInvalidCredentials(() ->
                service.login(
                        new LoginUseCase.Command(
                                "unknown@example.com",
                                "AnyPassword123!"
                        )
                )
        );

        verify(passwordHasher).matches(
                "AnyPassword123!",
                DUMMY_HASH
        );

        verify(accessTokens, never())
                .issue(
                        anyLong(),
                        any()
                );
    }

    @Test
    void shouldRejectIncorrectPasswordWithGenericError() {

        User user = activePatient();

        when(users.findByEmail("patient@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                "WrongPassword",
                user.passwordHash()
        )).thenReturn(false);

        assertInvalidCredentials(() ->
                service.login(
                        new LoginUseCase.Command(
                                "patient@example.com",
                                "WrongPassword"
                        )
                )
        );

        verify(accessTokens, never())
                .issue(
                        anyLong(),
                        any()
                );
    }

    @Test
void shouldRehashPasswordAfterSuccessfulLoginWhenUpgradeIsNeeded() {

    User user = activePatient();

    when(users.findByEmail("patient@example.com"))
            .thenReturn(Optional.of(user));

    when(passwordHasher.matches(
            "StrongPassword123!",
            user.passwordHash()
    )).thenReturn(true);

    when(passwordHasher.needsRehash(
            user.passwordHash()
    )).thenReturn(true);

    when(passwordHasher.hash(
            "StrongPassword123!"
    )).thenReturn(
            "$2a$10$upgradedPasswordHash"
    );

    Instant expiresAt =
            Instant.parse("2026-09-26T15:00:00Z");

    when(accessTokens.issue(
            10L,
            Role.PATIENT
    )).thenReturn(
            new AccessTokenProviderPort.IssuedAccessToken(
                    "access-token",
                    expiresAt
            )
    );

    LoginUseCase.Result result = service.login(
            new LoginUseCase.Command(
                    "patient@example.com",
                    "StrongPassword123!"
            )
    );

    verify(passwordHasher).matches(
            "StrongPassword123!",
            user.passwordHash()
    );

    verify(passwordHasher).needsRehash(
            user.passwordHash()
    );

    verify(passwordHasher).hash(
            "StrongPassword123!"
    );

    verify(users).updatePasswordHash(
            10L,
            "$2a$10$upgradedPasswordHash"
    );

    verify(accessTokens).issue(
            10L,
            Role.PATIENT
    );

    assertThat(result.accessToken())
            .isEqualTo("access-token");
}

    @Test
    void shouldRejectInactiveUserWithGenericError() {

        User user = new User(
                10L,
                "Patient Test",
                "patient@example.com",
                "DOC-100",
                "$2a$10$encodedPassword",
                Role.PATIENT,
                false,
                false
        );

        when(users.findByEmail("patient@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordHasher.matches(
                "StrongPassword123!",
                user.passwordHash()
        )).thenReturn(true);

        assertInvalidCredentials(() ->
                service.login(
                        new LoginUseCase.Command(
                                "patient@example.com",
                                "StrongPassword123!"
                        )
                )
        );

        verify(passwordHasher).matches(
                "StrongPassword123!",
                user.passwordHash()
        );

        verify(accessTokens, never())
                .issue(
                        anyLong(),
                        any()
                );
    }

    private void assertInvalidCredentials(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation
    ) {
        assertThatThrownBy(operation)
                .isInstanceOf(AuthenticationException.class)
                .satisfies(exception ->
                        assertThat(
                                ((AuthenticationException) exception)
                                        .reason()
                        ).isEqualTo(INVALID_CREDENTIALS)
                );
    }

    private User activePatient() {
        return new User(
                10L,
                "Patient Test",
                "patient@example.com",
                "DOC-100",
                "$2a$10$encodedPassword",
                Role.PATIENT,
                true,
                false
        );
    }
}