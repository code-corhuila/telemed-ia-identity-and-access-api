package com.telemed.identityaccess.adapter.in.http;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards /internal/accounts (create and look up) before the body is read: 401 without a valid token signed by
 * identity, 403 unless it is a service token of a client in security.account-provisioning.clients.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ServiceCallerFilter extends OncePerRequestFilter {

    private final ServiceTokenVerifierPort serviceTokens;
    private final ApiErrorWriter errors;
    private final Set<String> allowedClients;

    public ServiceCallerFilter(
            ServiceTokenVerifierPort serviceTokens,
            ApiErrorWriter errors,
            @Value("${security.account-provisioning.clients:}") String allowedClients
    ) {
        this.serviceTokens = serviceTokens;
        this.errors = errors;
        this.allowedClients = Arrays.stream(allowedClients.split(","))
                .map(String::trim)
                .filter(client -> !client.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !InternalAccountsController.PATH.equals(
                request.getRequestURI().substring(request.getContextPath().length()))
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : "";

        if (token.isEmpty()) {
            unauthorized(request, response);
            return;
        }

        ServiceTokenVerifierPort.ServiceCaller caller;
        try {
            caller = serviceTokens.verify(token);
        } catch (AccessTokenVerificationException exception) {
            unauthorized(request, response);
            return;
        }

        if (allowedClients.stream().noneMatch(caller::isService)) {
            errors.write(
                    request,
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN",
                    "This caller may not use this endpoint."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void unauthorized(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        errors.write(
                request,
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication is required."
        );
    }
}
