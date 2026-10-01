package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.domain.model.Role;

import java.util.UUID;

public record SessionResponse(UUID userId, Role role) {
}
