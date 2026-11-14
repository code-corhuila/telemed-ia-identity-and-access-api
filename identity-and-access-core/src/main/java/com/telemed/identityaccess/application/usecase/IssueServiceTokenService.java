package com.telemed.identityaccess.application.usecase;

import com.telemed.identityaccess.application.exception.ServiceAuthenticationException;
import com.telemed.identityaccess.application.port.in.IssueServiceTokenUseCase;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort.IssuedAccessToken;
import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import com.telemed.identityaccess.application.port.out.ServiceClientRegistryPort;
import com.telemed.identityaccess.application.port.out.ServiceTokenProviderPort;

/** Client credentials for services. Unknown clients cost the same work as known ones. */
public class IssueServiceTokenService implements IssueServiceTokenUseCase {

    // Compared when the client is unknown, so response time does not reveal which ids exist.
    private static final String UNKNOWN_CLIENT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z/9N2n8OeYdLs7ZyO6VQZ8Kq";

    private final ServiceClientRegistryPort clients;
    private final PasswordHasherPort hasher;
    private final ServiceTokenProviderPort tokens;

    public IssueServiceTokenService(ServiceClientRegistryPort clients, PasswordHasherPort hasher,
                                    ServiceTokenProviderPort tokens) {
        this.clients = clients;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    @Override
    public Result issue(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ServiceAuthenticationException();
        }
        String hash = clients.secretHashOf(clientId).orElse(null);
        boolean matches = hasher.matches(clientSecret, hash == null ? UNKNOWN_CLIENT_HASH : hash);
        if (hash == null || !matches) {
            throw new ServiceAuthenticationException();
        }
        IssuedAccessToken token = tokens.issue(clientId);
        return new Result(token.value(), token.expiresAt());
    }
}
