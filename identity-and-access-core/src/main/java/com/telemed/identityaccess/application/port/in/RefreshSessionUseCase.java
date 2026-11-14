package com.telemed.identityaccess.application.port.in;

import com.telemed.identityaccess.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

public interface RefreshSessionUseCase {

    Result refresh(String refreshToken);

    record Result(
            UUID userId,
            Role role,
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt
    ) {
    }
}