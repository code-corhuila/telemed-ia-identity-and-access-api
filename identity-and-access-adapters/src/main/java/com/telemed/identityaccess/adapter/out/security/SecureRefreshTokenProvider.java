package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.RefreshTokenProviderPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

public class SecureRefreshTokenProvider
        implements RefreshTokenProviderPort {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public SecureRefreshTokenProvider(
            RefreshTokenProperties properties,
            Clock clock
    ) {
        this(
                properties,
                clock,
                new SecureRandom()
        );
    }

    SecureRefreshTokenProvider(
            RefreshTokenProperties properties,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    public IssuedRefreshToken issue() {

        byte[] randomBytes =
                new byte[TOKEN_BYTES];

        secureRandom.nextBytes(randomBytes);

        String value =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(randomBytes);

        String tokenHash =
                sha256(value);

        Instant expiresAt =
                clock.instant()
                        .plus(properties.ttl());

        return new IssuedRefreshToken(
                value,
                tokenHash,
                expiresAt
        );
    }

    private String sha256(String value) {

        try {
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

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is not available.",
                    exception
            );
        }
    }
}