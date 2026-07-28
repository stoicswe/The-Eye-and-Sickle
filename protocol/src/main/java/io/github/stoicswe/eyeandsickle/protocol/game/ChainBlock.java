package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;

/**
 * One block, shaped the way a block explorer shows one.
 *
 * <h2>⚠ Ethereum's field names over Bitcoin's behaviour, and that is coherent rather than a mash</h2>
 *
 * The header below is modelled on <b>pre-Merge Ethereum</b>, which was itself a proof-of-work chain:
 * it had a {@code nonce}, a {@code difficulty}, a {@code miner} who took the block reward, and
 * {@code extraData} the miner chose. So "Ethereum-shaped data, Bitcoin-shaped mechanics" is not a
 * contradiction — it is roughly what Ethereum 1.0 was, and it is why this chain can carry these
 * fields without any of them being a lie.
 *
 * <p>What is deliberately <em>absent</em> is anything that would be: no contract addresses, no logs,
 * no uncles, no fee market. Every transaction here is a plain value transfer, which is exactly the
 * 21 000 gas Ethereum charges for one, so {@code gasUsed} is honest arithmetic rather than decoration.
 *
 * @param number height
 * @param hash {@code 0x} + 64 hex
 * @param parentHash the block before it — what makes this a chain
 * @param timestamp when it was found
 * @param minerLabel who found it, in words ("YOUR RIG", a pool's name, "unpooled")
 * @param minerAddress that miner's {@code 0x} + 40 hex address
 * @param yours whether this rig mined it
 * @param difficulty the target it was mined against
 * @param nonce the number the miner found, {@code 0x} + 16 hex
 * @param transactions how many transactions it carries
 * @param gasUsed 21 000 per transfer
 * @param gasLimit the block's ceiling
 * @param sizeBytes serialised size
 * @param rewardMinorUnits the subsidy the miner took
 * @param feesMinorUnits the transaction fees the miner also collected
 * @param extraData whatever the miner wrote in it
 * @param body every transaction in the block, highest fee first — empty until asked for
 */
public record ChainBlock(
        long number,
        String hash,
        String parentHash,
        Instant timestamp,
        String minerLabel,
        String minerAddress,
        boolean yours,
        double difficulty,
        String nonce,
        int transactions,
        long gasUsed,
        long gasLimit,
        int sizeBytes,
        long rewardMinorUnits,
        long feesMinorUnits,
        String extraData,
        java.util.List<ChainTransaction> body) {

    public ChainBlock {
        body = body == null ? java.util.List.of() : java.util.List.copyOf(body);
    }

    /** The same header with its transactions attached. Bodies are derived on demand, never stored. */
    public ChainBlock withBody(java.util.List<ChainTransaction> transactions) {
        return new ChainBlock(number, hash, parentHash, timestamp, minerLabel, minerAddress, yours,
                difficulty, nonce, this.transactions, gasUsed, gasLimit, sizeBytes, rewardMinorUnits,
                feesMinorUnits, extraData, transactions);
    }

    /** What the miner took home: the subsidy plus every fee in the block. */
    public long minerTakeMinorUnits() {
        return rewardMinorUnits + feesMinorUnits;
    }

    /** Gas used as a fraction of the limit, for a fill bar. */
    public double fullness() {
        return gasLimit <= 0 ? 0.0d : Math.min(1.0d, gasUsed / (double) gasLimit);
    }

    /** {@code 0x9f3a…c1d0} — a hash short enough for a card. */
    public String shortHash() {
        return shorten(hash);
    }

    /** Middle-elided, the way every explorer renders a hash that will not fit. */
    public static String shorten(String hex) {
        if (hex == null || hex.length() <= 13) {
            return hex == null ? "" : hex;
        }
        return hex.substring(0, 8) + "…" + hex.substring(hex.length() - 4);
    }
}
