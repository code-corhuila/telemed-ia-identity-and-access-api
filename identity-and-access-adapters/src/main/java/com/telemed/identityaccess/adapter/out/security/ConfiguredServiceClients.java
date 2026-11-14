package com.telemed.identityaccess.adapter.out.security;

import com.telemed.identityaccess.application.port.out.ServiceClientRegistryPort;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service clients from configuration (SERVICE_CLIENTS), as "id:bcrypt-hash,id:bcrypt-hash".
 * Only hashes are configured: the secrets live in each calling service's environment.
 */
public class ConfiguredServiceClients implements ServiceClientRegistryPort {

    private final Map<String, String> hashes = new HashMap<>();

    public ConfiguredServiceClients(String specification) {
        if (specification == null || specification.isBlank()) {
            return;
        }
        for (String entry : specification.split(",")) {
            int separator = entry.indexOf(':');
            String clientId = separator < 0 ? "" : entry.substring(0, separator).strip();
            String hash = separator < 0 ? "" : entry.substring(separator + 1).strip();
            if (clientId.isEmpty() || hash.isEmpty()) {
                // The message never repeats the entry: it would print a hash.
                throw new IllegalArgumentException("SERVICE_CLIENTS has an entry that is not id:hash.");
            }
            hashes.put(clientId, hash);
        }
    }

    @Override
    public Optional<String> secretHashOf(String clientId) {
        return Optional.ofNullable(hashes.get(clientId));
    }
}
