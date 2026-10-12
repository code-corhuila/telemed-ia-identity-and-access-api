package com.telemed.identityaccess.adapter.in.http;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterPatientRequest(

        @NotBlank(message = "Full name is required.")
        @Size(
                max = 150,
                message = "Full name must not exceed 150 characters."
        )
        String fullName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(
                max = 180,
                message = "Email must not exceed 180 characters."
        )
        String email,

        @NotBlank(message = "Identity document is required.")
        @Size(
                max = 50,
                message = "Identity document must not exceed 50 characters."
        )
        String identityDocument,

        @NotBlank(message = "Password is required.")
        String password

) {

    public RegisterPatientRequest {
        fullName = normalize(fullName);
        email = normalize(email);
        identityDocument = normalize(identityDocument);
    }

    private static String normalize(String value) {
        return value == null
                ? null
                : value.trim();
    }

    @Override
    public String toString() {
        return "RegisterPatientRequest[" +
                "fullName=[REDACTED]" +
                ", email=[REDACTED]" +
                ", identityDocument=[REDACTED]" +
                ", password=[REDACTED]" +
                "]";
    }
}