package com.telemed.identityaccess.application.exception;

public final class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("Account not found.");
    }
}
