package com.telemed.identityaccess.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(

        @NotBlank
        String issuer,

        @NotNull
        Duration accessTokenTtl,

        @NotBlank
        String secretBase64

) {

    public JwtProperties {

        if (accessTokenTtl != null
                && (accessTokenTtl.isZero()
                || accessTokenTtl.isNegative())) {

            throw new IllegalArgumentException(
                    "JWT access token TTL must be positive."
            );
        }
    }
}