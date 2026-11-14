package com.telemed.identityaccess.adapter.in.http;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(

        @NotBlank(message = "Reset token is required.")
        String token,

        @NotBlank(message = "New password is required.")
        String newPassword

) {
}