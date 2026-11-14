package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.RefreshSessionUseCase;
import com.telemed.identityaccess.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenResponse(
        UUID userId,
        String role,
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String refreshToken,
        Instant refreshExpiresAt
) {

    private static final String TOKEN_TYPE = "Bearer";

    public static RefreshTokenResponse from(
            RefreshSessionUseCase.Result result
    ) {
        return new RefreshTokenResponse(
                result.userId(),
                toApiRole(result.role()),
                result.accessToken(),
                TOKEN_TYPE,
                result.accessExpiresAt(),
                result.refreshToken(),
                result.refreshExpiresAt()
        );
    }

    private static String toApiRole(Role role) {
        return switch (role) {
            case PATIENT -> "PATIENT";
            case PROFESSIONAL -> "PROFESSIONAL";
            case ADMIN -> "ADMIN";
        };
    }
}