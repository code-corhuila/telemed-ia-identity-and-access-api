package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.port.in.LoginUseCase;
import com.telemed.identityaccess.domain.model.Role;

import java.time.Instant;

public record LoginResponse(
        Long userId,
        String role,
        String accessToken,
        String tokenType,
        Instant expiresAt
) {

    private static final String TOKEN_TYPE = "Bearer";

    public static LoginResponse from(
            LoginUseCase.Result result
    ) {
        return new LoginResponse(
                result.userId(),
                toApiRole(result.role()),
                result.accessToken(),
                TOKEN_TYPE,
                result.expiresAt()
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