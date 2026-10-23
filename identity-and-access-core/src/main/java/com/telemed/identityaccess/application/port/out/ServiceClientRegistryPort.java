package com.telemed.identityaccess.application.port.out;

import java.util.Optional;

/** The services allowed to ask for a token, each with the hash of its secret (never the secret). */
public interface ServiceClientRegistryPort {

    Optional<String> secretHashOf(String clientId);
}
