package com.telemed.identityaccess.infrastructure.web;

import com.telemed.identityaccess.domain.model.Role;

public record RegisterPatientResponse(
        Long userId,
        String role
) {

    public static RegisterPatientResponse from(
            Long userId,
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