package com.telemed.identityaccess.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher =
            new BCryptPasswordHasher();

    @Test
    void shouldHashPasswordUsingBCrypt() {

        String rawPassword = "Secret123!";

        String hash = hasher.hash(rawPassword);

        assertNotNull(hash);
        assertNotEquals(rawPassword, hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(hasher.matches(rawPassword, hash));
    }

    @Test
    void shouldRejectIncorrectPassword() {

        String hash = hasher.hash("Secret123!");

        assertFalse(
                hasher.matches("WrongPassword123!", hash)
        );
    }
}