package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenProviderTest {

    private static final UUID USER_ID = UUID.fromString(
            "22222222-2222-4222-8222-222222222222"
    );

    private static RSAPrivateKey privateKey;
    private static RSAPublicKey publicKey;

    @BeforeAll
    static void generateKeys() throws Exception {

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048);

        KeyPair keyPair = generator.generateKeyPair();

        privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        publicKey =
                (RSAPublicKey) keyPair.getPublic();
    }

    @Test
    void shouldIssueRs256TokenWithExpectedClaims() {

        Instant issuedAt =
                Instant.parse("2026-09-25T20:00:00Z");

        Clock clock = Clock.fixed(
                issuedAt,
                ZoneOffset.UTC
        );

        JwtProperties properties = new JwtProperties(
                "telemed-ia-identity-and-access",
                Duration.ofHours(1),
                "unused-private-key-path",
                "unused-public-key-path"
        );

        JwtAccessTokenProvider provider =
                new JwtAccessTokenProvider(
                        properties,
                        privateKey,
                        publicKey,
                        clock
                );

        AccessTokenProviderPort.IssuedAccessToken issued =
                provider.issue(
                        USER_ID,
                        Role.PATIENT
                );

        Jwt jwt = decoder(publicKey, clock)
                .decode(issued.value());

        assertThat(jwt.getHeaders().get("alg"))
                .isEqualTo("RS256");

        assertThat(jwt.getSubject())
                .isEqualTo(USER_ID.toString());

        assertThat(jwt.getClaimAsString("role"))
                .isEqualTo("PATIENT");

        assertThat(jwt.getIssuer())
                .isEqualTo(
                        "telemed-ia-identity-and-access"
                );

        assertThat(jwt.getIssuedAt())
                .isEqualTo(issuedAt);

        assertThat(jwt.getExpiresAt())
                .isEqualTo(
                        issuedAt.plus(Duration.ofHours(1))
                );

        assertThat(jwt.getId())
                .isNotBlank();

        assertThat(issued.expiresAt())
                .isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void shouldRejectTokenSignedWithDifferentPrivateKey()
            throws Exception {

        Instant issuedAt =
                Instant.parse("2026-09-25T20:00:00Z");

        Clock clock = Clock.fixed(
                issuedAt,
                ZoneOffset.UTC
        );

        JwtProperties properties = new JwtProperties(
                "telemed-ia-identity-and-access",
                Duration.ofHours(1),
                "unused-private-key-path",
                "unused-public-key-path"
        );

        JwtAccessTokenProvider provider =
                new JwtAccessTokenProvider(
                        properties,
                        privateKey,
                        publicKey,
                        clock
                );

        String token = provider.issue(
                USER_ID,
                Role.PATIENT
        ).value();

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048);

        RSAPublicKey anotherPublicKey =
                (RSAPublicKey) generator
                        .generateKeyPair()
                        .getPublic();

        assertThatThrownBy(() ->
                decoder(
                        anotherPublicKey,
                        clock
                ).decode(token)
        );
    }

    @Test
    void shouldRejectNonPositiveAccessTokenTtl() {

        assertThatThrownBy(() ->
                new JwtProperties(
                        "telemed-ia-identity-and-access",
                        Duration.ZERO,
                        "private.pem",
                        "public.pem"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "TTL must be positive"
                );
    }

    private NimbusJwtDecoder decoder(
            RSAPublicKey key,
            Clock clock
    ) {

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withPublicKey(key)
                        .signatureAlgorithm(
                                SignatureAlgorithm.RS256
                        )
                        .build();

        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator();

        timestampValidator.setClock(clock);

        decoder.setJwtValidator(timestampValidator);

        return decoder;
    }
}