package com.telemed.identityaccess.adapter.in.http;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordRecoveryRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(
                max = 180,
                message = "Email must not exceed 180 characters."
        )
        String email

) {

    public PasswordRecoveryRequest {
        email = email == null
                ? null
                : email.trim();
    }
}