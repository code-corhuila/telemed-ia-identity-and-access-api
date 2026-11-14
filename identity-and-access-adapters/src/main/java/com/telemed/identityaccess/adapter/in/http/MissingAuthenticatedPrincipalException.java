package com.telemed.identityaccess.adapter.in.http;

public final class MissingAuthenticatedPrincipalException
        extends RuntimeException {

    public MissingAuthenticatedPrincipalException() {
        super("Authenticated principal is missing.");
    }
}