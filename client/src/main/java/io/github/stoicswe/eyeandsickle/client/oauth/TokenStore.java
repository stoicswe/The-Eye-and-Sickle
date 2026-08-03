package io.github.stoicswe.eyeandsickle.client.oauth;

/**
 * Where the refresh token and the DPoP private key live between launches.
 *
 * <h2>⚠ Read this before changing anything here, and before believing the word "secure"</h2>
 *
 * <strong>No desktop credential store protects you from an attacker already running code as your
 * user.</strong> On macOS an unlocked keychain answers any process the user runs; on Windows DPAPI
 * decrypts for any process in the user's session; on Linux the Secret Service is unlocked for the
 * login session. Anything claiming otherwise is claiming something the platform does not offer.
 *
 * <p>What a store here can genuinely do is bound the <em>other</em> exposures, and they are the ones
 * that actually happen:
 *
 * <ul>
 *   <li><strong>Backup and sync leakage</strong> — the profile directory is under
 *       {@code ~/Library/Application Support}, {@code %APPDATA%} and {@code $XDG_DATA_HOME}, all of
 *       which are routinely backed up to a cloud. A plaintext token there is a token in somebody's
 *       backup, forever.
 *   <li><strong>A copied or stolen save</strong> — players share profile directories to move a
 *       character between machines, and this game's own fiction encourages poking at save files.
 *   <li><strong>Casual reading</strong> — the profile is a plain JSON file that
 *       {@code ClientProfile}'s own comment promises holds no credentials.
 *   <li><strong>Other users on a shared machine</strong> — file permissions cover this; the OS
 *       keychain covers it better.
 * </ul>
 *
 * <h2>The rule that is absolute</h2>
 *
 * ⚠ <strong>Nothing here may ever be written to {@code ClientProfile}</strong>, whose comment states:
 * <em>"No credentials and no tokens are ever written here — the profile is a plain unencrypted JSON
 * file in a conventional location."</em> That promise is load-bearing and a token in the profile
 * breaks it silently. The DPoP private key counts as a credential: a refresh token stored well beside
 * a DPoP key stored carelessly is a refresh token stored carelessly.
 *
 * <h2>Two implementations, and the fallback must be visible</h2>
 *
 * {@link KeychainTokenStore} uses the platform's own credential store. {@link EncryptedFileTokenStore}
 * is the fallback where there is none — it encrypts, but the key sits beside the data, so against a
 * local attacker it is obfuscation rather than protection. ⚠ {@link #isPlatformSecured()} exists so
 * the difference can be <em>shown to the player</em> rather than assumed: a security property nobody
 * is told about is one they cannot account for.
 */
public interface TokenStore {

    /**
     * What is worth keeping between launches.
     *
     * <p>⚠ Deliberately does <strong>not</strong> include the access token. It lasts ≤30 minutes, so
     * persisting it stores a credential that is expired by the next launch anyway — all cost, no
     * benefit. The refresh token is what buys a session; the DPoP key is what makes it usable.
     *
     * @param did the account this belongs to
     * @param refreshToken the refresh token
     * @param dpopPrivateKey PKCS#8, base64
     * @param dpopPublicKey X.509, base64
     * @param issuer the authorization server that issued it
     */
    record Credentials(String did, String refreshToken, String dpopPrivateKey, String dpopPublicKey, String issuer) {}

    /**
     * @return the stored credentials, or null if there are none
     * @throws OauthException {@code STORAGE} if the store exists but cannot be read
     */
    Credentials load();

    /**
     * @param credentials what to keep
     * @throws OauthException {@code STORAGE} if it cannot be written
     */
    void save(Credentials credentials);

    /**
     * Removes everything.
     *
     * <p>⚠ Must be called on sign-out <em>and</em> whenever a refresh is rejected. A refresh token the
     * server has already invalidated is not a credential any more, and keeping it means every launch
     * retries a failing exchange and shows the player an error instead of a sign-in button.
     */
    void clear();

    /**
     * Whether the platform's own credential store is doing the work.
     *
     * @return false when this is the encrypt-beside-the-key fallback
     */
    boolean isPlatformSecured();

    /** A human-readable name for the mechanism, for the settings screen. */
    String describe();
}
