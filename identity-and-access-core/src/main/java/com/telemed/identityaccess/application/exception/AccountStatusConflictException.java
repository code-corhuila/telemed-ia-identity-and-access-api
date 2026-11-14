package com.telemed.identityaccess.application.exception;

public final class AccountStatusConflictException extends RuntimeException {

    public enum Reason {
        SELF_DEACTIVATION,
        LAST_ACTIVE_ADMIN
    }

    private final Reason reason;

    public AccountStatusConflictException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
