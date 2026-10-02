package com.telemed.identityaccess.adapter.in.http;

public class MissingAuthenticatedPrincipalException
        extends RuntimeException {

    public MissingAuthenticatedPrincipalException() {
        super("Authenticated principal is missing from the request.");
    }
}