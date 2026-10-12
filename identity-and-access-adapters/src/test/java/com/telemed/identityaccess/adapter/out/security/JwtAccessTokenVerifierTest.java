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

import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.EXPIRED;
import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.INVALID_SIGNATURE;
import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.MALFORMED_TOKEN;
import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.UNSUPPORTED_ALGORITHM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenVerifierTest {

    @Test
    void shouldVerifyValidRs256TokenAndPreserveTokenId() {

        KeyPair pair = TestRsaKeys.generate();

        Instant now =
                Instant.parse("2026-09-30T20:00:00Z");

        Clock clock = Clock.fixed(
                now,
                ZoneOffset.UTC
        );

        JwtProperties properties = properties();

        JwtAccessTokenProvider provider =
                new JwtAccessTokenProvider(
                        properties,
                        clock,
                        TestRsaKeys.publicKey(pair),
                        TestRsaKeys.privateKey(pair)
                );

        JwtAccessTokenVerifier verifier =
                new JwtAccessTokenVerifier(
                        properties,
                        TestRsaKeys.publicKey(pair),
                        clock
                );

        UUID userId = UUID.randomUUID();

        var issued = provider.issue(
                userId,
                Role.ADMIN
        );

        var verified = verifier.verify(
                issued.value()
        );

        assertThat(verified.userId())
                .isEqualTo(userId);

        assertThat(verified.role())
                .isEqualTo(Role.ADMIN);

        assertThat(verified.tokenId())
                .isNotBlank();

        assertThat(verified.expiresAt())
                .isEqualTo(issued.expiresAt());
    }

    @Test
    void shouldClassifyTokenSignedWithDifferentRsaKeyAsInvalidSignature() {

        KeyPair issuerPair =
                TestRsaKeys.generate();

        KeyPair verifierPair =
                TestRsaKeys.generate();

        Instant now =
                Instant.parse("2026-09-30T20:00:00Z");

        Clock clock = Clock.fixed(
                now,
                ZoneOffset.UTC
        );

        JwtAccessTokenProvider provider =
                new JwtAccessTokenProvider(
                        properties(),
                        clock,
                        TestRsaKeys.publicKey(
                                issuerPair
                        ),
                        TestRsaKeys.privateKey(
                                issuerPair
                        )
                );

        JwtAccessTokenVerifier verifier =
                new JwtAccessTokenVerifier(
                        properties(),
                        TestRsaKeys.publicKey(
                                verifierPair
                        ),
                        clock
                );

        assertThatThrownBy(() ->
                verifier.verify(
                        provider.issue(
                                UUID.randomUUID(),
                                Role.PATIENT
                        ).value()
                )
        )
                .isInstanceOfSatisfying(
                        AccessTokenVerificationException.class,
                        exception ->
                                assertThat(
                                        exception.reason()
                                ).isEqualTo(
                                        INVALID_SIGNATURE
                                )
                );
    }

    @Test
    void shouldClassifyHs256AsUnsupportedAlgorithm()
            throws Exception {

        byte[] secret =
                "01234567890123456789012345678901"
                        .getBytes();

        Instant now =
                Instant.parse("2026-09-30T20:00:00Z");

        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(
                                "telemed-ia-identity-and-access"
                        )
                        .subject(
                                UUID.randomUUID()
                                        .toString()
                        )
                        .jwtID(
                                UUID.randomUUID()
                                        .toString()
                        )
                        .claim(
                                "role",
                                "PATIENT"
                        )
                        .issueTime(
                                Date.from(now)
                        )
                        .expirationTime(
                                Date.from(
                                        now.plus(
                                                Duration.ofHours(1)
                                        )
                                )
                        )
                        .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader(
                        JWSAlgorithm.HS256
                ),
                claims
        );

        jwt.sign(
                new MACSigner(secret)
        );

        JwtAccessTokenVerifier verifier =
                new JwtAccessTokenVerifier(
                        properties(),
                        TestRsaKeys.publicKey(
                                TestRsaKeys.generate()
                        ),
                        Clock.fixed(
                                now,
                                ZoneOffset.UTC
                        )
                );

        assertThatThrownBy(() ->
                verifier.verify(jwt.serialize())
        )
                .isInstanceOfSatisfying(
                        AccessTokenVerificationException.class,
                        exception ->
                                assertThat(
                                        exception.reason()
                                ).isEqualTo(
                                        UNSUPPORTED_ALGORITHM
                                )
                );
    }

    @Test
    void shouldClassifyExpiredTokenAsExpired() {

        KeyPair pair =
                TestRsaKeys.generate();

        Instant verificationTime =
                Instant.parse("2026-09-30T20:00:00Z");

        Instant issuedAt =
                verificationTime.minus(
                        Duration.ofHours(2)
                );

        JwtAccessTokenProvider provider =
                new JwtAccessTokenProvider(
                        properties(),
                        Clock.fixed(
                                issuedAt,
                                ZoneOffset.UTC
                        ),
                        TestRsaKeys.publicKey(pair),
                        TestRsaKeys.privateKey(pair)
                );

        JwtAccessTokenVerifier verifier =
                new JwtAccessTokenVerifier(
                        properties(),
                        TestRsaKeys.publicKey(pair),
                        Clock.fixed(
                                verificationTime,
                                ZoneOffset.UTC
                        )
                );

        assertThatThrownBy(() ->
                verifier.verify(
                        provider.issue(
                                UUID.randomUUID(),
                                Role.PATIENT
                        ).value()
                )
        )
                .isInstanceOfSatisfying(
                        AccessTokenVerificationException.class,
                        exception ->
                                assertThat(
                                        exception.reason()
                                ).isEqualTo(
                                        EXPIRED
                                )
                );
    }

    @Test
    void shouldClassifyMalformedToken() {

        JwtAccessTokenVerifier verifier =
                new JwtAccessTokenVerifier(
                        properties(),
                        TestRsaKeys.publicKey(
                                TestRsaKeys.generate()
                        )
                );

        assertThatThrownBy(() ->
                verifier.verify(
                        "this-is-not-a-jwt"
                )
        )
                .isInstanceOfSatisfying(
                        AccessTokenVerificationException.class,
                        exception ->
                                assertThat(
                                        exception.reason()
                                ).isEqualTo(
                                        MALFORMED_TOKEN
                                )
                );
    }

    private JwtProperties properties() {

        return new JwtProperties(
                "telemed-ia-identity-and-access",
                Duration.ofHours(1),
                null,
                null,
                null,
                null
        );
    }
}