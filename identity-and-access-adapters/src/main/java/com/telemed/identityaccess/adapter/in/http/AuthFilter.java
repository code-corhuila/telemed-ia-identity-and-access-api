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
        return !AuthRoutes.SESSION_PATH.equals(pathWithinApplication(request))
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
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