package io.github.stoicswe.eyeandsickle.server.directory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of {@code character_directory} — this server's low-trust knowledge of where an account's
 * character is homed ({@code docs/architecture/09-player-state-portability.md} §4).
 *
 * <p>The table is a non-adversarial location index ({@code
 * docs/architecture/03-server-and-federation.md} §2, Invariant I14): holding a {@code CharacterHomeEntry}
 * says only "this character's home is that server, at this signed sequence" — it never adjudicates the
 * character's items, balance, heat or standing, all of which live only in the home server's own Postgres
 * and never travel as self-asserted data. It is the player-character analogue of a {@code PeerRecord}.
 *
 * <p>The binding is keyed by {@link #accountDid()} + {@link #slot()}, the account-relative identity that
 * survives a migration (a new home mints a fresh {@link #characterId()}, but the slot is stable — 09 §8).
 * The signature is retained so any peer can re-verify the binding it was served, and so an
 * equal-sequence re-announcement can be told apart from an equal-sequence conflict.
 *
 * @param entryId server-local surrogate key
 * @param accountDid the account (a DID) the character belongs to; the stable key, gossip-safe (09 §7)
 * @param characterId the character's id at its home server; home-relative, replaced on a migration
 * @param slot the save slot within the account; the account-relative identity the binding is keyed on
 * @param homeServerDid the DID of the home server that hosts the character and signed the binding
 * @param homeEndpoint where to reach the home server; never key anything off it — the DID is stable
 * @param homeTransportPublicKey X.509-encoded X25519 transport key of the home server
 * @param signingKeyId the DID fragment naming the home server's signing key
 * @param sequenceNumber the monotonic version of the stored binding
 * @param signature the home server's Ed25519 signature over the record's canonical bytes
 * @param firstSeenAt when this server first learned of the binding
 * @param lastSeenAt last time this server observed the binding (an announcement or refresh)
 * @param rowVersion optimistic-concurrency version
 */
public record CharacterHomeEntry(
        UUID entryId,
        String accountDid,
        UUID characterId,
        int slot,
        String homeServerDid,
        String homeEndpoint,
        byte[] homeTransportPublicKey,
        String signingKeyId,
        long sequenceNumber,
        byte[] signature,
        Instant firstSeenAt,
        Instant lastSeenAt,
        long rowVersion) {

    public CharacterHomeEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(accountDid, "accountDid");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(homeServerDid, "homeServerDid");
        Objects.requireNonNull(homeEndpoint, "homeEndpoint");
        homeTransportPublicKey = Objects.requireNonNull(homeTransportPublicKey, "homeTransportPublicKey")
                .clone();
        Objects.requireNonNull(signingKeyId, "signingKeyId");
        signature = Objects.requireNonNull(signature, "signature").clone();
    }

    @Override
    public byte[] homeTransportPublicKey() {
        return homeTransportPublicKey.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }
}
