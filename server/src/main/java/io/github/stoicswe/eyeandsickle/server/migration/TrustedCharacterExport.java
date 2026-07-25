package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.Heat;
import java.util.List;
import java.util.Objects;

/**
 * The full-state export bundle of Option B — cooperative migration between operators who trust each other
 * ({@code docs/architecture/09-player-state-portability.md} §5).
 *
 * <h2>Trusted by construction — and named so it can never be confused with Option C</h2>
 *
 * Unlike the untrusted {@link io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle},
 * this carries the character's <em>whole</em> state, economy included: the committed faction, personal
 * heat and ethecoin balance alongside the provenanced items. That is legitimate <strong>only</strong>
 * because both operators cooperate and therefore trust each other (§5) — it is "the only path by which the
 * non-portable economy legitimately moves." Importing a full-state bundle from an <em>untrusted</em> source
 * would import freely-assertable economy and violate the portable/non-portable split (§3) and Invariant
 * I14, so:
 *
 * <ul>
 *   <li>This is a distinct type from the C bundle, and the name says {@code Trusted} out loud, so the two
 *       are never accidentally interchanged.
 *   <li>It lives in the server slice, not {@code protocol}: it is server-owned economy state moving between
 *       cooperating operators, not a general wire type any peer may originate.
 *   <li>The endpoints that produce and consume it are operator-authenticated ({@link OperatorAuthorization}),
 *       never player-triggered.
 * </ul>
 *
 * <h2>What the import side of B can and cannot restore</h2>
 *
 * The identity-owned standing — committed faction and personal heat — is restored onto the fresh character
 * on import. The ethecoin <em>balance</em> is carried here for completeness and for the operational
 * full-fidelity path (a PostgreSQL dump/restore, {@code deploy/BACKUP.md}), but re-applying a balance is a
 * ledger transaction the economy slice owns (Invariant I1), and faction <em>reputation</em> is a separate
 * table; both are documented seams the bundle-based B import leaves to their owning slices rather than
 * writing a balance outside the ledger.
 *
 * @param accountDid the migrating account's DID
 * @param sourceCharacter the character being moved, at its source home
 * @param sourceHomeServerDid the DID of the cooperating source home server
 * @param homeSequence the source home-binding sequence this migration supersedes (§4)
 * @param handle the character's display handle, or {@code null}
 * @param faction the committed faction to carry (§5)
 * @param ethecoinBalance the ethecoin balance to carry (see the restore caveat above)
 * @param personalHeat the personal heat to carry
 * @param itemChains the provenanced items, each as its verbatim chain (still re-verified on import)
 */
public record TrustedCharacterExport(
        String accountDid,
        CharacterRef sourceCharacter,
        String sourceHomeServerDid,
        long homeSequence,
        String handle,
        Faction faction,
        Ethecoin ethecoinBalance,
        Heat personalHeat,
        List<ItemChain> itemChains) {

    public TrustedCharacterExport {
        Objects.requireNonNull(accountDid, "accountDid");
        Objects.requireNonNull(sourceCharacter, "sourceCharacter");
        Objects.requireNonNull(sourceHomeServerDid, "sourceHomeServerDid");
        if (homeSequence < 0) {
            throw new IllegalArgumentException("homeSequence is never negative, was " + homeSequence);
        }
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(ethecoinBalance, "ethecoinBalance");
        Objects.requireNonNull(personalHeat, "personalHeat");
        itemChains = List.copyOf(Objects.requireNonNull(itemChains, "itemChains"));
    }
}
