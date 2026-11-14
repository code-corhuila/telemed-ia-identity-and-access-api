package com.telemed.identityaccess.adapter.in.http;

public record PasswordRecoveryResponse(
        String message
) {

    private static final String GENERIC_MESSAGE =
            "If the email is registered, password recovery instructions will be sent.";

    public static PasswordRecoveryResponse generic() {
        return new PasswordRecoveryResponse(
                GENERIC_MESSAGE
        );
    }
}