package com.telemed.identityaccess.application.exception;

public final class AccountProvisioningException extends RuntimeException {

    public enum Reason {
        INVALID_IDEMPOTENCY_KEY,
        IDEMPOTENCY_KEY_REUSED
    }

    private final Reason reason;

    public AccountProvisioningException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
