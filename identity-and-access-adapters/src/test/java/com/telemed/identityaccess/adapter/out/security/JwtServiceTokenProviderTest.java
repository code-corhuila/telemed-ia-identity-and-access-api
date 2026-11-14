package com.telemed.identityaccess.adapter.out.security;

import com.nimbusds.jwt.SignedJWT;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort.IssuedAccessToken;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** Same key and issuer as user tokens: every service already reads sub and role. */
class JwtServiceTokenProviderTest {

    @Test
    void signsAShortLivedServiceTokenWithTheServiceAsSubject() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        Instant now = Instant.parse("2026-10-23T15:00:00Z");
        JwtServiceTokenProvider provider = new JwtServiceTokenProvider("telemed-ia-identity-and-access",
                Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC),
                (RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate());

        IssuedAccessToken issued = provider.issue("document-generation");

        SignedJWT jwt = SignedJWT.parse(issued.value());
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("telemed-ia-identity-and-access");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("document-generation");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("SERVICE");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isEqualTo(Date.from(now.plusSeconds(600)));
        assertThat(issued.expiresAt()).isEqualTo(now.plusSeconds(600));
    }
}
