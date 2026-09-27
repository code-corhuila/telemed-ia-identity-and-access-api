package com.telemed.identityaccess.adapter.in.http;

import java.util.List;
import java.util.Map;

public record ApiErrorResponse(
        String error,
        String message,
        List<FieldError> details,
        String traceId
) {

    public record FieldError(String field, String message) {
    }

    public static ApiErrorResponse of(
            String error,
            String message,
            Map<String, String> fieldErrors,
            String traceId
    ) {
        List<FieldError> details = fieldErrors.entrySet().stream()
                .map(entry -> new FieldError(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();

        return new ApiErrorResponse(
                error,
                message,
                details,
                traceId
        );
    }
}