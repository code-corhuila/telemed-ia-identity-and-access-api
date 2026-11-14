package com.telemed.identityaccess.application.exception;

public class LogoutException extends RuntimeException {

    public enum Reason {
        INVALID_REFRESH_TOKEN
    }

    private final Reason reason;

    private LogoutException(
            Reason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    public static LogoutException invalidRefreshToken() {
        return new LogoutException(
                Reason.INVALID_REFRESH_TOKEN,
                "Refresh token is invalid."
        );
    }

    public Reason reason() {
        return reason;
    }
}