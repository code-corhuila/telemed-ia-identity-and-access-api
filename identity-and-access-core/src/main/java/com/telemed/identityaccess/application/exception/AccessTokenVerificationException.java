package com.telemed.identityaccess.application.exception;

public class AccessTokenVerificationException
        extends RuntimeException {

    public enum Reason {
        EXPIRED,
        INVALID_SIGNATURE,
        UNSUPPORTED_ALGORITHM,
        MALFORMED_TOKEN,
        INVALID_CLAIMS
    }

    private final Reason reason;

    public AccessTokenVerificationException(
            Reason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    public AccessTokenVerificationException(
            Reason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}