package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.solo.state.LedgerEntryState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;

/**
 * Every movement of ethecoin, and the only place the balance changes.
 *
 * <p>Funnelling all mutation through one method is what makes the ledger trustworthy as a
 * <em>readout</em>: the balance shown in the rig strip is not tracked separately from the log that
 * explains it, so the two cannot disagree. That is the same property the real server gets from a
 * transactional ledger table, achieved here by there being exactly one writer.
 */
public final class LedgerRules {

    private LedgerRules() {}

    /** Insufficient funds is a refusal, not an exception path — the caller renders exit status 1. */
    public static boolean canDebit(SoloSave save, long minorUnits) {
        return minorUnits >= 0 && save.ethecoinMinorUnits >= minorUnits;
    }

    /**
     * Applies a signed delta and appends the row that explains it.
     *
     * @return the new balance
     * @throws IllegalArgumentException if the delta would take the balance negative — the caller must
     *     have checked {@link #canDebit} first, and a bug that skips it should be loud
     */
    public static Ethecoin apply(
            SoloSave save, long deltaMinorUnits, String type, String description, Instant now) {
        applyEntry(save, deltaMinorUnits, type, description, now);
        return Ethecoin.ofMinorUnits(save.ethecoinMinorUnits);
    }

    /**
     * The same, returning the row it wrote so a caller can stamp chain metadata on it.
     *
     * <p>⚠ The row is returned <b>after</b> it is already in the ledger, deliberately. A caller that
     * had to add it themselves could forget, and a movement of ethecoin that never reached the ledger
     * is the one thing {@code ledger(1)} promises cannot happen.
     */
    public static LedgerEntryState applyEntry(
            SoloSave save, long deltaMinorUnits, String type, String description, Instant now) {
        long next = save.ethecoinMinorUnits + deltaMinorUnits;
        if (next < 0) {
            throw new IllegalArgumentException(
                    "Ledger would go negative: balance " + save.ethecoinMinorUnits + " delta " + deltaMinorUnits);
        }
        save.ethecoinMinorUnits = next;

        LedgerEntryState entry = new LedgerEntryState();
        entry.at = now;
        entry.deltaMinorUnits = deltaMinorUnits;
        entry.balanceAfterMinorUnits = next;
        entry.type = type;
        entry.description = description;
        save.ledger.add(entry);
        return entry;
    }
}
