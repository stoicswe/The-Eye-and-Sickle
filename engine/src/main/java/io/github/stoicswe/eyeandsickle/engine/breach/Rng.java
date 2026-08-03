package io.github.stoicswe.eyeandsickle.engine.breach;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * The solo runtime's only source of randomness: splitmix64, seeded from the save and written back
 * to it.
 *
 * <h2>Why not {@code java.util.Random}</h2>
 *
 * This module's whole discipline is that it is a pure function of {@code (save, clock)} — that is
 * what lets {@code GameEngineTest} assert exact ethecoin figures and what makes a bug reproducible
 * from a save file. A {@code Random} field would be neither seeded from the save nor written back
 * to it, so the same save would produce different games on two loads, and nothing would be
 * reproducible again.
 *
 * <h2>⚠ Save scumming is the failure this exists to prevent</h2>
 *
 * A breach board and a scan's false positive are both draws. If a draw were rerollable by quitting
 * without saving, both would be advisory: a player who did not like their board would reload until
 * they got one they did, and a scan that said the wrong thing would be re-run until it said the
 * right thing. That would gut {@code docs/design/04-mining.md} §3.2a — "a scan hit is a lead to
 * corroborate, not an answer" only means something when the answer is fixed.
 *
 * <p>The mechanism is small and the discipline is absolute: <b>every rule that draws must call
 * {@link #commit(GameSave)} before it returns.</b> Uncommitted draws advance a local counter and
 * nothing else, so a reload replays them identically — which is the same bug wearing the opposite
 * mask. Boards are additionally generated <em>once, at breach start, and persisted</em>, so a reload
 * mid-breach replays nothing at all.
 *
 * <h2>splitmix64, hand-rolled</h2>
 *
 * Sixteen lines, no dependency, and a state that is exactly one {@code long} — which is what makes
 * it storable in a JSON save as a single field a human can read. The algorithm is the one Java's own
 * {@code SplittableRandom} uses to mix its seed; it is written out here rather than reached for
 * through the JDK class because {@code SplittableRandom} would again own state this class needs to
 * hand back to the save on every draw.
 */
public final class Rng {

    /**
     * The golden-ratio odd increment splitmix64 steps its state by. Any odd constant works; this one
     * is the published choice and is used here so the generator is a known quantity rather than a
     * home-made one.
     */
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    /** Reads the seed off the save. The caller must {@link #commit} back before returning. */
    public static Rng of(GameSave save) {
        return new Rng(save.rngSeed);
    }

    public long nextLong() {
        state += GAMMA;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * A value in {@code [0, boundExclusive)}.
     *
     * <p><b>Lemire's multiply-shift, without the rejection loop.</b> Take the top 64 bits of the
     * 128-bit product of a uniform 64-bit draw and the bound, which is {@code floor(x * bound /
     * 2^64)}. The bias this leaves is at most {@code bound / 2^64} — for the largest bound this game
     * ever passes (a few hundred) that is under {@code 2e-17}, which is smaller than the difference
     * between any two boards a player could tell apart.
     *
     * <p>The rejection loop is omitted deliberately rather than forgotten: it would consume a
     * variable number of draws, which makes the sequence depend on the values it produced, which
     * makes a replay from a stored seed depend on the code path as well as the seed. A generator
     * whose stream shape can change under refactoring is not the reproducibility guarantee this
     * class is here to give.
     */
    public int nextInt(int boundExclusive) {
        if (boundExclusive <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + boundExclusive);
        }
        return (int) Math.unsignedMultiplyHigh(nextLong(), Integer.toUnsignedLong(boundExclusive));
    }

    /** A value in {@code [0, 1)}, from the top 53 bits — the exactly-representable double range. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public <T> T pick(List<T> from) {
        if (from.isEmpty()) {
            throw new IllegalArgumentException("cannot pick from an empty list");
        }
        return from.get(nextInt(from.size()));
    }

    /** Fisher-Yates, in place, consuming exactly {@code size - 1} draws. */
    public void shuffle(List<?> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            swap(list, i, nextInt(i + 1));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void swap(List<T> list, int a, int b) {
        T tmp = list.get(a);
        list.set(a, list.get(b));
        list.set(b, tmp);
    }

    public long state() {
        return state;
    }

    /**
     * Writes the advanced state back to the save.
     *
     * <p>⚠ Call this after every batch of draws, before returning to the caller. This is the single
     * most important correctness property in the breach engine: without it the save still holds the
     * seed the draws started from, and reloading rerolls everything that was decided since.
     */
    public void commit(GameSave save) {
        save.rngSeed = state;
    }

    /**
     * A starting seed for a new character, derived from the character's own identity.
     *
     * <p>Deterministic from its inputs and never from an ambient clock read inside the rules — the
     * caller passes the {@code now} it already has, for the same reason every other timestamp in
     * this module does. Two characters created in the same millisecond still differ, because the
     * character id is a fresh UUID.
     *
     * <p>Mixed rather than concatenated: an unmixed seed made of a hash and a millisecond count has
     * long runs of identical high bits between characters created near each other, and splitmix64
     * seeded with near-identical states produces near-identical first draws.
     */
    public static long derive(String characterId, Instant createdAt) {
        long h = 0xCBF29CE484222325L;
        for (byte b : String.valueOf(characterId).getBytes(StandardCharsets.UTF_8)) {
            h = (h ^ (b & 0xFFL)) * 0x100000001B3L;
        }
        Rng mixer = new Rng(h ^ (createdAt == null ? 0L : createdAt.toEpochMilli() * GAMMA));
        return mixer.nextLong();
    }
}
