package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.RefreshSessionException;
import com.telemed.identityaccess.application.port.in.RefreshSessionUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenHasherPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenProviderPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.application.port.out.UserRepositoryPort;
import com.telemed.identityaccess.domain.model.RefreshToken;
import com.telemed.identityaccess.domain.model.User;

import java.time.Clock;

public class RefreshSessionService
        implements RefreshSessionUseCase {

    private final RefreshTokenRepositoryPort refreshTokens;
    private final RefreshTokenHasherPort refreshTokenHasher;
    private final RefreshTokenProviderPort refreshTokenProvider;
    private final AccessTokenProviderPort accessTokens;
    private final UserRepositoryPort users;
    private final Clock clock;

    public RefreshSessionService(
            RefreshTokenRepositoryPort refreshTokens,
            RefreshTokenHasherPort refreshTokenHasher,
            RefreshTokenProviderPort refreshTokenProvider,
            AccessTokenProviderPort accessTokens,
            UserRepositoryPort users,
            Clock clock
    ) {
        this.refreshTokens = refreshTokens;
        this.refreshTokenHasher = refreshTokenHasher;
        this.refreshTokenProvider = refreshTokenProvider;
        this.accessTokens = accessTokens;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public Result refresh(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw RefreshSessionException
                    .invalidRefreshToken();
        }

        String tokenHash =
                refreshTokenHasher.hash(refreshToken);

        RefreshToken current =
                refreshTokens.findByTokenHash(tokenHash)
                        .orElseThrow(
                                RefreshSessionException
                                        ::invalidRefreshToken
                        );

        if (current.revoked()
                || !current.expiresAt()
                .isAfter(clock.instant())) {

            throw RefreshSessionException
                    .invalidRefreshToken();
        }

        User user =
                users.findById(current.userId())
                        .filter(User::active)
                        .orElseThrow(
                                RefreshSessionException
                                        ::invalidRefreshToken
                        );

        AccessTokenProviderPort.IssuedAccessToken accessToken =
                accessTokens.issue(
                        user.id(),
                        user.role()
                );

        RefreshTokenProviderPort.IssuedRefreshToken issuedRefreshToken =
                refreshTokenProvider.issue();

        RefreshToken replacement =
                RefreshToken.active(
                        user.id(),
                        issuedRefreshToken.tokenHash(),
                        issuedRefreshToken.expiresAt()
                );

        refreshTokens.replace(
                current.tokenHash(),
                replacement
        );

        return new Result(
                user.id(),
                user.role(),
                accessToken.value(),
                accessToken.expiresAt(),
                issuedRefreshToken.value(),
                issuedRefreshToken.expiresAt()
        );
    }
}