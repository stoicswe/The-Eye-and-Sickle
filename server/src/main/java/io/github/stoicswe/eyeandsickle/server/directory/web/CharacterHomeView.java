package io.github.stoicswe.eyeandsickle.server.directory.web;

import io.github.stoicswe.eyeandsickle.server.directory.CharacterHomeEntry;
import java.util.Base64;
import java.util.UUID;

/**
 * The wire view of one resolved home binding — what a client on a new machine reads to find a character's
 * home ({@code docs/architecture/09-player-state-portability.md} §4).
 *
 * <h2>Everything needed to re-verify, so the caller need not trust this server</h2>
 *
 * The directory is a low-trust index (Invariant I14): a server serving a resolution could lie. So the
 * view carries the whole signed binding — the home server's DID and signing {@code kid}, the transport
 * key, the sequence, and the {@code signature} — not just a pointer. A caller that can resolve the home
 * server's key can rebuild the {@code CharacterHomeRecord} and re-verify the signature itself, exactly as
 * the serving server did before storing it. The byte fields are base64url without padding, the same
 * encoding the publish envelope uses.
 *
 * @param accountDid the account the character belongs to
 * @param characterId the character's id at its home server
 * @param slot the save slot within the account
 * @param homeServerDid the DID of the home server that hosts the character
 * @param homeEndpoint where to reach the home server
 * @param homeTransportPublicKey base64url X.509-encoded X25519 transport key of the home server
 * @param signingKeyId the DID fragment naming the home server's signing key
 * @param sequenceNumber the monotonic version of the binding
 * @param signature base64url Ed25519 signature by the home server over the binding
 */
public record CharacterHomeView(
        String accountDid,
        UUID characterId,
        int slot,
        String homeServerDid,
        String homeEndpoint,
        String homeTransportPublicKey,
        String signingKeyId,
        long sequenceNumber,
        String signature) {

    /**
     * Projects a stored directory row to its wire view.
     *
     * @param entry the stored binding
     * @return the view
     */
    public static CharacterHomeView from(CharacterHomeEntry entry) {
        return new CharacterHomeView(
                entry.accountDid(),
                entry.characterId(),
                entry.slot(),
                entry.homeServerDid(),
                entry.homeEndpoint(),
                base64Url(entry.homeTransportPublicKey()),
                entry.signingKeyId(),
                entry.sequenceNumber(),
                base64Url(entry.signature()));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
