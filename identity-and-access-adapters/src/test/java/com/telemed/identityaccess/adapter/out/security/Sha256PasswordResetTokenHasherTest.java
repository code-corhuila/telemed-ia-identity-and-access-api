package com.telemed.identityaccess.adapter.out.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sha256PasswordResetTokenHasherTest {

    private final Sha256PasswordResetTokenHasher hasher =
            new Sha256PasswordResetTokenHasher();

    @Test
    void shouldHashPasswordResetTokenUsingSha256() {

        String hash =
                hasher.hash(
                        "plain-reset-token"
                );

        assertThat(hash)
                .matches("[0-9a-f]{64}");

        assertThat(hash)
                .isEqualTo(
                        "f2d0d1f0457c7d418c65bf66af6a084e"
                                + "304a8f8e978979f8dde24eb0cf12eb9b"
                );
    }

    @Test
    void shouldProduceSameHashForSameToken() {

        String first =
                hasher.hash(
                        "plain-reset-token"
                );

        String second =
                hasher.hash(
                        "plain-reset-token"
                );

        assertThat(first)
                .isEqualTo(second);
    }

    @Test
    void shouldProduceDifferentHashesForDifferentTokens() {

        String first =
                hasher.hash(
                        "plain-reset-token"
                );

        String second =
                hasher.hash(
                        "another-reset-token"
                );

        assertThat(first)
                .isNotEqualTo(second);
    }

    @Test
    void shouldRejectBlankToken() {

        assertThatThrownBy(
                () -> hasher.hash(" ")
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "Password reset token is required."
                );
    }

    @Test
    void shouldRejectNullToken() {

        assertThatThrownBy(
                () -> hasher.hash(null)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "Password reset token is required."
                );
    }
}