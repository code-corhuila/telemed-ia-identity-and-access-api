package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasherPort {

    private static final int BCRYPT_STRENGTH = 10;

    private static final BCryptPasswordEncoder.BCryptVersion
            BCRYPT_VERSION =
            BCryptPasswordEncoder.BCryptVersion.$2A;

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher() {
        this.encoder = new BCryptPasswordEncoder(
                BCRYPT_VERSION,
                BCRYPT_STRENGTH
        );
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(
            String rawPassword,
            String passwordHash
    ) {

        if (rawPassword == null
                || passwordHash == null
                || passwordHash.isBlank()) {
            return false;
        }

        try {
            return encoder.matches(
                    rawPassword,
                    passwordHash
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(
            String passwordHash
    ) {

        if (passwordHash == null
                || passwordHash.isBlank()) {
            return false;
        }

        try {
            return encoder.upgradeEncoding(
                    passwordHash
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}