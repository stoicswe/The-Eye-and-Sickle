package io.github.stoicswe.eyeandsickle.client.oauth;

import java.util.Base64;

/**
 * Serialises {@link TokenStore.Credentials} to one opaque line and back.
 *
 * <h2>Why not JSON</h2>
 *
 * A credential blob is written into a keychain slot and read back as a single string. JSON would work,
 * but every field here is already base64 or an opaque token, and a hand-rolled record format with a
 * version prefix means the decoder can <em>refuse</em> an unrecognised shape rather than half-parse
 * it. ⚠ That matters more than usual: a partially-parsed credential set produces a session that
 * authenticates and then fails on the first refresh, which reads to the player as the server logging
 * them out at random.
 *
 * <p>⚠ Fields are base64-encoded before joining, so a token containing the separator cannot forge a
 * field boundary. Two of these values are attacker-influenced (they come from an authorization server
 * the player named), so that is not hypothetical.
 */
final class CredentialCodec {

    private CredentialCodec() {}

    /** Bumped if the field list changes; an older or newer line is refused, never guessed at. */
    private static final String VERSION = "v1";

    private static final String SEPARATOR = "|";

    static String encode(TokenStore.Credentials credentials) {
        return String.join(
                SEPARATOR,
                VERSION,
                b64(credentials.did()),
                b64(credentials.refreshToken()),
                b64(credentials.dpopPrivateKey()),
                b64(credentials.dpopPublicKey()),
                b64(credentials.issuer()));
    }

    static TokenStore.Credentials decode(String line) {
        String[] parts = line.trim().split("\\" + SEPARATOR, -1);
        if (parts.length != 6 || !VERSION.equals(parts[0])) {
            // Refused, not repaired. A credential set this code does not fully understand is one it
            // must not act on — "sign in again" is a fine outcome and a silently wrong session is not.
            throw new OauthException(
                    OauthException.Kind.STORAGE, "stored credentials are in an unrecognised format; sign in again");
        }
        try {
            return new TokenStore.Credentials(
                    unb64(parts[1]), unb64(parts[2]), unb64(parts[3]), unb64(parts[4]), unb64(parts[5]));
        } catch (IllegalArgumentException corrupt) {
            throw new OauthException(
                    OauthException.Kind.STORAGE, "stored credentials are corrupt; sign in again", corrupt);
        }
    }

    private static String b64(String value) {
        return Base64.getEncoder()
                .encodeToString((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        return new String(Base64.getDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
    }
}
