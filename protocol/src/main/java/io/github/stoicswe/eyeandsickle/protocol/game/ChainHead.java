package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * The tip of a chain, as one server tells another about it.
 *
 * <h2>What "longest" actually means, and why it is not the height</h2>
 *
 * The rule is <b>most accumulated work</b>, not most blocks. Height is the intuitive reading and it is
 * wrong in the one case that matters: a fork of many easy blocks can be taller than a fork of fewer
 * hard ones, and adopting it would mean an attacker could out-vote the honest chain by lowering
 * difficulty rather than by doing work. Bitcoin compares cumulative difficulty for exactly this
 * reason, so {@link #totalWork} is the field that decides and {@link #height} is only ever shown.
 *
 * <p>⚠ A head is a <b>claim</b>, not evidence. Anyone can assert any numbers. Nothing may be adopted
 * on the strength of one — a peer that answers with a taller head has to be able to produce the blocks
 * — which is why {@code ChainSelection} is careful to say it picks a head to <em>fetch</em> rather than
 * a chain to believe.
 *
 * @param height the tip's block number
 * @param hash the tip's hash
 * @param totalWork accumulated difficulty over the whole chain — what actually decides
 * @param genesisHash the first block, so two chains can tell they are even comparable
 */
public record ChainHead(long height, String hash, double totalWork, String genesisHash) {

    public ChainHead {
        if (height < 0) {
            throw new IllegalArgumentException("height cannot be negative: " + height);
        }
        if (totalWork < 0 || !Double.isFinite(totalWork)) {
            throw new IllegalArgumentException("totalWork must be finite and non-negative: " + totalWork);
        }
        hash = hash == null ? "" : hash;
        genesisHash = genesisHash == null ? "" : genesisHash;
    }

    /**
     * Whether this head is on the same chain as {@code other} — same genesis.
     *
     * <p>⚠ Checked before any comparison. Two servers that were never seeded from the same genesis are
     * running different currencies, and adopting one head over the other would not be a reorganisation
     * but a silent migration of every balance onto somebody else's ledger.
     */
    public boolean comparableWith(ChainHead other) {
        return other != null && !genesisHash.isBlank() && genesisHash.equals(other.genesisHash());
    }
}
