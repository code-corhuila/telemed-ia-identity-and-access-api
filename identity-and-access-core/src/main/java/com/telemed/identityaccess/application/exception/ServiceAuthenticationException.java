package com.telemed.identityaccess.application.exception;

/** Unknown client or wrong secret: deliberately the same failure, so neither can be told apart. */
public class ServiceAuthenticationException extends RuntimeException {

    public ServiceAuthenticationException() {
        super("Invalid service client credentials.");
    }
}
