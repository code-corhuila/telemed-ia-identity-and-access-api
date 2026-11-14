package com.telemed.identityaccess.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void shouldAcceptPasswordsFromEightCharactersToSeventyTwoBytes() {
        assertThat(PasswordPolicy.isTooShort("Secret1!")).isFalse();
        assertThat(PasswordPolicy.isTooLong("a".repeat(72))).isFalse();
    }

    @Test
    void shouldCountCharactersByCodePoint() {
        // Four emoji: 4 characters but 8 UTF-16 units.
        assertThat(PasswordPolicy.isTooShort("😀😀😀😀")).isTrue();
    }

    @Test
    void shouldRejectPasswordsShorterThanEightCharacters() {
        assertThat(PasswordPolicy.isTooShort("Secret1")).isTrue();
    }

    @Test
    void shouldRejectPasswordsLongerThanSeventyTwoUtf8Bytes() {
        assertThat(PasswordPolicy.isTooLong("a".repeat(73))).isTrue();
        assertThat(PasswordPolicy.isTooLong("ñ".repeat(37))).isTrue();
    }
}
