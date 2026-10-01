package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.domain.model.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {

    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute(AuthenticatedRequestContext.USER_ID_ATTRIBUTE);
        Role role = (Role) request.getAttribute(AuthenticatedRequestContext.ROLE_ATTRIBUTE);
        return new SessionResponse(userId, role);
    }
}
