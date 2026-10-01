package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.domain.model.Role;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class AuthenticatedRequestContext {

    private static final String PRINCIPAL_ATTRIBUTE =
            AuthenticatedRequestContext.class.getName()
                    + ".principal";

    private AuthenticatedRequestContext() {
    }

    public static void set(
            HttpServletRequest request,
            UUID userId,
            Role role
    ) {
        request.setAttribute(
                PRINCIPAL_ATTRIBUTE,
                new AuthenticatedPrincipal(
                        userId,
                        role
                )
        );
    }

    public static AuthenticatedPrincipal require(
            HttpServletRequest request
    ) {

        Object value =
                request.getAttribute(PRINCIPAL_ATTRIBUTE);

        if (!(value instanceof AuthenticatedPrincipal principal)) {
            throw new MissingAuthenticatedPrincipalException();
        }

        return principal;
    }
}