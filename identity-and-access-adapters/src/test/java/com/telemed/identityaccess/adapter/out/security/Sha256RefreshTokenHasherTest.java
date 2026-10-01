package com.telemed.identityaccess.adapter.out.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256RefreshTokenHasherTest {

    @Test
    void shouldHashRefreshTokenUsingSha256() throws Exception {

        Sha256RefreshTokenHasher hasher =
                new Sha256RefreshTokenHasher();

        String refreshToken =
                "plain-refresh-token";

        String expected =
                HexFormat.of()
                        .formatHex(
                                MessageDigest
                                        .getInstance("SHA-256")
                                        .digest(
                                                refreshToken.getBytes(
                                                        StandardCharsets.UTF_8
                                                )
                                        )
                        );

        String actual =
                hasher.hash(refreshToken);

        assertThat(actual)
                .isEqualTo(expected);

        assertThat(actual)
                .hasSize(64);
    }

    @Test
    void shouldProduceSameHashForSameRefreshToken() {

        Sha256RefreshTokenHasher hasher =
                new Sha256RefreshTokenHasher();

        String first =
                hasher.hash("same-refresh-token");

        String second =
                hasher.hash("same-refresh-token");

        assertThat(first)
                .isEqualTo(second);
    }
}