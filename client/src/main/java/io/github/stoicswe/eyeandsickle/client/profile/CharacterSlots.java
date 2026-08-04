package io.github.stoicswe.eyeandsickle.client.profile;

import io.github.stoicswe.eyeandsickle.engine.save.LocalDatabase;
import io.github.stoicswe.eyeandsickle.engine.save.SaveStore;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The save slots a player chooses between on the main menu.
 *
 * <h2>Three, and the number is not arbitrary</h2>
 *
 * {@code docs/architecture/09-player-state-portability.md} fixes three character slots per account
 * for <b>online</b> play, and this mirrors it offline so the two modes present the same shape. A
 * player who later goes online should not have to learn a different mental model of what a character
 * is — and a player who never goes online still benefits from being able to keep a cautious run and a
 * reckless one at the same time.
 *
 * <h2>⚠ A solo slot is a ROW now, not a file</h2>
 *
 * Slots were JSON files in the profile directory until 2026-08-03. They are rows in
 * {@code character_game_state}, in the profile's local H2 database, written by the same
 * {@code JdbcSaveStore} a home server uses — one save system for every mode.
 *
 * <p>⚠ The slot's identity is therefore a <strong>character id</strong>, derived from the slot number
 * ({@link #characterId}). It is derived rather than stored because a stored mapping is a second piece
 * of state that can disagree with the first, on the surface a player uses to find their character.
 *
 * <h2>Solo slots are local; online slots are not</h2>
 *
 * An <em>online</em> slot lives on a home server, keyed to a DID, and this client cannot enumerate
 * one without the transport that <b>CL-8</b> still lacks — so {@link #onlineSlots} returns what the
 * profile has cached about servers the player has named, and says plainly that it cannot list
 * characters yet. Inventing a plausible list would be the worst option available.
 */
public final class CharacterSlots {

    /** ⚠ JUL — captured by {@code log/ClientLog} for the CLIENT LOGS tab. */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(CharacterSlots.class.getName());

    /** Matches the online cap in {@code docs/architecture/09}. */
    public static final int SLOT_COUNT = 3;

    private final ClientProfile profile;
    private LocalDatabase database;

    public CharacterSlots(ClientProfile profile) {
        this.profile = profile;
    }

    /**
     * The local database every solo slot lives in, opened and migrated on first use.
     *
     * <p>⚠ Opened lazily and held, not opened per call. H2 allows one writer, and reopening the same
     * file from a second {@code JdbcDataSource} while the first is live is how a player meets
     * "Database may be already in use" on a screen that has no way to explain it.
     *
     * @return the profile's database
     */
    public synchronized LocalDatabase database() {
        if (database == null) {
            database = LocalDatabase.openAt(profile.directory().resolve("characters"));
        }
        return database;
    }

    /**
     * The store for one slot — the same {@code JdbcSaveStore} a home server builds.
     *
     * @param slot the slot number
     * @return its store
     */
    public SaveStore store(int slot) {
        return database().store(characterId(slot), Instant::now);
    }

    /**
     * The character id slot {@code n} maps to.
     *
     * <p>⚠ DERIVED from the slot number, never stored. A stored mapping would be a second piece of
     * state able to disagree with the row it points at — and the symptom would be a player opening
     * slot 2 and meeting slot 3's character. Deriving it means the two cannot come apart.
     *
     * @param slot the slot number
     * @return the character id
     */
    public UUID characterId(int slot) {
        return UUID.nameUUIDFromBytes(("eyeandsickle:solo:slot:" + slot).getBytes(StandardCharsets.UTF_8));
    }

    /** Reads all three slots. A slot that cannot be parsed is reported, never silently skipped. */
    public List<Slot> soloSlots() {
        List<Slot> out = new ArrayList<>();
        for (int i = 1; i <= SLOT_COUNT; i++) {
            out.add(readSlot(i));
        }
        return out;
    }

    private Slot readSlot(int index) {
        try {
            // ⚠ Goes through store(), which imports a legacy JSON save if the row is empty — so a
            // returning player's character appears on the menu rather than reading as an empty slot
            // they are then invited to overwrite.
            GameSave save = store(index).load();
            if (save == null) {
                return Slot.empty(index);
            }
            return new Slot(
                    index,
                    true,
                    save.handle,
                    save.ethecoinWei,
                    save.rig.totalCycles,
                    save.playedSeconds,
                    save.lastPlayedAt,
                    save.createdAt,
                    null,
                    save.avatarPng);
        } catch (RuntimeException unreadable) {
            // ⚠ SEVERE. The slot card says "unreadable" and nothing more; this is the only place the
            // reason is recorded, and the reason is what decides whether a character is recoverable.
            LOG.log(java.util.logging.Level.SEVERE, "slot " + index + " could not be read", unreadable);
            // A corrupt or future-format save is shown as such rather than hidden. A slot that
            // silently reads as empty invites the player to overwrite the thing they were trying to
            // recover.
            //
            // ⚠ getMessage() CAN BE NULL, and this used to pass it straight through — which set
            // `problem` to null, made `unreadable()` false, and rendered the slot as EMPTY. That is
            // precisely the failure the comment above says this branch exists to prevent, reachable
            // by any exception that carries no message. Falling back to toString() means the slot is
            // always reported as unreadable, with the class name if there is nothing better.
            String problem = unreadable.getMessage() != null ? unreadable.getMessage() : unreadable.toString();
            return new Slot(index, false, "", java.math.BigInteger.ZERO, 0, 0, null, null, problem, "");
        }
    }

    /**
     * Deletes a slot. The caller is responsible for confirming — this does not ask.
     *
     * <p>⚠ The slot's <b>appearance</b> goes with it. Slots are reused, and a new character
     * inheriting a deleted one's palette is a ghost nobody can explain: the assistant would show
     * them choosing Deck and the game would open in Phosphor. Forgotten even when the file was
     * already gone, so a half-deleted slot cannot leave one behind.
     */
    public boolean delete(int slot) {
        profile.settings().forgetAppearance(slot);
        profile.save();
        return database()
                        .jdbcClient()
                        .sql("DELETE FROM character_game_state WHERE character_id = :id")
                        .param("id", characterId(slot))
                        .update()
                > 0;
    }

    /**
     * Home servers the player has told us about.
     *
     * <p>Not characters. Listing an online character requires resolving the player's DID and asking
     * their home server, which needs the transport CL-8 has not built — so this returns servers and
     * the menu says so, rather than showing an empty character list that reads as "you have none".
     */
    public List<String> onlineSlots() {
        return List.copyOf(profile.settings().knownServers);
    }

    /** One save slot as the menu renders it. */
    public record Slot(
            int index,
            boolean occupied,
            String handle,
            java.math.BigInteger ethecoinWei,
            long totalCycles,
            long playedSeconds,
            Instant lastPlayedAt,
            Instant createdAt,
            String problem,
            /**
             * The character's picture as a base64 PNG, or empty.
             *
             * <p>Carried on the slot so the menu can show a face without opening the save twice —
             * and because the login screen is the one place a picture is doing real work: it is how
             * a player tells three of their own characters apart at a glance, which a handle in
             * eight-point type does less well.
             */
            String avatarPng) {

        static Slot empty(int index) {
            return new Slot(index, false, "", java.math.BigInteger.ZERO, 0, 0, null, null, null, "");
        }

        public boolean unreadable() {
            return problem != null;
        }

        /** A one-line summary for the slot card. */
        public String summary() {
            if (unreadable()) {
                return "unreadable — " + problem;
            }
            if (!occupied) {
                return "empty";
            }
            return handle + "  ·  "
                    + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(ethecoinWei)
                    + "  ·  " + totalCycles + " cycles";
        }

        /** "3 hours played, last seen 2 days ago" — the thing that identifies a run at a glance. */
        public String detail() {
            if (!occupied || unreadable()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(humanDuration(Duration.ofSeconds(playedSeconds))).append(" played");
            if (lastPlayedAt != null) {
                sb.append("  ·  last seen ").append(humanAgo(lastPlayedAt));
            }
            return sb.toString();
        }

        private static String humanDuration(Duration d) {
            long hours = d.toHours();
            if (hours >= 1) {
                return hours + (hours == 1 ? " hour" : " hours");
            }
            long minutes = Math.max(1, d.toMinutes());
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }

        private static String humanAgo(Instant then) {
            Duration ago = Duration.between(then, Instant.now());
            if (ago.isNegative()) {
                return "just now";
            }
            long days = ago.toDays();
            if (days >= 1) {
                return days + (days == 1 ? " day ago" : " days ago");
            }
            long hours = ago.toHours();
            if (hours >= 1) {
                return hours + (hours == 1 ? " hour ago" : " hours ago");
            }
            return "recently";
        }
    }
}
