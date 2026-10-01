package com.telemed.identityaccess.adapter.in.http;

import java.util.UUID;

public record SessionResponse(
        UUID userId,
        String role
) {
}