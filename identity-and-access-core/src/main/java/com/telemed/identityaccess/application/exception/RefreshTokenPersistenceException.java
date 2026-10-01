package com.telemed.identityaccess.application.exception;

public class RefreshTokenPersistenceException
        extends RuntimeException {

    public RefreshTokenPersistenceException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}