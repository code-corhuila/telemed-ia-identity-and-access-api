package com.telemed.identityaccess.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(
                max = 180,
                message = "Email must not exceed 180 characters."
        )
        String email,

        @NotBlank(message = "Password is required.")
        String password

) {

    public LoginRequest {
        email = normalize(email);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    @Override
    public String toString() {
        return "LoginRequest[" +
                "email=[REDACTED]" +
                ", password=[REDACTED]" +
                "]";
    }
}