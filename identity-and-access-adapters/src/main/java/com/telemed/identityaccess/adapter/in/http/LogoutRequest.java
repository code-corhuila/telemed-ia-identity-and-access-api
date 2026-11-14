package com.telemed.identityaccess.adapter.in.http;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token is required.")
        String refreshToken
) {
}