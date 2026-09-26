package com.telemed.identityaccess.adapter.in.http;

import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors
) {
}