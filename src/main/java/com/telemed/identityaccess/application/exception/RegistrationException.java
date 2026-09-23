package com.telemed.identityaccess.application.exception;

public final class RegistrationException extends RuntimeException {

    public enum Reason {
        EMAIL_ALREADY_REGISTERED,
        IDENTITY_DOCUMENT_ALREADY_REGISTERED,
        INVALID_REGISTRATION
    }

    private final Reason reason;

    public RegistrationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}