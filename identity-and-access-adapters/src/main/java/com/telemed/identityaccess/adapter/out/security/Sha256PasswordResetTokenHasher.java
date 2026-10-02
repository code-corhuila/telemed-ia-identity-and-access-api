package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.PasswordResetTokenHasherPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class Sha256PasswordResetTokenHasher
        implements PasswordResetTokenHasherPort {

    @Override
    public String hash(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Password reset token is required."
            );
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashed =
                    digest.digest(
                            token.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(hashed);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available.",
                    exception
            );
        }
    }
}