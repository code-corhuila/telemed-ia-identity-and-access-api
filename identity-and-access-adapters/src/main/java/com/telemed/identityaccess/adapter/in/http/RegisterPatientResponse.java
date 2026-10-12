package com.telemed.identityaccess.adapter.in.http;

import java.util.UUID;

import com.telemed.identityaccess.domain.model.Role;

public record RegisterPatientResponse(
        UUID userId,
        String role
) {

    public static RegisterPatientResponse from(
            UUID userId,
            Role role
    ) {
        return new RegisterPatientResponse(
                userId,
                toApiRole(role)
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