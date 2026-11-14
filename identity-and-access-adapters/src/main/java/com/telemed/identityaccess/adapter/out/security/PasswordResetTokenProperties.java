package com.telemed.identityaccess.adapter.out.security;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.password-reset-token")
public record PasswordResetTokenProperties(
        @NotNull Duration ttl
) {

    public PasswordResetTokenProperties {
        if (ttl != null
                && (ttl.isZero() || ttl.isNegative())) {

            throw new IllegalArgumentException(
                    "Password reset token TTL must be positive."
            );
        }
    }
}