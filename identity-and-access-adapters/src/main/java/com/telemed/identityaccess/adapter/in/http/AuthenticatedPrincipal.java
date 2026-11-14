package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.domain.model.Role;

import java.util.UUID;

public record AuthenticatedPrincipal(
        UUID userId,
        Role role
) {

    public AuthenticatedPrincipal {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Authenticated user ID is required."
            );
        }

        if (role == null) {
            throw new IllegalArgumentException(
                    "Authenticated role is required."
            );
        }
    }
}