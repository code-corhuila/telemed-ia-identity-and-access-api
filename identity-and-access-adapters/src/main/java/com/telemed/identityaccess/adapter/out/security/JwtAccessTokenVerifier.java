package com.telemed.identityaccess.adapter.out.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import com.telemed.identityaccess.application.exception.AccessTokenVerificationException;
import com.telemed.identityaccess.application.port.out.AccessTokenVerifierPort;
import com.telemed.identityaccess.domain.model.Role;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.EXPIRED;
import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.INVALID_CLAIMS;
import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.INVALID_SIGNATURE;
import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.MALFORMED_TOKEN;
import static com.telemed.identityaccess.application.exception.AccessTokenVerificationException.Reason.UNSUPPORTED_ALGORITHM;

public class JwtAccessTokenVerifier
        implements AccessTokenVerifierPort {

    private final JwtProperties properties;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;

    public JwtAccessTokenVerifier(
            JwtProperties properties
    ) {
        this(
                properties,
                RsaKeyLoader.loadPublicKey(properties),
                Clock.systemUTC()
        );
    }

    public JwtAccessTokenVerifier(
            JwtProperties properties,
            RSAPublicKey publicKey
    ) {
        this(
                properties,
                publicKey,
                Clock.systemUTC()
        );
    }

    JwtAccessTokenVerifier(
            JwtProperties properties,
            RSAPublicKey publicKey,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;

        this.decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(
                        SignatureAlgorithm.RS256
                )
                .build();

        /*
         * Signature verification remains inside NimbusJwtDecoder.
         *
         * Claims are validated explicitly below so verification
         * failures can be classified without string matching.
         */
        this.decoder.setJwtValidator(
                jwt -> OAuth2TokenValidatorResult.success()
        );
    }

    @Override
    public VerifiedAccessToken verify(String token) {

        validateTokenPresence(token);
        validateAlgorithm(token);

        Jwt jwt = decodeAndVerifySignature(token);

        return validateClaims(jwt);
    }

    private void validateTokenPresence(String token) {

        if (token == null || token.isBlank()) {
            throw new AccessTokenVerificationException(
                    MALFORMED_TOKEN,
                    "Access token is required."
            );
        }
    }

    private void validateAlgorithm(String token) {

        SignedJWT signedJwt;

        try {
            signedJwt = SignedJWT.parse(token);

        } catch (ParseException exception) {
            throw new AccessTokenVerificationException(
                    MALFORMED_TOKEN,
                    "Access token is malformed.",
                    exception
            );
        }

        if (!JWSAlgorithm.RS256.equals(
                signedJwt.getHeader().getAlgorithm()
        )) {
            throw new AccessTokenVerificationException(
                    UNSUPPORTED_ALGORITHM,
                    "Access token must use RS256."
            );
        }
    }

    private Jwt decodeAndVerifySignature(String token) {

        try {
            return decoder.decode(token);

        } catch (JwtException exception) {
            throw new AccessTokenVerificationException(
                    INVALID_SIGNATURE,
                    "Access token signature is invalid.",
                    exception
            );
        }
    }

    private VerifiedAccessToken validateClaims(Jwt jwt) {

        String issuer = jwt.getClaimAsString("iss");

        if (!properties.issuer().equals(issuer)) {
            throw invalidClaims(
                    "Access token issuer is invalid."
            );
        }

        String subject = jwt.getSubject();

        if (subject == null || subject.isBlank()) {
            throw invalidClaims(
                    "Access token requires sub claim."
            );
        }

        Instant expiresAt = jwt.getExpiresAt();

        if (expiresAt == null) {
            throw invalidClaims(
                    "Access token requires exp claim."
            );
        }

        if (!expiresAt.isAfter(clock.instant())) {
            throw new AccessTokenVerificationException(
                    EXPIRED,
                    "Access token has expired."
            );
        }

        String roleClaim =
                jwt.getClaimAsString("role");

        if (roleClaim == null || roleClaim.isBlank()) {
            throw invalidClaims(
                    "Access token requires role claim."
            );
        }

        String tokenId = jwt.getId();

        if (tokenId == null || tokenId.isBlank()) {
            throw invalidClaims(
                    "Access token requires jti claim."
            );
        }

        UUID userId;
        Role role;

        try {
            userId = UUID.fromString(subject);
            role = Role.valueOf(roleClaim);

        } catch (IllegalArgumentException exception) {
            throw new AccessTokenVerificationException(
                    INVALID_CLAIMS,
                    "Access token contains invalid claims.",
                    exception
            );
        }

        return new VerifiedAccessToken(
                userId,
                role,
                tokenId,
                expiresAt
        );
    }

    private AccessTokenVerificationException invalidClaims(
            String message
    ) {
        return new AccessTokenVerificationException(
                INVALID_CLAIMS,
                message
        );
    }
}