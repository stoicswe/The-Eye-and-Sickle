package io.github.stoicswe.eyeandsickle.server.items;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this server's own Ed25519 signing identity comes from.
 *
 * <h2>Why the key is never generated and never committed</h2>
 *
 * This server signs every {@code initial_mint}, {@code server_grant} and {@code trade} it issues with
 * one long-lived Ed25519 key ({@code docs/architecture/04-item-provenance.md} §5). That key <em>is</em>
 * the server's item-forging authority: every item it ever minted chains back to a genesis record
 * signed with it, and other servers recognize those items by resolving this key from the server's DID.
 *
 * <p>Two consequences follow, and both are enforced by {@link ServerSigningKeyLoader} rather than left
 * to operator discipline:
 *
 * <ul>
 *   <li><strong>Never auto-generate.</strong> If the configured key file is missing, the honest
 *       failure is a loud one — not silently minting a fresh identity, which would orphan every item
 *       this server has ever signed (a new key resolves nothing the old chains point at). See the
 *       {@code .gitignore} header: a leaked private key is item-forging material that cannot be
 *       un-leaked.
 *   <li><strong>Never in the repository.</strong> {@code .gitignore} blocks {@code *.pem} / {@code
 *       *.key} / {@code *.p8}; the key lives on the operator's disk or in a mounted secret, and only
 *       its <em>path</em> is configuration. The matching public key is safe to commit ({@code
 *       *.pub.pem} is un-ignored) and is what peers verify against.
 * </ul>
 *
 * <h2>Not configured is a valid state</h2>
 *
 * A purely client-facing dev build, or a server that only ever <em>receives</em> items and never mints
 * its own, has no reason to hold a signing key. So an entirely unset configuration is allowed and
 * yields a {@link ServerSigningIdentity} that refuses to sign with a clear message — the loud failure
 * happens at the first mint attempt, not at boot. What is <em>not</em> tolerated is a configured path
 * that does not resolve: that is a misconfiguration, and {@link ServerSigningKeyLoader} fails at
 * startup so it surfaces before a single record is signed against the wrong key.
 *
 * @param did this server's DID, the {@code issuerDid} on every record it signs ({@code
 *     docs/architecture/04} §2). Null when signing is not configured.
 * @param keyId the fragment identifying which of the DID's keys signs, appended after {@code #} to
 *     form the {@code kid} (e.g. {@code did:plc:server#key1}). Defaults to {@value #DEFAULT_KEY_ID}.
 * @param privateKeyPath filesystem path to the PKCS#8 private key (PEM or DER). Required to sign; when
 *     set but unreadable, startup fails.
 * @param publicKeyPath filesystem path to the X.509 {@code SubjectPublicKeyInfo} public key (PEM or
 *     DER). Optional: when present, this server can re-verify records it signed itself — the case
 *     where a home-minted item is traded away and later returns.
 */
@ConfigurationProperties(prefix = "eyeandsickle.items.signing")
public record ServerSigningProperties(String did, String keyId, String privateKeyPath, String publicKeyPath) {

    /** The conventional single-key fragment, matching the {@code #key1} example in {@code 04} §3. */
    public static final String DEFAULT_KEY_ID = "key1";

    public ServerSigningProperties {
        keyId = (keyId == null || keyId.isBlank()) ? DEFAULT_KEY_ID : keyId;
    }

    /** Whether a private key path was supplied at all. */
    public boolean signingConfigured() {
        return privateKeyPath != null && !privateKeyPath.isBlank();
    }

    /**
     * The full {@code kid} a signature block carries: {@code did#keyId}.
     *
     * @return the key identifier, or {@code null} if no DID is configured
     */
    public String kid() {
        return did == null ? null : did + "#" + keyId;
    }
}
