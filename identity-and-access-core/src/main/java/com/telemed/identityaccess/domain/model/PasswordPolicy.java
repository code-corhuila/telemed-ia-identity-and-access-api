package com.telemed.identityaccess.domain.model;

import java.nio.charset.StandardCharsets;

/**
 * Length rules shared by registration and password reset.
 *
 * <p>The upper bound is BCrypt's: it only uses the first 72 bytes and the
 * encoder rejects longer input.
 */
public final class PasswordPolicy {

    public static final int MIN_CHARACTERS = 8;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean isTooShort(
            String password
    ) {
        return password.codePointCount(0, password.length())
                < MIN_CHARACTERS;
    }

    public static boolean isTooLong(
            String password
    ) {
        return password.getBytes(StandardCharsets.UTF_8).length
                > MAX_UTF8_BYTES;
    }
}
