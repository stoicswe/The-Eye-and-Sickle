package io.github.stoicswe.eyeandsickle.solo.save;

import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes a save file.
 *
 * <h2>Atomic writes, because the alternative is losing someone's run</h2>
 *
 * A save is written to a sibling temporary file and then moved into place. The move is atomic on
 * every filesystem this client will realistically meet, which means a crash — or a laptop lid closing
 * mid-write — leaves either the old save or the new one, never a half-written file that parses as far
 * as the inventory and then stops.
 *
 * <p>Naively overwriting in place would be one line shorter and would eventually eat a player's
 * character. Autosave makes that a near-certainty over a long enough population: the window is small,
 * but it is entered on every single save.
 *
 * <h2>Human-readable on purpose</h2>
 *
 * The file is pretty-printed JSON. It is a single-player save on the player's own disk — obfuscating
 * it would buy nothing against anybody who wanted to edit it, and would cost the ability to diff a
 * save, mail one to a bug report, or hand-repair one after a bad release. The threat model here has
 * exactly one participant and they own the machine.
 */
public final class SaveStore {

    /**
     * Jackson 3 registers {@code java.time} support from {@code databind} itself, so an {@link
     * java.time.Instant} round-trips with no extra module on the classpath. Written as ISO-8601
     * strings rather than epoch numbers so the file stays readable by the human it belongs to.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            // Jackson 3 moved the date-format switches out of SerializationFeature into
            // DateTimeFeature, and flipped the default: java.time values now serialize as ISO-8601
            // strings rather than numeric timestamps. That is what we want, so this is disabled
            // explicitly rather than relied upon — a default that changed once can change again, and
            // a save file full of epoch nanoseconds would be unreadable by the person it belongs to.
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final Path file;

    public SaveStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public boolean exists() {
        return Files.isRegularFile(file);
    }

    /**
     * Loads the save, or returns {@code null} if there is not one yet.
     *
     * @throws UnreadableSaveException if the file exists but cannot be used — a corrupt file, or one
     *     written by a newer version of the game. Both are refused loudly rather than partially
     *     applied, because a half-loaded character is worse than an error message.
     */
    public SoloSave load() {
        if (!exists()) {
            return null;
        }
        SoloSave save;
        try {
            save = MAPPER.readValue(Files.readString(file), SoloSave.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read save " + file, e);
        } catch (RuntimeException e) {
            throw new UnreadableSaveException("Save file " + file + " is not readable as a save", e);
        }
        if (save == null) {
            throw new UnreadableSaveException("Save file " + file + " is empty", null);
        }
        if (save.format > SoloSave.CURRENT_FORMAT) {
            // Downgrading is refused rather than attempted. A newer save may contain state this
            // build has no rule for, and silently dropping it is how a player loses progress they
            // can see in the file.
            throw new UnreadableSaveException(
                    "Save file " + file + " has format " + save.format + ", but this build understands at most "
                            + SoloSave.CURRENT_FORMAT + ". Update the game to load it.",
                    null);
        }
        return save;
    }

    /** Writes the save atomically, creating parent directories if needed. */
    public void save(SoloSave save) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, MAPPER.writeValueAsString(save));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                // Some network and FUSE filesystems refuse ATOMIC_MOVE. A plain replace is still
                // better than writing in place, and it is the best available on that volume.
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write save " + file, e);
        }
    }

    /** Thrown when a save exists but must not be used. */
    public static final class UnreadableSaveException extends RuntimeException {
        public UnreadableSaveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
