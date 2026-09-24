package com.telemed.identityaccess.infrastructure.web;

public record RegisterPatientResponse(
        Long userId,
        String role
) {
}