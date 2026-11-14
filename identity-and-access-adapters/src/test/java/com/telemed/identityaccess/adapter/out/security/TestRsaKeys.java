package com.telemed.identityaccess.adapter.out.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

final class TestRsaKeys {

    private TestRsaKeys() {
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static RSAPublicKey publicKey(KeyPair pair) {
        return (RSAPublicKey) pair.getPublic();
    }

    static RSAPrivateKey privateKey(KeyPair pair) {
        return (RSAPrivateKey) pair.getPrivate();
    }
}
