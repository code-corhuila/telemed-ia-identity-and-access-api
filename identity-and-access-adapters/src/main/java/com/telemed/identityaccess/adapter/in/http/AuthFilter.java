package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.domain.model.Role;
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

    private final AccessTokenVerifierPort verifier;
    private final ApiErrorWriter errors;

    public AuthFilter(
            AccessTokenVerifierPort verifier,
            ApiErrorWriter errors
    ) {
        this.verifier = verifier;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);

        return !(AuthRoutes.SESSION_PATH.equals(path) || isAdminPath(path))
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private static boolean isAdminPath(String path) {
        return path.equals(AuthRoutes.ADMIN_ACCOUNTS_PATH)
                || path.startsWith(AuthRoutes.ADMIN_ACCOUNTS_PATH + "/");
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null
                && !contextPath.isEmpty()
                && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }

        return requestUri;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            errors.write(
                    request,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Authentication is required."
            );
            return;
        }

        String token = authorization
                .substring("Bearer ".length())
                .trim();

        if (token.isEmpty()) {
            errors.write(
                    request,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Authentication is required."
            );
            return;
        }

        try {
            AccessTokenVerifierPort.VerifiedAccessToken verified =
                    verifier.verify(token);

            // HU-05 AC5: administration is for the ADMIN role only.
            if (isAdminPath(pathWithinApplication(request))
                    && verified.role() != Role.ADMIN) {
                errors.write(
                        request,
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN",
                        "Administrator role is required."
                );
                return;
            }

            AuthenticatedRequestContext.set(
                    request,
                    verified.userId(),
                    verified.role()
            );

            filterChain.doFilter(request, response);

        } catch (AccessTokenVerificationException exception) {
            errors.write(
                    request,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Access token is invalid or expired."
            );
        }
    }
}