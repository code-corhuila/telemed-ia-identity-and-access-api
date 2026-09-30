package com.telemed.identityaccess.adapter.out.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenVerifierTest {

    @Test
    void shouldVerifyValidRs256Token() {
        KeyPair pair = TestRsaKeys.generate();
        Instant now = Instant.now();
        JwtProperties properties = properties();
        JwtAccessTokenProvider provider = new JwtAccessTokenProvider(
                properties,
                Clock.fixed(now, ZoneOffset.UTC),
                TestRsaKeys.publicKey(pair),
                TestRsaKeys.privateKey(pair)
        );
        JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(
                properties,
                TestRsaKeys.publicKey(pair)
        );
        UUID userId = UUID.randomUUID();

        var verified = verifier.verify(provider.issue(userId, Role.ADMIN).value());

        assertThat(verified.userId()).isEqualTo(userId);
        assertThat(verified.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldRejectTokenSignedWithDifferentRsaKey() {
        KeyPair issuerPair = TestRsaKeys.generate();
        KeyPair verifierPair = TestRsaKeys.generate();
        Instant now = Instant.now();
        JwtAccessTokenProvider provider = new JwtAccessTokenProvider(
                properties(),
                Clock.fixed(now, ZoneOffset.UTC),
                TestRsaKeys.publicKey(issuerPair),
                TestRsaKeys.privateKey(issuerPair)
        );
        JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(
                properties(),
                TestRsaKeys.publicKey(verifierPair)
        );

        assertThatThrownBy(() -> verifier.verify(
                provider.issue(UUID.randomUUID(), Role.PATIENT).value()
        )).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectHs256EvenWhenTokenClaimsLookValid() throws Exception {
        byte[] secret = "01234567890123456789012345678901".getBytes();
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("telemed-ia-identity-and-access")
                .subject(UUID.randomUUID().toString())
                .claim("role", "PATIENT")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret));

        JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(
                properties(),
                TestRsaKeys.publicKey(TestRsaKeys.generate())
        );

        assertThatThrownBy(() -> verifier.verify(jwt.serialize()))
                .isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        KeyPair pair = TestRsaKeys.generate();
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        JwtAccessTokenProvider provider = new JwtAccessTokenProvider(
                properties(),
                Clock.fixed(issuedAt, ZoneOffset.UTC),
                TestRsaKeys.publicKey(pair),
                TestRsaKeys.privateKey(pair)
        );
        JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(
                properties(),
                TestRsaKeys.publicKey(pair)
        );

        assertThatThrownBy(() -> verifier.verify(
                provider.issue(UUID.randomUUID(), Role.PATIENT).value()
        )).isInstanceOf(AccessTokenVerificationException.class);
    }

    private JwtProperties properties() {
        return new JwtProperties(
                "telemed-ia-identity-and-access",
                Duration.ofHours(1),
                null, null, null, null
        );
    }
}
