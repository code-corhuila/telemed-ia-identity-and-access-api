package com.telemed.identityaccess.application.exception;

public class PasswordResetException
        extends RuntimeException {

    public enum Reason {
        INVALID_RESET_TOKEN,
        INVALID_NEW_PASSWORD
    }

    private final Reason reason;

    private PasswordResetException(
            Reason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    public static PasswordResetException invalidResetToken() {
        return new PasswordResetException(
                Reason.INVALID_RESET_TOKEN,
                "Password reset token is invalid or expired."
        );
    }

    public static PasswordResetException invalidNewPassword() {
        return new PasswordResetException(
                Reason.INVALID_NEW_PASSWORD,
                "New password is invalid."
        );
    }

    public Reason reason() {
        return reason;
    }
}