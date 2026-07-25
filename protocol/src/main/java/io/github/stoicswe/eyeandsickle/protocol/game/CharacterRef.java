package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;
import java.util.UUID;

/**
 * A reference to one character within an account, as it crosses the wire
 * ({@code docs/architecture/09-player-state-portability.md} §1).
 *
 * <h2>The shared atom the directory and migration name a character with</h2>
 *
 * A character is a save slot within an account (09 §1). Two federation features need to name one across
 * the wire and should name it the same way: the character directory (09 §4) maps an account DID to
 * {@code [{characterId, slot, homeServerDid, homeEndpoint}]}, and verifiable migration (09 §6) refers to
 * the character being moved. This is the minimal identity both build on — the character's home-local id
 * and the slot it occupies — so those features do not each invent their own.
 *
 * <p>The {@code characterId} is the character's id at its <em>home</em> server (a {@code players} row
 * key there); it is home-relative on purpose, because migration to another home mints a fresh row and a
 * fresh id (09 §6). The account identity (the DID) and the home's location are carried alongside a
 * {@code CharacterRef} by whatever record embeds it — the directory entry pairs it with the account DID
 * and the home endpoint — rather than being duplicated here.
 *
 * <h2>Structure only — no product rules (Invariant I14)</h2>
 *
 * A slot is a positive index; that is the one invariant this wire type asserts. It deliberately does not
 * encode the cap: "at most {@code maxCharacters} characters" is a soft, server-enforced product limit
 * (09 §2), and the generous upper bound a slot number may take is a server/database detail, not a wire
 * fact. The client and any peer must agree only that a slot is 1-or-greater to render or route a
 * reference; how many an account may hold is the authoritative server's call.
 *
 * @param characterId the character's id at its home server; never {@code null}
 * @param slot the save slot the character occupies within its account; 1 or greater
 */
public record CharacterRef(UUID characterId, int slot) {

    /** The lowest slot number. A slot is a positive index; 0 or negative is not a slot. */
    public static final int MIN_SLOT = 1;

    public CharacterRef {
        Objects.requireNonNull(characterId, "characterId");
        if (slot < MIN_SLOT) {
            throw new IllegalArgumentException("slot is a positive index (>= " + MIN_SLOT + "), was " + slot);
        }
    }

    /**
     * A reference to a character at a slot.
     *
     * @param characterId the character's home-server id
     * @param slot the slot within the account, 1 or greater
     * @return the reference
     * @throws IllegalArgumentException if {@code slot} is below {@link #MIN_SLOT}
     */
    public static CharacterRef of(UUID characterId, int slot) {
        return new CharacterRef(characterId, slot);
    }
}
