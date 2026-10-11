
package com.telemed.identityaccess.application.exception;

public final class PasswordHashUpdateException
        extends RuntimeException {

    public PasswordHashUpdateException(
            String message
    ) {
        super(message);
    }

    public PasswordHashUpdateException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
