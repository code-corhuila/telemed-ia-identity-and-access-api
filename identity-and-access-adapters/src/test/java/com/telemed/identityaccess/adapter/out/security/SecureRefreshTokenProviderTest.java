package com.telemed.identityaccess.adapter.out.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRefreshTokenProviderTest {

    @Test
    void shouldIssueRefreshTokenAndExposeOnlyItsHashForPersistence()
            throws Exception {

        Instant now =
                Instant.parse("2026-10-01T13:00:00Z");

        RefreshTokenProperties properties =
                new RefreshTokenProperties(
                        Duration.ofDays(7)
                );

        SecureRefreshTokenProvider provider =
                new SecureRefreshTokenProvider(
                        properties,
                        Clock.fixed(
                                now,
                                ZoneOffset.UTC
                        )
                );

        var issued = provider.issue();

        assertThat(issued.value())
                .isNotBlank();

        assertThat(issued.tokenHash())
                .isNotEqualTo(issued.value());

        assertThat(issued.tokenHash())
                .isEqualTo(
                        sha256(issued.value())
                );

        assertThat(issued.expiresAt())
                .isEqualTo(
                        now.plus(Duration.ofDays(7))
                );
    }

    @Test
    void shouldIssueDifferentRefreshTokens() {

        RefreshTokenProperties properties =
                new RefreshTokenProperties(
                        Duration.ofDays(7)
                );

        SecureRefreshTokenProvider provider =
                new SecureRefreshTokenProvider(
                        properties,
                        Clock.systemUTC()
                );

        var first = provider.issue();
        var second = provider.issue();

        assertThat(first.value())
                .isNotEqualTo(second.value());

        assertThat(first.tokenHash())
                .isNotEqualTo(second.tokenHash());
    }

    private String sha256(String value)
            throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        return HexFormat.of()
                .formatHex(
                        digest.digest(
                                value.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
                );
    }
}