package com.telemed.identityaccess.adapter.in.http;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(AuthRoutes.BASE)
public class SessionController {

    @GetMapping(AuthRoutes.SESSION)
    public SessionResponse session(HttpServletRequest request) {

        AuthenticatedPrincipal principal =
                AuthenticatedRequestContext.require(request);

        return new SessionResponse(
                principal.userId(),
                toApiRole(principal.role())
        );
    }

    private static String toApiRole(
            com.telemed.identityaccess.domain.model.Role role
    ) {
        return switch (role) {
            case PATIENT -> "PATIENT";
            case PROFESSIONAL -> "PROFESSIONAL";
            case ADMIN -> "ADMIN";
        };
    }
}