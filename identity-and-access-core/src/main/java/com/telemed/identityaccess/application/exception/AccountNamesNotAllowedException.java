package com.telemed.identityaccess.application.exception;

/** DEC-013: only professionals and administrators look up account names. */
public final class AccountNamesNotAllowedException extends RuntimeException {

    public AccountNamesNotAllowedException() {
        super("Professional or administrator role is required.");
    }
}
