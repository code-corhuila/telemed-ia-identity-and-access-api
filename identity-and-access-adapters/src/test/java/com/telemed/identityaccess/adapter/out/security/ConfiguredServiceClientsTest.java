package com.telemed.identityaccess.adapter.out.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SERVICE_CLIENTS="id:bcrypt-hash,id:bcrypt-hash": only hashes, never the secrets. */
class ConfiguredServiceClientsTest {

    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuJ9n1t8x1a2b3c4d5e6f7g8h9i0j1k2l";

    @Test
    void findsTheHashOfEachConfiguredClient() {
        ConfiguredServiceClients clients = new ConfiguredServiceClients(
                " document-generation:" + HASH + " , worker:" + HASH + "x ");

        assertThat(clients.secretHashOf("document-generation")).contains(HASH);
        assertThat(clients.secretHashOf("worker")).contains(HASH + "x");
        assertThat(clients.secretHashOf("unknown")).isEmpty();
    }

    @Test
    void hasNoClientsWhenNothingIsConfigured() {
        assertThat(new ConfiguredServiceClients("").secretHashOf("worker")).isEmpty();
        assertThat(new ConfiguredServiceClients(null).secretHashOf("worker")).isEmpty();
    }

    @Test
    void refusesAMalformedEntryInsteadOfIgnoringIt() {
        assertThatThrownBy(() -> new ConfiguredServiceClients("worker-without-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("$2a$");
    }
}
