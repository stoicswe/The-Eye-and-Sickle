package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The wire bundle a character carries when it migrates to an <strong>untrusted</strong> home server —
 * Option C ({@code docs/architecture/09-player-state-portability.md} §6).
 *
 * <h2>Only what is cryptographically yours travels</h2>
 *
 * §3 is the whole design: a character's state splits into a <em>portable</em> half and a
 * <em>non-portable</em> half, and this bundle carries exactly the portable half —
 *
 * <ul>
 *   <li>the {@link #accountDid() DID} (portable by construction, {@code 02});
 *   <li>the character's <strong>provenanced items, each with its full signed chain</strong> ({@code
 *       04}), carried as the verbatim envelope documents so the signatures reproduce at the
 *       destination.
 * </ul>
 *
 * It deliberately carries <strong>none</strong> of the non-portable half — no ethecoin balance, no
 * rig/compute config, no personal heat, no faction reputation, no deployed miners. Those are
 * freely-assertable, server-local ledger state with no cryptographic anchor (§3), so an untrusted
 * destination cannot trust them and they <em>reset</em> there (§6). This type simply gives them nowhere
 * to ride: a field that does not exist cannot smuggle economy across a trust boundary. The trusted
 * counterpart that <em>does</em> carry economy is Option B, and it is a different type on purpose so the
 * two can never be confused (importing a full-state bundle from an untrusted source would be an I14
 * violation).
 *
 * <h2>Untrusted transport — the proof rides inside, not on the envelope</h2>
 *
 * The bundle itself is unsigned and untrusted; it is a courier, nothing more (§6.1). Its trustworthiness
 * comes entirely from the per-item chains, which the destination re-verifies with {@code
 * ProvenanceChainVerifier} before recognizing anything. An item whose chain does not verify is simply
 * not recognized — the same rule that makes a cheating server's fabricated items worthless. Nothing here
 * may ever be read as authoritative without that re-verification.
 *
 * <h2>Structure only — no game rules (Invariant I14)</h2>
 *
 * This is a courier manifest: an account identity, a reference to the character being moved, the home
 * binding it supersedes (so the destination can advance the directory monotonically and refuse a
 * rollback — §4, §6.1), and the opaque item chains. It encodes no cap, no price, no yield and evaluates
 * nothing. The envelope documents are treated as opaque strings on purpose, so re-serialization can
 * never alter the bytes a signature covers.
 *
 * @param accountDid the migrating account's DID, the one piece of identity that travels (§3); never
 *     {@code null} or blank
 * @param sourceCharacter the character being moved, named at its <em>source</em> home (a home-relative
 *     id and slot — {@link CharacterRef}); the destination mints a fresh id (§6), so this is for the
 *     directory binding and audit, not reuse
 * @param sourceHomeServerDid the DID of the home server releasing the character — who signed the home
 *     binding this migration supersedes (§4); never {@code null} or blank
 * @param homeSequence the monotonic directory sequence the source home last published for this
 *     character (§4). The destination advances strictly past it; a bundle presenting a stale sequence is
 *     a replay/rollback and is refused (§6.1). Never negative.
 * @param itemChains one {@link ItemChain} per provenanced item the character carries; may be empty (a
 *     character with no items still migrates — it simply arrives with an empty inventory)
 */
public record CharacterMigrationBundle(
        String accountDid,
        CharacterRef sourceCharacter,
        String sourceHomeServerDid,
        long homeSequence,
        List<ItemChain> itemChains) {

    public CharacterMigrationBundle {
        accountDid = requireText(accountDid, "accountDid");
        Objects.requireNonNull(sourceCharacter, "sourceCharacter");
        sourceHomeServerDid = requireText(sourceHomeServerDid, "sourceHomeServerDid");
        if (homeSequence < 0) {
            throw new IllegalArgumentException(
                    "homeSequence is a directory sequence and is never negative, was " + homeSequence);
        }
        Objects.requireNonNull(itemChains, "itemChains");
        itemChains = List.copyOf(itemChains);
    }

    /**
     * One item's full provenance chain, carried verbatim.
     *
     * <p>The envelope documents are the exact JSON the source stored or received, ordered genesis-first
     * ({@code 04} §6.1), and kept as opaque strings so nothing between the two homes re-serializes them —
     * re-serialization could change the bytes a signature covers, which is the one thing that would make
     * a genuine item fail verification at the destination.
     *
     * @param itemId the item's identity, matching the {@code itemId} inside every record of its chain
     * @param envelopes the item's provenance records as verbatim detached-JWS envelope documents,
     *     ordered genesis-first; never empty, because an item with no records has no provenance
     */
    public record ItemChain(UUID itemId, List<String> envelopes) {

        public ItemChain {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(envelopes, "envelopes");
            if (envelopes.isEmpty()) {
                throw new IllegalArgumentException(
                        "An item chain carries at least its genesis record; item " + itemId + " carried none");
            }
            envelopes = List.copyOf(envelopes);
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
