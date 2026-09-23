package com.telemed.identityaccess.domain.model;

public record User(
        Long id,
        String fullName,
        String email,
        String identityDocument,
        String passwordHash,
        Role role,
        boolean active,
        boolean verified
) {
}