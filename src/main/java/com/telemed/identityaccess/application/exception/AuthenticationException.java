package com.telemed.identityaccess.application.exception;

public class AuthenticationException extends RuntimeException {

    public enum Reason {
        INVALID_CREDENTIALS
    }

    private final Reason reason;

    public AuthenticationException(
            Reason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException(
                Reason.INVALID_CREDENTIALS,
                "Invalid credentials."
        );
    }
}