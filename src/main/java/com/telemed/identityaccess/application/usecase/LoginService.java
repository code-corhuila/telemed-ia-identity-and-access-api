package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.User;

public class LoginService implements LoginUseCase {

    private static final String DUMMY_PASSWORD =
            "telemed-authentication-dummy-password";

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final AccessTokenProviderPort accessTokens;
    private final String dummyPasswordHash;

    public LoginService(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            AccessTokenProviderPort accessTokens
    ) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.accessTokens = accessTokens;
        this.dummyPasswordHash =
                passwordHasher.hash(DUMMY_PASSWORD);
    }

    @Override
    public Result login(Command command) {

        User user = users.findByEmail(command.email())
                .orElse(null);

        String passwordHash = user != null
                ? user.passwordHash()
                : dummyPasswordHash;

        boolean passwordMatches =
                passwordHasher.matches(
                        command.password(),
                        passwordHash
                );

        if (user == null
                || !passwordMatches
                || !user.active()) {

            throw AuthenticationException
                    .invalidCredentials();
        }

        AccessTokenProviderPort.IssuedAccessToken token =
                accessTokens.issue(
                        user.id(),
                        user.role()
                );

        return new Result(
                user.id(),
                user.role(),
                token.value(),
                token.expiresAt()
        );
    }
}