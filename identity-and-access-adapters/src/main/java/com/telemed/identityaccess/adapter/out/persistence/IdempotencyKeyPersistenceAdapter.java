package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.exception.AccountProvisioningException;
import com.telemed.identityaccess.application.port.out.IdempotencyKeyRepositoryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccountProvisioningException.Reason.IDEMPOTENCY_KEY_REUSED;

/**
 * identity_idempotency_key, owned by the identity -db repository. Plain SQL so a key
 * is only ever inserted, never merged into an existing row.
 */
@Repository
public class IdempotencyKeyPersistenceAdapter
        implements IdempotencyKeyRepositoryPort {

    private final JdbcTemplate jdbc;

    public IdempotencyKeyPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findUserId(String key) {
        return jdbc.query(
                        "SELECT user_id FROM identity_idempotency_key WHERE key_value = ?",
                        (row, index) -> row.getObject("user_id", UUID.class),
                        key
                )
                .stream()
                .findFirst();
    }

    @Override
    public void save(String key, UUID userId) {
        try {
            jdbc.update(
                    "INSERT INTO identity_idempotency_key (key_value, user_id) VALUES (?, ?)",
                    key,
                    userId
            );

        } catch (DuplicateKeyException exception) {
            // Another request with the same key but other data was stored first.
            throw new AccountProvisioningException(
                    IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used for a different request."
            );
        }
    }
}
