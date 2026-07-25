package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.Objects;

/**
 * The lifecycle of a character (a {@code players} row) —
 * {@code docs/architecture/09-player-state-portability.md} §6.1.
 *
 * <h2>Why three states, and why two of them are terminal</h2>
 *
 * A character has exactly one authoritative home at a time (09 §1.1). The status column records whether
 * this server is still that home:
 *
 * <ul>
 *   <li>{@link #ACTIVE} — a live, playable character whose authoritative state lives here.
 *   <li>{@link #MIGRATED} — the character's home has moved to another server (09 §5 cooperative
 *       migration, or §6 verifiable migration to an untrusted server). This row is a shell the old home
 *       keeps for dispute and audit (open question Q-retire-window) — the character is now live
 *       <em>elsewhere</em>.
 *   <li>{@link #RETIRED} — the character has been decommissioned and is not live anywhere.
 * </ul>
 *
 * <p>{@code MIGRATED} and {@code RETIRED} are <strong>terminal</strong>. The one-way rule is the whole
 * of no-double-play (09 §6.1): "Migration retires the character at the old home before it becomes live
 * at the new one; a retired character cannot be played or migrated again." A transition therefore only
 * ever goes {@code ACTIVE -> MIGRATED} or {@code ACTIVE -> RETIRED}, and never back — {@link
 * CharacterService} enforces that, and this enum names which states it may enforce it between.
 *
 * <h2>Not a wire type</h2>
 *
 * Status is server-authoritative lifecycle, never carried on the migration bundle: the untrusted
 * bundle's economy is discarded and only provenance-verified items are imported (09 §3, §7). So this
 * enum lives in the server's identity slice, not in {@code protocol}. Its database spellings are mapped
 * with exhaustive switches — the same discipline as {@code persistence/EnumColumns} — so renaming a
 * constant is a compile error rather than a silent change to what the {@code ck_players_status} CHECK
 * accepts.
 */
public enum CharacterStatus {

    /** A live, playable character whose authoritative home is this server. */
    ACTIVE,

    /** The character's home has moved to another server (09 §5, §6); this row is a retained shell. */
    MIGRATED,

    /** The character has been decommissioned and is live nowhere. */
    RETIRED;

    /**
     * @return the database spelling stored in {@code players.status}
     */
    public String dbValue() {
        return switch (this) {
            case ACTIVE -> "active";
            case MIGRATED -> "migrated";
            case RETIRED -> "retired";
        };
    }

    /**
     * @return whether this is a terminal state — a one-way destination from {@link #ACTIVE} that can
     *     never be left ({@link #MIGRATED} or {@link #RETIRED})
     */
    public boolean isTerminal() {
        return this != ACTIVE;
    }

    /**
     * @return whether a character in this state may be played or migrated — true only for {@link #ACTIVE}
     */
    public boolean isPlayable() {
        return this == ACTIVE;
    }

    /**
     * Parses a stored status value.
     *
     * @param value a value read from {@code players.status}
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not in the {@code ck_players_status} vocabulary —
     *     rejected, never mapped to a fallback, because a status this build does not understand is one it
     *     cannot apply the one-way rule to
     */
    public static CharacterStatus fromDb(String value) {
        return switch (Objects.requireNonNull(value, "value")) {
            case "active" -> ACTIVE;
            case "migrated" -> MIGRATED;
            case "retired" -> RETIRED;
            default ->
                throw new IllegalArgumentException(
                        "players.status holds '" + value
                                + "', which is not a recognized character status (active | migrated | retired). Either a "
                                + "migration added a value this build predates, or the row was written outside CharacterStatus.");
        };
    }
}
