package com.telemed.identityaccess.application.exception;

public class RefreshSessionException extends RuntimeException {

    public enum Reason {
        INVALID_REFRESH_TOKEN
    }

    private final Reason reason;

    private RefreshSessionException(
            Reason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    public static RefreshSessionException invalidRefreshToken() {
        return new RefreshSessionException(
                Reason.INVALID_REFRESH_TOKEN,
                "Refresh token is invalid or expired."
        );
    }

    public Reason reason() {
        return reason;
    }
}