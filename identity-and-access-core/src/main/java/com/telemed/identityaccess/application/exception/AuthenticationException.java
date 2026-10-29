package com.telemed.identityaccess.application.exception;

import java.time.Duration;

public class AuthenticationException extends RuntimeException {

    public enum Reason {
        INVALID_CREDENTIALS,
        ACCOUNT_TEMPORARILY_LOCKED
    }

    private final Reason reason;

    private final Duration retryAfter;

    public AuthenticationException(
            Reason reason,
            String message
    ) {
        this(reason, message, Duration.ZERO);
    }

    private AuthenticationException(
            Reason reason,
            String message,
            Duration retryAfter
    ) {
        super(message);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    public Reason reason() {
        return reason;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException(
                Reason.INVALID_CREDENTIALS,
                "Invalid credentials."
        );
    }

    public static AuthenticationException accountTemporarilyLocked(
            Duration retryAfter
    ) {
        return new AuthenticationException(
                Reason.ACCOUNT_TEMPORARILY_LOCKED,
                "Account temporarily locked.",
                retryAfter
        );
    }
}
