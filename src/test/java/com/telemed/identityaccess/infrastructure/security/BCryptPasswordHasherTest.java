package com.telemed.identityaccess.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptPasswordHasherTest {

    private static final String RAW_PASSWORD =
            "Secret123!";

    private final BCryptPasswordHasher hasher =
            new BCryptPasswordHasher();

    @Test
    void shouldHashPasswordUsingCurrentBcryptPolicy() {

        String hash = hasher.hash(RAW_PASSWORD);

        assertNotNull(hash);
        assertNotEquals(RAW_PASSWORD, hash);

        assertTrue(
                hash.startsWith("$2a$10$"),
                "New password hashes must use BCrypt $2a$ with strength 10."
        );

        assertTrue(
                hasher.matches(
                        RAW_PASSWORD,
                        hash
                )
        );
    }

    @Test
    void shouldRejectIncorrectPassword() {

        String hash = hasher.hash(RAW_PASSWORD);

        assertFalse(
                hasher.matches(
                        "WrongPassword123!",
                        hash
                )
        );
    }

    @Test
    void shouldMatchCompatibleBcrypt2BVariant() {

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder(
                        BCryptPasswordEncoder.BCryptVersion.$2B,
                        10
                );

        String hash = encoder.encode(RAW_PASSWORD);

        assertTrue(
                hasher.matches(
                        RAW_PASSWORD,
                        hash
                )
        );
    }

    @Test
    void shouldMatchCompatibleBcrypt2YVariant() {

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder(
                        BCryptPasswordEncoder.BCryptVersion.$2Y,
                        10
                );

        String hash = encoder.encode(RAW_PASSWORD);

        assertTrue(
                hasher.matches(
                        RAW_PASSWORD,
                        hash
                )
        );
    }

    @Test
    void shouldMatchLegacyBcryptStrength() {

        BCryptPasswordEncoder legacyEncoder =
                new BCryptPasswordEncoder(
                        BCryptPasswordEncoder.BCryptVersion.$2A,
                        8
                );

        String legacyHash =
                legacyEncoder.encode(RAW_PASSWORD);

        assertTrue(
                hasher.matches(
                        RAW_PASSWORD,
                        legacyHash
                )
        );
    }

    @Test
void shouldRejectMalformedPasswordHashWithoutThrowing() {

    assertFalse(
            hasher.matches(
                    "Secret123!",
                    "not-a-valid-bcrypt-hash"
            )
    );
}

    @Test
    void shouldRejectIncorrectPasswordAgainstLegacyHash() {

        BCryptPasswordEncoder legacyEncoder =
                new BCryptPasswordEncoder(
                        BCryptPasswordEncoder.BCryptVersion.$2A,
                        8
                );

        String legacyHash =
                legacyEncoder.encode(RAW_PASSWORD);

        assertFalse(
                hasher.matches(
                        "WrongPassword123!",
                        legacyHash
                )
        );
    }
}