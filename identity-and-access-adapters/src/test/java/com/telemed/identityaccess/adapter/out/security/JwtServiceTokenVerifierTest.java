package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort.ServiceCaller;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Internal endpoints read who is calling from a token that identity signed itself. */
class JwtServiceTokenVerifierTest {

    private static final String ISSUER = "telemed-ia-identity-and-access";
    private static final Instant NOW = Instant.parse("2026-10-24T15:00:00Z");

    private final KeyPair keys = TestRsaKeys.generate();

    private final JwtServiceTokenVerifier verifier = new JwtServiceTokenVerifier(
            ISSUER, TestRsaKeys.publicKey(keys), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void readsTheClientAndRoleOfAServiceToken() {
        String token = provider(ISSUER, keys, NOW).issue("professional-management").value();

        assertThat(verifier.verify(token))
                .isEqualTo(new ServiceCaller("professional-management", "SERVICE"));
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        String token = provider(ISSUER, TestRsaKeys.generate(), NOW).issue("professional-management").value();

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void rejectsATokenOfAnotherIssuer() {
        String token = provider("someone-else", keys, NOW).issue("professional-management").value();

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = provider(ISSUER, keys, NOW.minusSeconds(3600)).issue("professional-management").value();

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void rejectsSomethingThatIsNotAToken() {
        assertThatThrownBy(() -> verifier.verify("not-a-token"))
                .isInstanceOf(AccessTokenVerificationException.class);
    }

    private static JwtServiceTokenProvider provider(String issuer, KeyPair keys, Instant now) {
        return new JwtServiceTokenProvider(issuer, Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC),
                TestRsaKeys.publicKey(keys), TestRsaKeys.privateKey(keys));
    }
}
