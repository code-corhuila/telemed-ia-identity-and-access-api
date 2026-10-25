package com.telemed.identityaccess.domain.model;

import java.util.UUID;

public record User(
        UUID id,
        String fullName,
        String email,
        String identityDocument,
        String passwordHash,
        Role role,
        boolean active,
        boolean verified
) {

    public User {
        requireText(fullName, "Full name is required.");
        requireText(email, "Email is required.");
        requireText(identityDocument, "Identity document is required.");
        requireText(passwordHash, "Password hash is required.");

        if (role == null) {
            throw new IllegalArgumentException("Role is required.");
        }
    }

    /** Created by professional management on behalf of an ADMIN; professionals never register themselves. */
    public static User registerProfessional(
            String fullName,
            String email,
            String identityDocument,
            String passwordHash
    ) {
        return new User(
                null,
                fullName,
                email,
                identityDocument,
                passwordHash,
                Role.PROFESSIONAL,
                true,
                false
        );
    }

    public static User registerPatient(
            String fullName,
            String email,
            String identityDocument,
            String passwordHash
    ) {
        return new User(
                null,
                fullName,
                email,
                identityDocument,
                passwordHash,
                Role.PATIENT,
                true,
                false
        );
    }

    /** The first administrator, created from the environment; nobody can register one. */
    public static User registerAdmin(
            String fullName,
            String email,
            String identityDocument,
            String passwordHash
    ) {
        return new User(
                null,
                fullName,
                email,
                identityDocument,
                passwordHash,
                Role.ADMIN,
                true,
                true
        );
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}