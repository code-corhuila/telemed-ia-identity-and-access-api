package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenProviderPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.RefreshToken;
import com.telemed.identityaccess.domain.model.Role;
import com.telemed.identityaccess.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AuthenticationException.Reason.INVALID_CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String DUMMY_HASH =
            "$dummy-bcrypt-hash";

    private static final String REFRESH_TOKEN_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111"
    );

    @Mock
    private UserRepositoryPort users;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private AccessTokenProviderPort accessTokens;

    @Mock
    private RefreshTokenProviderPort refreshTokens;

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;

    private LoginService service;

    @BeforeEach
    void setUp() {

        when(passwordHasher.hash(anyString()))
                .thenReturn(DUMMY_HASH);

        service = new LoginService(
                users,
                passwordHasher,
                accessTokens,
                refreshTokens,
                refreshTokenRepository
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

        Instant accessExpiresAt =
                Instant.parse("2026-09-25T22:00:00Z");

        when(accessTokens.issue(
                USER_ID,
                Role.PATIENT
        )).thenReturn(
                new AccessTokenProviderPort.IssuedAccessToken(
                        "access-token",
                        accessExpiresAt
                )
        );

        Instant refreshExpiresAt =
                Instant.parse("2026-10-02T22:00:00Z");

        when(refreshTokens.issue())
                .thenReturn(
                        new RefreshTokenProviderPort.IssuedRefreshToken(
                                "plain-refresh-token",
                                REFRESH_TOKEN_HASH,
                                refreshExpiresAt
                        )
                );

        LoginUseCase.Result result = service.login(
                new LoginUseCase.Command(
                        "patient@example.com",
                        "StrongPassword123!"
                )
        );

        assertThat(result.userId())
                .isEqualTo(USER_ID);

        assertThat(result.role())
                .isEqualTo(Role.PATIENT);

        assertThat(result.accessToken())
                .isEqualTo("access-token");

        assertThat(result.expiresAt())
                .isEqualTo(accessExpiresAt);

        assertThat(result.refreshToken())
                .isEqualTo("plain-refresh-token");

        assertThat(result.refreshExpiresAt())
                .isEqualTo(refreshExpiresAt);

        verify(passwordHasher).matches(
                "StrongPassword123!",
                user.passwordHash()
        );

        verify(accessTokens).issue(
                USER_ID,
                Role.PATIENT
        );

        verify(refreshTokens).issue();

        verify(refreshTokenRepository).save(
                RefreshToken.active(
                        USER_ID,
                        REFRESH_TOKEN_HASH,
                        refreshExpiresAt
                )
        );
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
                        any(UUID.class),
                        any()
                );

        verify(refreshTokens, never())
                .issue();

        verify(refreshTokenRepository, never())
                .save(
                        any(RefreshToken.class)
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

        verify(passwordHasher).matches(
                "WrongPassword",
                user.passwordHash()
        );

        verify(accessTokens, never())
                .issue(
                        any(UUID.class),
                        any()
                );

        verify(refreshTokens, never())
                .issue();

        verify(refreshTokenRepository, never())
                .save(
                        any(RefreshToken.class)
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

        Instant accessExpiresAt =
                Instant.parse("2026-09-26T15:00:00Z");

        when(accessTokens.issue(
                USER_ID,
                Role.PATIENT
        )).thenReturn(
                new AccessTokenProviderPort.IssuedAccessToken(
                        "access-token",
                        accessExpiresAt
                )
        );

        Instant refreshExpiresAt =
                Instant.parse("2026-10-03T15:00:00Z");

        when(refreshTokens.issue())
                .thenReturn(
                        new RefreshTokenProviderPort.IssuedRefreshToken(
                                "plain-refresh-token",
                                REFRESH_TOKEN_HASH,
                                refreshExpiresAt
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
                USER_ID,
                "$2a$10$upgradedPasswordHash"
        );

        verify(accessTokens).issue(
                USER_ID,
                Role.PATIENT
        );

        verify(refreshTokens).issue();

        verify(refreshTokenRepository).save(
                RefreshToken.active(
                        USER_ID,
                        REFRESH_TOKEN_HASH,
                        refreshExpiresAt
                )
        );

        assertThat(result.accessToken())
                .isEqualTo("access-token");

        assertThat(result.refreshToken())
                .isEqualTo("plain-refresh-token");
    }

    @Test
    void shouldRejectInactiveUserWithGenericError() {

        User user = new User(
                USER_ID,
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
                        any(UUID.class),
                        any()
                );

        verify(refreshTokens, never())
                .issue();

        verify(refreshTokenRepository, never())
                .save(
                        any(RefreshToken.class)
                );
    }

    private void assertInvalidCredentials(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation
    ) {

        assertThatThrownBy(operation)
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid credentials.")
                .satisfies(exception ->
                        assertThat(
                                ((AuthenticationException) exception)
                                        .reason()
                        ).isEqualTo(INVALID_CREDENTIALS)
                );
    }

    private User activePatient() {

        return new User(
                USER_ID,
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