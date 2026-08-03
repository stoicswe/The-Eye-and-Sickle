package io.github.stoicswe.eyeandsickle.client.profile;

import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
 * <h2>Solo slots are files; online slots are not</h2>
 *
 * A solo slot is a JSON file in the profile directory and nothing else. An <em>online</em> slot lives
 * on a home server, keyed to a DID, and this client cannot enumerate one without the transport that
 * <b>CL-8</b> still lacks — so {@link #onlineSlots} returns what the profile has cached about servers
 * the player has named, and says plainly that it cannot list characters yet. Inventing a plausible
 * list would be the worst option available.
 */
public final class CharacterSlots {

    /** Matches the online cap in {@code docs/architecture/09}. */
    public static final int SLOT_COUNT = 3;

    private final ClientProfile profile;

    public CharacterSlots(ClientProfile profile) {
        this.profile = profile;
    }

    /** Where slot {@code n}'s save lives. Slot 1 keeps the original filename so no save is orphaned. */
    public Path saveFile(int slot) {
        // Slot 1 is deliberately `solo-save.json` rather than `solo-save-1.json`: that is the name
        // every save written before slots existed already has, and silently stranding somebody's
        // character behind a rename would be an unforced loss.
        return slot == 1
                ? profile.directory().resolve("solo-save.json")
                : profile.directory().resolve("solo-save-" + slot + ".json");
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
        Path file = saveFile(index);
        if (!Files.isRegularFile(file)) {
            return Slot.empty(index, file);
        }
        try {
            SoloSave save = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file).load();
            if (save == null) {
                return Slot.empty(index, file);
            }
            return new Slot(
                    index,
                    file,
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
            // A corrupt or future-format save is shown as such rather than hidden. A slot that
            // silently reads as empty invites the player to overwrite the thing they were trying to
            // recover.
            return new Slot(
                    index, file, false, "", java.math.BigInteger.ZERO, 0, 0, null, null, unreadable.getMessage(), "");
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
        try {
            return Files.deleteIfExists(saveFile(slot));
        } catch (IOException e) {
            return false;
        }
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
            Path file,
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

        static Slot empty(int index, Path file) {
            return new Slot(index, file, false, "", java.math.BigInteger.ZERO, 0, 0, null, null, null, "");
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
