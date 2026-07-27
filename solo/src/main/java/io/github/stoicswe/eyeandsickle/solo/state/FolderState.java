package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.UUID;

/**
 * One folder in the player's filing of the machines they have found.
 *
 * <h2>A directory, not a tag</h2>
 *
 * The tree is a <b>filesystem</b>: a folder has at most one parent, and a machine sits in at most one
 * folder ({@link NodeState#folderId}). The alternative — many-to-many labels — is more expressive and
 * is the wrong shape for this game twice over. It has no {@code mv}, so the terminal half of the
 * feature would have to invent a verb the player does not already know; and
 * {@code docs/client/04-terminology-and-education.md} builds the whole teaching layer on the claim
 * that the words here are the real ones. {@code mkdir}, {@code mv} and {@code rmdir} do what a player
 * who has met a Unix filesystem already expects, and a player who has not meets them here first and
 * carries them out of the game. That transfer is the point; a bespoke tagging model would teach
 * nothing.
 *
 * <h2>Filing is knowledge the player already paid for</h2>
 *
 * ⚠ A folder <b>may only hold an address the player has discovered</b> — {@code FolderRules} checks
 * {@code knownNodes}, not the topology. Letting a player file {@code 10.0.4.7} on a hunch and having
 * the folder confirm it exists would sell, for free, the one thing every sweep tier is sold on
 * ({@code docs/design/07-recon-tools.md} §1). The refusal is deliberately identical whether the
 * address is undiscovered or entirely fictional, because two different refusals are an oracle.
 *
 * <h2>Purely the player's, and therefore mechanically inert</h2>
 *
 * Nothing reads this to decide anything. Filing a machine does not make it easier to breach, cheaper
 * to sweep, or more likely to be found; it costs no compute, no ethecoin and no time, and it can be
 * undone. That is what makes it safe to hand the player unlimited folders — there is no quantity here
 * for a gate to be attached to, so {@code docs/design/02-unlock-gates.md} §1.1 does not apply and no
 * invariant is in reach. A bookmark that changed the game would be a capability wearing a bookmark's
 * clothes.
 */
public final class FolderState {

    public String folderId = UUID.randomUUID().toString();

    /**
     * The folder this sits in, or {@code ""} for a top-level folder.
     *
     * <p>A string rather than a nested list of children because this lands in a JSON document that a
     * player can hand-edit: a flat list with parent pointers survives a mangled entry as one lost
     * folder, where a nested tree loses everything below the damage. {@code FolderRules} re-roots any
     * folder whose parent has gone missing rather than dropping it.
     */
    public String parentId = "";

    public String name = "";

    public Instant createdAt = Instant.EPOCH;
}
