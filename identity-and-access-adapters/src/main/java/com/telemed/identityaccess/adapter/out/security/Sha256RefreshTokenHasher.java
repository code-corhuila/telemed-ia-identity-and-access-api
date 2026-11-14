package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.RefreshTokenHasherPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Sha256RefreshTokenHasher
        implements RefreshTokenHasherPort {

    @Override
    public String hash(String refreshToken) {

        if (refreshToken == null
                || refreshToken.isBlank()) {

            throw new IllegalArgumentException(
                    "Refresh token is required."
            );
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    refreshToken.getBytes(
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