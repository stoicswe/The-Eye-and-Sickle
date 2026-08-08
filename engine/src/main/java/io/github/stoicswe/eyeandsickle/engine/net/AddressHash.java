package io.github.stoicswe.eyeandsickle.engine.net;

import java.nio.charset.StandardCharsets;

/**
 * FNV-1a over a machine's address — the one way this package turns an address into a number.
 *
 * <h2>⚠ NEVER {@code String.hashCode}, AND THIS IS MEASURED RATHER THAN ASSUMED</h2>
 *
 * {@code String.hashCode} is {@code 31·h + c}, so two addresses differing by one in their last
 * character land one apart, and a modulo over them walks a pool <b>in declaration order</b>.
 * {@code VirtualFs.hostUser} shipped exactly that: the first machines of every server were
 * {@code wren dana kai morgan riley sasha toma ves} — the pool in order, offset by the server index.
 * The "random" name was the host index in disguise. {@code NpcNames} records that this was the
 * <b>second</b> time the trap had bitten here, which is why the mixing now has one home.
 *
 * <h2>⚠ HASHED, NOT DRAWN — the constraint that makes this class necessary at all</h2>
 *
 * Anything derived per machine has to be a pure function of the address, never a draw against the
 * save's RNG. {@code TopologyGenerator}'s draw count is a pure function of the world's shape, so a
 * single extra {@code nextDouble()} per host would <b>re-roll every existing world</b> — every name,
 * every detect roll, every document. {@code SweepDeterminismTest} asserts the exact number of draws a
 * world consumes, and would fail on the spot.
 *
 * <p>The other half of the same rule is {@code NetRules}': <em>"Detection is a roll made once, at
 * world generation, and stored. Nothing here draws for detection, ever."</em> A hash satisfies both —
 * it is fixed before the player arrives, and asking twice cannot give two answers.
 *
 * <h2>⚠ {@code DocumentPool} still carries its own copy</h2>
 *
 * {@code NpcNames} used to as well and now calls this. {@code DocumentPool.forAddress} is the last
 * duplicate; its own comment already says the two are "identical, and deliberately so". It should
 * migrate here — a third copy is the point at which deliberate duplication becomes nobody having
 * extracted it — but it has its own distribution tests and is left alone rather than changed in
 * passing.
 */
final class AddressHash {

    private AddressHash() {}

    /** FNV-1a, folded to 32 bits. Null-safe, because a hand-edited save is not a promise. */
    static long of(String address) {
        long h = 0xCBF29CE484222325L;
        for (byte b : String.valueOf(address).getBytes(StandardCharsets.UTF_8)) {
            h = (h ^ (b & 0xFFL)) * 0x100000001B3L;
        }
        return h ^ (h >>> 32);
    }

    /**
     * A stable {@code 0…1} for one machine and one purpose.
     *
     * <h2>⚠ THE SALT IS WHAT KEEPS TWO DERIVATIONS INDEPENDENT</h2>
     *
     * Without it, everything hashed off an address would agree: the bridges carrying a MonJob would be
     * exactly the bridges whose name started with the same letter, and a player would eventually read
     * the correlation even if they could not name it. Each caller passes its own salt, so
     * "is this monitored" and "what is it called" are uncorrelated over the same address space.
     */
    static double unitOf(String address, String salt) {
        // ⚠ FNV-1a ALONE IS NOT ENOUGH HERE, AND THIS WAS MEASURED AFTER GETTING IT WRONG.
        //
        // FNV-1a XORs each byte into the LOW bits and then multiplies, so a one-character change at
        // the end of a string reaches the high bits only through carry propagation — weakly. The
        // first version of this method took the top 53 bits, with a confident comment claiming the
        // LOW ones were the unreliable half. It is the other way round, and the cost was total: ten
        // consecutive addresses, 10.3.0.10 through 10.3.0.19, every one of them returned 0.980.
        //
        // `of`'s closing fold (h ^ h>>>32) is what carries entropy DOWNWARD, which is exactly why
        // NpcNames — which takes a modulo, i.e. the low bits — has never had this problem.
        //
        // ⚠ Rather than swap which end is trusted, finalise properly so the question stops
        // existing. This is murmur3's finalizer: two multiply-xorshift rounds that avalanche a
        // single-bit input change across all 64 output bits. Any slice is then safe to take, so the
        // next person reaching for a different one cannot reintroduce this.
        long h = of(salt + ' ' + address);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) / (double) (1L << 53);
    }
}
