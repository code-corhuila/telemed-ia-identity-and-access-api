package com.telemed.identityaccess.adapter.out.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyLoader {

    private RsaKeyLoader() {
    }

    public static RSAPrivateKey loadPrivateKey(JwtProperties properties) {
        String pem = resolvePem(properties.privateKey(), properties.privateKeyFile(), "private");
        String body = stripPem(pem, "PRIVATE KEY");
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(body);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT private key must be a valid PKCS#8 RSA PEM.", exception);
        }
    }

    public static RSAPublicKey loadPublicKey(JwtProperties properties) {
        String pem = resolvePem(properties.publicKey(), properties.publicKeyFile(), "public");
        String body = stripPem(pem, "PUBLIC KEY");
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(body);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT public key must be a valid X.509 RSA PEM.", exception);
        }
    }

    private static String resolvePem(String inline, String file, String type) {
        if (inline != null && !inline.isBlank()) {
            return inline.replace("\\n", "\n");
        }
        if (file != null && !file.isBlank()) {
            try {
                return Files.readString(Path.of(file), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Unable to read JWT " + type + " key file.", exception);
            }
        }
        throw new IllegalArgumentException(
                "JWT " + type + " key must be supplied inline or through a key file."
        );
    }

    private static String stripPem(String pem, String label) {
        return pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
    }
}
