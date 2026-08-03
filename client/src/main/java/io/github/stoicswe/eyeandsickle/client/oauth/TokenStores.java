package io.github.stoicswe.eyeandsickle.client.oauth;

import java.nio.file.Path;

/** Picks the strongest credential store this machine actually offers. */
public final class TokenStores {

    private TokenStores() {}

    /**
     * @param profileDirectory where the fallback's files go — never {@code ClientProfile}'s JSON
     * @return the platform store if one answers, otherwise the encrypted-file fallback
     */
    public static TokenStore forProfile(Path profileDirectory) {
        // ⚠ Probed by USE, not by "does the binary exist": a keychain can be present and locked, and
        // secret-tool can be installed with no D-Bus session behind it. A store that claims to work
        // and then loses credentials is worse than one that declines. KeychainTokenStore.available()
        // writes and reads back a canary.
        KeychainTokenStore keychain = KeychainTokenStore.available();
        return keychain != null ? keychain : new EncryptedFileTokenStore(profileDirectory);
    }
}
