package com.telemed.identityaccess.application.exception;

public class AccessTokenVerificationException extends RuntimeException {

    public AccessTokenVerificationException(String message) {
        super(message);
    }

    public AccessTokenVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
