package com.telemed.identityaccess.application.exception;

/** The broker did not confirm an integration event; it stays pending in the outbox. */
public class EventPublicationException extends RuntimeException {

    public EventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
