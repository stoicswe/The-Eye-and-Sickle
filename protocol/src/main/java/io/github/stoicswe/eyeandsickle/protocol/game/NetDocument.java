package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;
import java.util.Objects;

/**
 * A story fragment pulled off a machine the player has breached — and, sometimes, a step towards a
 * schematic.
 *
 * <h2>Flavour with a pull, never a critical path</h2>
 *
 * Deep machines carry things worth reading, and some of them carry schematic material so that danger has
 * a reason beyond loot. What none of them may ever carry is <em>progression the player cannot get any
 * other way</em>. The ordered critical-path beats of the narrative are unwritten
 * ({@code docs/design/15-open-questions.md}, N-2), and a system that gated advancement on documents would
 * make every one of those beats a blocker on a narrative pass that has not happened. So: reading these is
 * optional, skipping them costs a player nothing they cannot earn elsewhere, and the world is generated
 * so that the home server carries none at all — the flavour layer starts one bridge out, which puts it
 * structurally off the early path rather than merely off it by tuning.
 *
 * <h2>There is no body here, and that is not an oversight</h2>
 *
 * A document's prose is a client resource keyed by {@code documentId}; the rules know that a fragment
 * exists, what it is called and where it came from, and nothing about what it says. Rules never carry
 * prose — a paragraph in a rules module is a paragraph that has to be translated, versioned and
 * regression-tested alongside balance values it has nothing to do with. A client with no file for an id
 * renders an unreadable fragment, which is a valid and entirely in-fiction outcome: recovering something
 * corrupted off a machine you should not have been on is the expected experience, not an error state.
 *
 * <h2>{@code schematicMaterial} reports an award; it does not decide one</h2>
 *
 * Invariant I13 gates salvage and partial-progress drops on <em>engagement tier</em>, and
 * {@code docs/design/10-botnets.md} §1a gives the reason in its original costume: "the material drop is
 * gated on engagement tier — the bot must have been lost against a defended target above a difficulty
 * threshold. Without this, the optimal play is to build the cheapest junk bot and feed it to a loss." The
 * same exploit exists here wearing different clothes — find a deep-but-trivial machine and farm it for
 * material — and the same guard closes it. The threshold itself is a balance value held by the
 * authoritative rules; this record says only how much was actually awarded, which for a document off an
 * easy machine is legitimately zero.
 *
 * @param documentId which fragment this is; the key a client resolves prose against
 * @param title what it is called, e.g. {@code "AUDIT FINDING 14-C"}
 * @param recoveredFrom the address it came off
 * @param recoveredAt when it was taken — an input, chosen by the caller. Nothing in this package reads a
 *     clock ({@code ArchitectureRulesTest}), because a record that timestamped itself would serialize
 *     differently on two machines
 * @param schematicMaterial units awarded, {@code 0} when the machine did not clear the engagement gate
 */
public record NetDocument(
        String documentId, String title, String recoveredFrom, Instant recoveredAt, int schematicMaterial) {

    public NetDocument {
        documentId = documentId == null ? "" : documentId;
        title = title == null ? "" : title;
        recoveredFrom = recoveredFrom == null ? "" : recoveredFrom;

        // The one field that is required rather than blanked. A document is an entry in an ordered
        // record of what the player has recovered — oldest first — and an undated entry cannot take a
        // place in that order, so it would silently sort wherever the comparator happened to put it.
        Objects.requireNonNull(recoveredAt, "recoveredAt");

        if (schematicMaterial < 0) {
            throw new IllegalArgumentException("schematicMaterial is an award, never a forfeit, was "
                    + schematicMaterial + " (Invariant I13, docs/design/10-botnets.md §1a)");
        }
    }
}
