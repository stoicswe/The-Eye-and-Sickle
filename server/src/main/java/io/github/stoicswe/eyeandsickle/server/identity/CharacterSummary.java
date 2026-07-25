package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.util.Objects;

/**
 * The character-select view of one character — what the account roster and the create response show.
 *
 * <h2>A view, not the row</h2>
 *
 * A {@link Player} carries authoritative internals a client has no business reading off a roster: the
 * spendable balance, personal heat, and the {@code row_version} concurrency token. This projection is
 * only what a character-select screen needs — the character's wire reference ({@link CharacterRef}), its
 * lifecycle status, its display handle and its committed side — so those internals never leave the server
 * through this path. It is built exclusively from DID-bound characters (the account endpoints only ever
 * list an account's own, DID-bound characters), which is why the reference's slot is always present.
 *
 * @param ref the character's wire reference (home-server id + slot)
 * @param status the lifecycle status, as its wire spelling ({@code active} on a selectable character)
 * @param handle the account's display handle on this character, or {@code null}
 * @param faction the committed side, or {@link Faction#NONE} while uncommitted
 */
public record CharacterSummary(CharacterRef ref, String status, String handle, Faction faction) {

    public CharacterSummary {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(faction, "faction");
    }

    /**
     * Projects a DID-bound character to its select-view.
     *
     * @param character a DID-bound character (its {@code slot} is present)
     * @return the summary
     * @throws IllegalArgumentException if the character is local (no slot) — local characters are outside
     *     the account roster (09 §1)
     */
    public static CharacterSummary from(Player character) {
        Objects.requireNonNull(character, "character");
        if (character.slot() == null) {
            throw new IllegalArgumentException("Cannot summarize a local, slot-less character as an account character");
        }
        return new CharacterSummary(
                new CharacterRef(character.playerId(), character.slot()),
                character.status().dbValue(),
                character.handle(),
                character.faction());
    }
}
