package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter extends OncePerRequestFilter {

    private static final String SESSION_PATH = "/api/v1/auth/session";

    private final AccessTokenVerifierPort verifier;
    private final ApiErrorWriter errors;

    public AuthFilter(AccessTokenVerifierPort verifier, ApiErrorWriter errors) {
        this.verifier = verifier;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !SESSION_PATH.equals(request.getRequestURI())
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            errors.write(request, response, 401, "UNAUTHORIZED", "Authentication is required.");
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            errors.write(request, response, 401, "UNAUTHORIZED", "Authentication is required.");
            return;
        }

        try {
            AccessTokenVerifierPort.VerifiedAccessToken verified = verifier.verify(token);
            request.setAttribute(
                    AuthenticatedRequestContext.USER_ID_ATTRIBUTE,
                    verified.userId()
            );
            request.setAttribute(
                    AuthenticatedRequestContext.ROLE_ATTRIBUTE,
                    verified.role()
            );
            filterChain.doFilter(request, response);
        } catch (AccessTokenVerificationException exception) {
            errors.write(request, response, 401, "UNAUTHORIZED", "Access token is invalid or expired.");
        }
    }
}
