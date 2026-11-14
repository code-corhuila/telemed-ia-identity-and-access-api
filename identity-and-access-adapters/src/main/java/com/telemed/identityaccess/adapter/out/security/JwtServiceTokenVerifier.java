package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.port.out.ServiceTokenVerifierPort;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;

import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.INVALID_CLAIMS;
import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.INVALID_SIGNATURE;

/**
 * Verifies any token identity signed (RS256, issuer, expiry) without assuming a user id
 * as subject, so internal endpoints can tell a service from a user and refuse the user.
 */
public class JwtServiceTokenVerifier implements ServiceTokenVerifierPort {

    private final NimbusJwtDecoder decoder;

    public JwtServiceTokenVerifier(JwtProperties properties) {
        this(properties.issuer(), RsaKeyLoader.loadPublicKey(properties), Clock.systemUTC());
    }

    JwtServiceTokenVerifier(String issuer, RSAPublicKey publicKey, Clock clock) {
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        JwtTimestampValidator expiry = new JwtTimestampValidator(Duration.ZERO);
        expiry.setClock(clock);
        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(expiry, new JwtIssuerValidator(issuer));
        this.decoder.setJwtValidator(validator);
    }

    @Override
    public ServiceCaller verify(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException exception) {
            throw new AccessTokenVerificationException(
                    INVALID_SIGNATURE, "Access token is invalid or expired.", exception);
        }

        String subject = jwt.getSubject();
        String role = jwt.getClaimAsString("role");
        if (subject == null || subject.isBlank() || role == null || role.isBlank()) {
            throw new AccessTokenVerificationException(
                    INVALID_CLAIMS, "Access token requires sub and role claims.");
        }

        return new ServiceCaller(subject, role);
    }
}
