package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Objects;

/**
 * How one breach attempt ended, and everything it left behind.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §2 lists what a breach produces: an outcome, a
 * {@code noiseGenerated} scalar, {@code traceProgress}, loot on success or consequence on failure, and
 * the persisted {@code resolutionRecord}. This record is that list, once. {@link ResolutionRecord} is
 * carried whole rather than flattened into fields, because it is the part §2 promised would last — it
 * is what proof-of-skill ({@code docs/design/02-unlock-gates.md} §2.4) and the bot-salvage guard
 * ({@code docs/design/10-botnets.md}, Invariant I13) read, and it must stay recognisably the same four
 * fields wherever it turns up.
 *
 * <h2>{@code heatGained} is zero on a crack, on every outcome, including failure</h2>
 *
 * Invariant I9: defending your own rig never generates heat. {@code docs/design/04-mining.md} §5.1
 * spells out the consequence for cracking specifically — "low noise, <strong>no heat</strong>" — and
 * §5.1's tutorial note is what depends on it: cracking is "the strongest early-game teaching vector for
 * the core minigame … comprehensible failure, no heat cost for losing". A player has to be able to lose
 * their first breach repeatedly and be no worse off for it. This record cannot tell on its own whether
 * the attempt was a crack; {@link BreachSnapshot} can, and checks.
 *
 * <h2>{@code lootMinorUnits} is a transfer, never a faucet</h2>
 *
 * {@code docs/design/04-mining.md} §5.1: a cracked miner's buffer "physically resides on the host's
 * machine … so the EC is already there to take — a transfer, not a faucet; no new currency enters the
 * economy." An offensive breach yields salvaged <em>items</em> and never ethecoin at all
 * ({@code docs/design/03-economy.md} §5 rule 3, Invariants I1 and I2). Money is a {@code long} of minor
 * units throughout this codebase; there is no floating-point currency anywhere.
 *
 * <h2>A failure always says why</h2>
 *
 * §1 constraint 4 — "it must have a comprehensible failure state" — is the constraint the entire
 * attention ledger exists to serve, and it does not stop at the last row. A {@link BreachOutcome#FAILED}
 * resolution with an empty {@code consequences} list is a loss the player is told nothing about, which
 * is the failure mode "never <em>the game decided</em>" names. The constructor refuses it. An
 * {@link BreachOutcome#ABORTED} may legitimately be quiet: walking away is a decision the player
 * already understands, and §4.1's escape hatch should not be dressed up as a punishment.
 *
 * @param record the four-field contract §2 promised the economy
 * @param noiseGenerated §2's scalar — a function of the tools used and the alarms tripped
 * @param traceProgress attention consumed over the whole attempt as a fraction of the whole budget,
 *     0..1; §4's redefinition of §2's field
 * @param heatGained personal heat added by this attempt; always 0 on a miner crack (Invariant I9)
 * @param lootMinorUnits ethecoin seized, in minor units; non-zero only on a successful crack
 * @param lootLabel what was taken, in words; {@code ""} when nothing was
 * @param schematicMaterial tier-gated partial-progress material awarded ({@code
 *     docs/design/02-unlock-gates.md} §2.2, Invariant I13); 0 when the gate did not open
 * @param consequences the itemised aftermath, in the order it happened; never empty on a failure
 */
public record BreachResolution(
        ResolutionRecord record,
        int noiseGenerated,
        double traceProgress,
        int heatGained,
        long lootMinorUnits,
        String lootLabel,
        int schematicMaterial,
        List<String> consequences) {

    public BreachResolution {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(lootLabel, "lootLabel");

        consequences = List.copyOf(consequences);

        if (noiseGenerated < 0) {
            throw new IllegalArgumentException("noiseGenerated must not be negative, was " + noiseGenerated);
        }
        if (!(traceProgress >= 0.0 && traceProgress <= 1.0)) {
            // Written as a positive range test so a NaN — the signature of a division by a zero budget
            // somewhere upstream — fails here rather than propagating into a meter that draws nothing.
            throw new IllegalArgumentException(
                    "traceProgress is attention consumed over budget, 0..1, was " + traceProgress);
        }
        if (heatGained < 0) {
            throw new IllegalArgumentException("heatGained must not be negative, was " + heatGained);
        }
        if (lootMinorUnits < 0) {
            throw new IllegalArgumentException("lootMinorUnits must not be negative, was " + lootMinorUnits);
        }
        if (lootMinorUnits > 0 && lootLabel.isEmpty()) {
            throw new IllegalArgumentException(
                    "Loot without a label is an unattributable payout: " + lootMinorUnits + " minor units, no name");
        }
        if (schematicMaterial < 0) {
            throw new IllegalArgumentException("schematicMaterial must not be negative, was " + schematicMaterial);
        }
        if (record.outcome() == BreachOutcome.FAILED && consequences.isEmpty()) {
            throw new IllegalArgumentException("A failed breach must itemise what it cost "
                    + "(docs/design/05-hacking-minigame.md §1 constraint 4); consequences was empty");
        }
    }
}
