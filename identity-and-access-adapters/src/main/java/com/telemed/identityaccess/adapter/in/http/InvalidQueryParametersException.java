package com.telemed.identityaccess.adapter.in.http;

import java.util.Map;

/** Query parameters outside the contract, by parameter name. */
public class InvalidQueryParametersException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidQueryParametersException(Map<String, String> fieldErrors) {
        super("Request validation failed.");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
