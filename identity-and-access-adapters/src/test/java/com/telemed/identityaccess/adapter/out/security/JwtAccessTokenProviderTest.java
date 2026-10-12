package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenProviderTest {

    private static final byte[] SECRET_BYTES =
            new byte[32];

    private static final UUID USER_ID = UUID.fromString(
            "22222222-2222-4222-8222-222222222222"
    );

    @Test
    void shouldIssueTokenWithExpectedClaims() {

        Instant issuedAt =
                Instant.parse("2026-09-25T20:00:00Z");

        Clock clock = Clock.fixed(
                issuedAt,
                ZoneOffset.UTC
        );

        JwtProperties properties = new JwtProperties(
                "telemed-ia-identity-and-access",
                Duration.ofMinutes(15),
                Base64.getEncoder()
                        .encodeToString(SECRET_BYTES)
        );

        JwtAccessTokenProvider provider =
                new JwtAccessTokenProvider(
                        properties,
                        clock
                );

        AccessTokenProviderPort.IssuedAccessToken issued =
                provider.issue(
                        USER_ID,
                        Role.PATIENT
                );

        Jwt jwt = decoder(clock)
                .decode(issued.value());

        assertThat(jwt.getSubject())
                .isEqualTo(USER_ID.toString());

        assertThat(jwt.getClaimAsString("role"))
                .isEqualTo("PATIENT");

        assertThat(jwt.getClaimAsString("iss"))
                .isEqualTo(
                        "telemed-ia-identity-and-access"
                );

        assertThat(jwt.getIssuedAt())
                .isEqualTo(issuedAt);

        assertThat(jwt.getExpiresAt())
                .isEqualTo(
                        issuedAt.plus(
                                Duration.ofMinutes(15)
                        )
                );

        assertThat(issued.expiresAt())
                .isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void shouldRejectInvalidBase64Secret() {

        JwtProperties properties = new JwtProperties(
                "telemed-ia-identity-and-access",
                Duration.ofMinutes(15),
                "not-valid-base64!"
        );

        assertThatThrownBy(() ->
                new JwtAccessTokenProvider(
                        properties,
                        Clock.systemUTC()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid Base64");
    }

    @Test
    void shouldRejectSecretShorterThan256Bits() {

        String weakSecret = Base64.getEncoder()
                .encodeToString(new byte[16]);

        JwtProperties properties = new JwtProperties(
                "telemed-ia-identity-and-access",
                Duration.ofMinutes(15),
                weakSecret
        );

        assertThatThrownBy(() ->
                new JwtAccessTokenProvider(
                        properties,
                        Clock.systemUTC()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void shouldRejectNonPositiveAccessTokenTtl() {

        assertThatThrownBy(() ->
                new JwtProperties(
                        "telemed-ia-identity-and-access",
                        Duration.ZERO,
                        Base64.getEncoder()
                                .encodeToString(SECRET_BYTES)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "TTL must be positive"
                );
    }

    private NimbusJwtDecoder decoder(Clock clock) {

        SecretKey key = new SecretKeySpec(
                SECRET_BYTES,
                "HmacSHA256"
        );

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator();

        timestampValidator.setClock(clock);

        decoder.setJwtValidator(timestampValidator);

        return decoder;
    }
}