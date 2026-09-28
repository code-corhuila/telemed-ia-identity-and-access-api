package com.telemed.identityaccess.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort;
import com.telemed.identityaccess.domain.model.Role;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public class JwtAccessTokenProvider
        implements AccessTokenProviderPort {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;

    public JwtAccessTokenProvider(
            JwtProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;

        byte[] secretBytes;

        try {
            secretBytes = Base64.getDecoder()
                    .decode(properties.secretBase64());

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "JWT secret must be valid Base64.",
                    exception
            );
        }

        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret must contain at least 256 bits."
            );
        }

        SecretKey secretKey = new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );

        JWKSource<SecurityContext> jwkSource =
                new ImmutableSecret<>(secretKey);

        this.encoder = new NimbusJwtEncoder(jwkSource);
    }

    @Override
    public IssuedAccessToken issue(
            Long userId,
            Role role
    ) {

        Instant issuedAt = clock.instant();
        Instant expiresAt =
                issuedAt.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("role", role.name())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String token = encoder.encode(
                JwtEncoderParameters.from(
                        header,
                        claims
                )
        ).getTokenValue();

        return new IssuedAccessToken(
                token,
                expiresAt
        );
    }
}