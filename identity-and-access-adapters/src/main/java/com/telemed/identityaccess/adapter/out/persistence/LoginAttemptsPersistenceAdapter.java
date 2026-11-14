package com.telemed.identityaccess.adapter.out.persistence;

import com.telemed.identityaccess.application.port.out.LoginAttemptsRepositoryPort;
import com.telemed.identityaccess.domain.model.LoginAttempts;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Stores the counter under the SHA-256 of the normalized email, so the raw
 * email of a login attempt (often an unknown one) is never written.
 */
@Repository
public class LoginAttemptsPersistenceAdapter
        implements LoginAttemptsRepositoryPort {

    private final SpringDataLoginAttemptsRepository attempts;

    public LoginAttemptsPersistenceAdapter(
            SpringDataLoginAttemptsRepository attempts
    ) {
        this.attempts = attempts;
    }

    @Override
    public Optional<LoginAttempts> find(String email) {
        return attempts.findById(hash(email))
                .map(LoginAttemptsJpaEntity::toDomain);
    }

    @Override
    public void save(String email, LoginAttempts loginAttempts) {
        attempts.saveAndFlush(
                LoginAttemptsJpaEntity.of(hash(email), loginAttempts)
        );
    }

    private static String hash(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    email.trim()
                            .toLowerCase(Locale.ROOT)
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
