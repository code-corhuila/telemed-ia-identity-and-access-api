package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.PasswordResetTokenProviderPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SecurePasswordResetTokenProviderTest {

    private static final Instant NOW =
            Instant.parse("2026-10-02T15:00:00Z");

    @Test
    void shouldIssueSecurePasswordResetToken() {

        PasswordResetTokenProperties properties =
                new PasswordResetTokenProperties(
                        Duration.ofMinutes(30)
                );

        SecurePasswordResetTokenProvider provider =
                new SecurePasswordResetTokenProvider(
                        properties,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );

        PasswordResetTokenProviderPort
                .IssuedPasswordResetToken issued =
                provider.issue();

        assertThat(issued.value())
                .isNotBlank();

        assertThat(issued.tokenHash())
                .matches("[0-9a-f]{64}");

        assertThat(issued.expiresAt())
                .isEqualTo(
                        NOW.plus(
                                Duration.ofMinutes(30)
                        )
                );
    }

    @Test
    void shouldGenerateDifferentTokens() {

        PasswordResetTokenProperties properties =
                new PasswordResetTokenProperties(
                        Duration.ofMinutes(30)
                );

        SecurePasswordResetTokenProvider provider =
                new SecurePasswordResetTokenProvider(
                        properties,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );

        var first = provider.issue();
        var second = provider.issue();

        assertThat(first.value())
                .isNotEqualTo(second.value());

        assertThat(first.tokenHash())
                .isNotEqualTo(second.tokenHash());
    }
}