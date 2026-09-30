package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.domain.model.Role;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

public class JwtAccessTokenVerifier implements AccessTokenVerifierPort {

    private final NimbusJwtDecoder decoder;

    public JwtAccessTokenVerifier(JwtProperties properties) {
        this(properties, RsaKeyLoader.loadPublicKey(properties));
    }

    public JwtAccessTokenVerifier(JwtProperties properties, RSAPublicKey publicKey) {
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        this.decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
    }

    @Override
    public VerifiedAccessToken verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (jwt.getSubject() == null || jwt.getExpiresAt() == null) {
                throw new AccessTokenVerificationException("JWT requires sub and exp claims.");
            }

            String roleClaim = jwt.getClaimAsString("role");
            if (roleClaim == null) {
                throw new AccessTokenVerificationException("JWT requires role claim.");
            }

            return new VerifiedAccessToken(
                    UUID.fromString(jwt.getSubject()),
                    Role.valueOf(roleClaim),
                    jwt.getExpiresAt()
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessTokenVerificationException("Access token is invalid.", exception);
        }
    }
}
