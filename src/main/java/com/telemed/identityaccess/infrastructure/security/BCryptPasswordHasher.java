package com.telemed.identityaccess.infrastructure.security;

import com.telemed.identityaccess.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BCryptPasswordHasher implements PasswordHasherPort {

    private static final int BCRYPT_STRENGTH = 10;

    private static final BCryptPasswordEncoder.BCryptVersion
            BCRYPT_VERSION =
            BCryptPasswordEncoder.BCryptVersion.$2A;

    private static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2a\\$(\\d{2})\\$.*$");

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
        return encoder.matches(
                rawPassword,
                passwordHash
        );
    }

    @Override
    public boolean usesCurrentPolicy(
            String passwordHash
    ) {

        if (passwordHash == null) {
            return false;
        }

        Matcher matcher =
                BCRYPT_PATTERN.matcher(passwordHash);

        if (!matcher.matches()) {
            return false;
        }

        int strength =
                Integer.parseInt(matcher.group(1));

        return strength == BCRYPT_STRENGTH;
    }
}