package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.LogoutException;
import com.telemed.identityaccess.application.port.in.LogoutUseCase;
import com.telemed.identityaccess.application.port.out.RefreshTokenHasherPort;
import com.telemed.identityaccess.application.port.out.RefreshTokenRepositoryPort;
import com.telemed.identityaccess.domain.model.RefreshToken;

public class LogoutService
        implements LogoutUseCase {

    private final RefreshTokenRepositoryPort refreshTokens;
    private final RefreshTokenHasherPort refreshTokenHasher;

    public LogoutService(
            RefreshTokenRepositoryPort refreshTokens,
            RefreshTokenHasherPort refreshTokenHasher
    ) {
        this.refreshTokens = refreshTokens;
        this.refreshTokenHasher = refreshTokenHasher;
    }

    @Override
    public void logout(String refreshToken) {

        if (refreshToken == null
                || refreshToken.isBlank()) {

            throw LogoutException
                    .invalidRefreshToken();
        }

        String tokenHash =
                refreshTokenHasher.hash(
                        refreshToken
                );

        RefreshToken storedToken =
                refreshTokens.findByTokenHash(
                                tokenHash
                        )
                        .orElseThrow(
                                LogoutException
                                        ::invalidRefreshToken
                        );

        if (storedToken.revoked()) {
            throw LogoutException
                    .invalidRefreshToken();
        }

        refreshTokens.revokeByTokenHash(
                tokenHash
        );
    }
}