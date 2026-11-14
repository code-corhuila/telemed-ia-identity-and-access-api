package com.telemed.identityaccess.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Idempotency-Key of an account creation and the account it created. */
public interface IdempotencyKeyRepositoryPort {

    Optional<UUID> findUserId(String key);

    void save(String key, UUID userId);
}
