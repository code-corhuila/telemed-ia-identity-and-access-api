package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.AuthenticationException;
import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenProviderPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.User;

public class LoginService implements LoginUseCase {

    private static final String DUMMY_PASSWORD =
            "telemed-authentication-dummy-password";

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final AccessTokenProviderPort accessTokens;
    private final RefreshTokenProviderPort refreshTokens;
    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final String dummyPasswordHash;

    public LoginService(
            UserRepositoryPort users,
            PasswordHasherPort passwordHasher,
            AccessTokenProviderPort accessTokens,
            RefreshTokenProviderPort refreshTokens,
            RefreshTokenRepositoryPort refreshTokenRepository
    ) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.refreshTokenRepository = refreshTokenRepository;

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

        if (passwordHasher.needsRehash(
                user.passwordHash()
        )) {

            String upgradedPasswordHash =
                    passwordHasher.hash(
                            command.password()
                    );

            users.updatePasswordHash(
                    user.id(),
                    upgradedPasswordHash
            );
        }

        AccessTokenProviderPort.IssuedAccessToken accessToken =
                accessTokens.issue(
                        user.id(),
                        user.role()
                );

        RefreshTokenProviderPort.IssuedRefreshToken refreshToken =
                refreshTokens.issue();

        refreshTokenRepository.save(
                user.id(),
                refreshToken.tokenHash(),
                refreshToken.expiresAt()
        );

        return new Result(
                user.id(),
                user.role(),
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.expiresAt()
        );
    }
}