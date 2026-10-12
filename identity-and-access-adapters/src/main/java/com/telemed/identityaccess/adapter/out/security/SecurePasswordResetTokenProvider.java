package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.PasswordResetTokenProviderPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

public class SecurePasswordResetTokenProvider
        implements PasswordResetTokenProviderPort {

    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public SecurePasswordResetTokenProvider(
            PasswordResetTokenProperties properties,
            Clock clock
    ) {
        this(
                properties,
                clock,
                new SecureRandom()
        );
    }

    SecurePasswordResetTokenProvider(
            PasswordResetTokenProperties properties,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    public IssuedPasswordResetToken issue() {

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

        return new IssuedPasswordResetToken(
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