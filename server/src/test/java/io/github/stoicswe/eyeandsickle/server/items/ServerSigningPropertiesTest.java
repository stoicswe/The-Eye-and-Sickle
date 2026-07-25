package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ServerSigningProperties} — the binding that decides whether this server has a signing
 * identity, and how its {@code kid} is spelled. Pure record logic, no I/O.
 */
class ServerSigningPropertiesTest {

    private static final String DID = "did:plc:server00000000000000";

    @Test
    @DisplayName("a blank or absent key id defaults to the conventional #key1 fragment")
    void keyIdDefaults() {
        assertThat(new ServerSigningProperties(DID, null, "/k", null).keyId())
                .isEqualTo(ServerSigningProperties.DEFAULT_KEY_ID);
        assertThat(new ServerSigningProperties(DID, "  ", "/k", null).keyId())
                .isEqualTo(ServerSigningProperties.DEFAULT_KEY_ID);
    }

    @Test
    @DisplayName("a custom key id is kept")
    void customKeyIdKept() {
        assertThat(new ServerSigningProperties(DID, "signing-2", "/k", null).keyId())
                .isEqualTo("signing-2");
    }

    @Test
    @DisplayName("the kid is did#keyId, and null when there is no DID")
    void kidComposition() {
        assertThat(new ServerSigningProperties(DID, "key1", "/k", null).kid()).isEqualTo(DID + "#key1");
        assertThat(new ServerSigningProperties(DID, "transport-1", "/k", null).kid())
                .isEqualTo(DID + "#transport-1");
        // No DID -> no kid: a signature block cannot name a key that has no owning identity.
        assertThat(new ServerSigningProperties(null, "key1", "/k", null).kid()).isNull();
    }

    @Test
    @DisplayName("signing is configured only when a private key path is actually present")
    void signingConfiguredNeedsAPath() {
        assertThat(new ServerSigningProperties(DID, null, "/path/to/key", null).signingConfigured())
                .isTrue();
        assertThat(new ServerSigningProperties(DID, null, null, null).signingConfigured())
                .isFalse();
        assertThat(new ServerSigningProperties(DID, null, "   ", null).signingConfigured())
                .isFalse();
    }
}
