package com.telemed.identityaccess.adapter.out.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.telemed.identityaccess.application.port.out.AccessTokenProviderPort.IssuedAccessToken;
import com.telemed.identityaccess.application.port.out.ServiceTokenProviderPort;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Service tokens: same RS256 key and issuer as user tokens, sub = client id and
 * role = SERVICE, so every service authorises them with the claims it already reads.
 * Short-lived and without refresh token: the service asks again before expiry.
 */
public class JwtServiceTokenProvider implements ServiceTokenProviderPort {

    public static final String SERVICE_ROLE = "SERVICE";

    private final String issuer;
    private final Duration ttl;
    private final Clock clock;
    private final JwtEncoder encoder;

    public JwtServiceTokenProvider(JwtProperties properties, Duration ttl, Clock clock) {
        this(properties.issuer(), ttl, clock,
                RsaKeyLoader.loadPublicKey(properties), RsaKeyLoader.loadPrivateKey(properties));
    }

    public JwtServiceTokenProvider(String issuer, Duration ttl, Clock clock,
                                   RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        this.issuer = issuer;
        this.ttl = ttl;
        this.clock = clock;
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        this.encoder = new NimbusJwtEncoder(jwkSource);
    }

    @Override
    public IssuedAccessToken issue(String clientId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(clientId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("role", SERVICE_ROLE)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        return new IssuedAccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                expiresAt);
    }
}
