package com.telemed.identityaccess.application.port.in;

import java.time.Instant;

/** Norma 5.7: a service (document generation, the worker...) gets its own token from identity. */
public interface IssueServiceTokenUseCase {

    Result issue(String clientId, String clientSecret);

    record Result(String accessToken, Instant expiresAt) {
    }
}
