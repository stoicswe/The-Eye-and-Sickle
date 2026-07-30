package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import java.util.List;
import java.util.Locale;

/**
 * Everything the rig readout shows, derived once.
 *
 * <h2>Why derivation lives here and not in three views</h2>
 *
 * The strip, the rig-monitor panel and the terminal's {@code ps} all want the same figures. Deriving
 * them in each place is three chances to derive them differently — and a HUD whose two halves
 * disagree about the income rate is worse than one that shows neither, because
 * {@code docs/design/04-mining.md} §3.1 trains the player to treat a discrepancy as evidence.
 *
 * <h2>What this is allowed to compute, and what it is not</h2>
 *
 * Client pillar <b>C4</b> forbids the client claiming authority it does not have: no gate evaluation,
 * no loot rolls, no optimistic outcomes. It does <em>not</em> forbid arithmetic on published rates —
 * {@code docs/client/04} §3.4 explicitly endorses "printing the numbers and letting the player do
 * the arithmetic", and the mining window has always shown a projected EC/hr.
 *
 * <p>The line this holds: everything here is a <b>rate or a posture derived from a published
 * constant and current state</b>. Nothing here decides whether an action will succeed, whether a
 * gate opens, or what a balance is. The balance comes from the session. The projection is labelled
 * as a projection.
 */
public record RigStatus(
        ComputeBudget budget,
        long selfMiningCycles,
        java.math.BigInteger incomeWeiPerHour,
        int armedDefenses,
        long defenseCycles,
        DefensePosture posture,
        HeatBand heat,
        int personalHeat,
        int deployedMiners,
        java.math.BigInteger bufferedWei,
        java.math.BigInteger bufferCapWei,
        double noise,
        boolean connected) {

    // ⚠ There is no rate constant here any more, and there must not be one again.
    //
    // This class used to carry `MINOR_UNITS_PER_CYCLE_HOUR = 40` and multiply. That was a second
    // implementation of a balance number in a view class — the same mistake the `noise` note below
    // records — and it went wrong the moment self-mining became a Poisson process on 2026-07-27: a
    // solo miner earns the pool's fee back, so 40 stopped being the answer for half the players
    // while the readout kept printing it. The engine publishes the expectation; this draws it.

    public static RigStatus of(GameSession session) {
        ComputeBudget budget = session.computeBudget();
        GameSession.MiningSummary mining = session.mining();
        List<GameSession.ArmedDefense> defenses = session.defenses();

        long defenseCycles = defenses.stream()
                .mapToLong(GameSession.ArmedDefense::reservedCycles)
                .sum();

        return new RigStatus(
                budget,
                mining.selfMiningCycles(),
                session.miningChain().expectedWeiPerHour(),
                defenses.size(),
                defenseCycles,
                DefensePosture.of(defenses.size(), defenseCycles),
                HeatBand.of(session.personalHeat()),
                session.personalHeat(),
                mining.deployedMiners(),
                mining.bufferedWei(),
                mining.bufferCapWei(),
                session.noise(),
                session.connected());
    }

    /**
     * Income per second, as a display string.
     *
     * <p>Shown to four decimal places because at realistic allocations the per-second figure is
     * genuinely tiny — 100 cycles is 0.0111 EC/s — and rounding it to two would show a flat
     * {@code 0.01} that never moves, which is the opposite of the point. The hourly figure beside it
     * is the one a player actually reasons with.
     */
    public String incomePerSecond() {
        // ⚠ EC per second, computed in BigDecimal. This divided a hundredths-scale long by 100;
        // at 18 decimals the same expression would round the amount to a double before dividing.
        return new java.math.BigDecimal(incomeWeiPerHour)
                .divide(new java.math.BigDecimal(
                        io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.WEI_PER_ETHECOIN))
                .divide(java.math.BigDecimal.valueOf(3600), 4, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    public String incomePerHour() {
        return new java.math.BigDecimal(incomeWeiPerHour)
                .divide(new java.math.BigDecimal(
                        io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.WEI_PER_ETHECOIN))
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    // ⚠ `noise` is a component, read straight off the port, and this class no longer derives it.
    //
    // It used to: a switch over ComputeConsumer, summing held cycles on the outward ones. That put
    // three invariants (I4, I9, I6) inside a view class — the one place docs/design/00 §4 says a rule
    // may never live — and it silently made a sweep read as almost nothing, because a sweep holds two
    // cycles and is one of the loudest acts in the game. The rule is now in
    // solo/rules/NoiseRules, where the engine can state a task's loudness separately from its cost
    // and where a home server's answer arrives through the same field.

    /** Fraction of capacity currently committed, for the gauge. */
    public double load() {
        long total = budget.total().cycles();
        return total == 0 ? 0 : (double) (total - budget.available().cycles()) / total;
    }

    /** How full the deployed-miner buffers are, 0–1. The cap is what bounds offline income. */
    public double bufferFill() {
        // ⚠ A FRACTION, so a double output is right and the division is what makes it safe: both
        // operands are wei and the scale cancels. Converting either alone would be the lossy step.
        return bufferCapWei.signum() == 0
                ? 0
                : new java.math.BigDecimal(bufferedWei)
                        .divide(new java.math.BigDecimal(bufferCapWei), java.math.MathContext.DECIMAL64)
                        .doubleValue();
    }

    /**
     * Whether the rig readout adds up.
     *
     * <p>{@code docs/design/04-mining.md} §3.1 makes finding a hidden miner a matter of noticing that
     * the numbers do not. That only works if they normally do.
     */
    public boolean reconciles() {
        return budget.reconciles();
    }

    /**
     * The five heat bands from {@code docs/client/01-visual-language.md} §2.2.4, with the sweep
     * chances {@code docs/design/04-mining.md} §4 attaches to each.
     *
     * <p>§2.2.4's rule is emphatic and this type exists to obey it: <b>heat renders as a banded chip
     * carrying the band name, never as a continuous meter.</b> The player's decision is a threshold
     * decision — which vendors are reachable, how likely a sweep is — and a smooth bar would invite a
     * precision the model does not have. It also keeps heat from being confused with trace, which is
     * the client's only continuous red meter.
     */
    public enum HeatBand {
        ZERO(0, "cold", "2% sweep chance per hour"),
        LOW(1, "low", "~8% sweep chance per hour"),
        MODERATE(2, "moderate", "~25% sweep chance per hour"),
        HIGH(3, "high", "~45% sweep chance per hour"),
        NAMED(4, "named-hacker", "~60% sweep chance per hour; the Eye pursues you by name");

        private final int index;
        private final String label;
        private final String consequence;

        HeatBand(int index, String label, String consequence) {
            this.index = index;
            this.label = label;
            this.consequence = consequence;
        }

        public int index() {
            return index;
        }

        public String label() {
            return label;
        }

        /** What this band actually means, for the tooltip. A band name alone is trivia. */
        public String consequence() {
            return consequence;
        }

        /** The token class carrying this band's colour, which the theme defines per skin. */
        public String styleClass() {
            return "es-heat-" + index;
        }

        public static HeatBand of(int personalHeat) {
            if (personalHeat >= 80) {
                return NAMED;
            }
            if (personalHeat >= 55) {
                return HIGH;
            }
            if (personalHeat >= 30) {
                return MODERATE;
            }
            if (personalHeat >= 10) {
                return LOW;
            }
            return ZERO;
        }
    }

    /**
     * How defended the rig currently is, as a posture rather than a number.
     *
     * <p>Same reasoning as the heat band: what a player decides with is a threshold ("am I covered
     * enough to go do something noisy"), not a percentage. {@code docs/design/09-defense-and-hardening.md}
     * §3's whole point is that a full loadout costs more than a starting rig has, so the interesting
     * question is where on that spectrum you have chosen to sit.
     */
    public enum DefensePosture {
        NONE("undefended", "Nothing is armed. Anything reaching this rig meets no resistance."),
        MINIMAL("minimal", "One defence armed. Better than nothing, and not much better."),
        PARTIAL("partial", "A working posture, with gaps you have chosen."),
        LAYERED("layered", "Several defences that fail differently — which is what layering buys."),
        PARANOID("paranoid", "Most of the rig is committed to not being touched. That is a real cost.");

        private final String label;
        private final String explanation;

        DefensePosture(String label, String explanation) {
            this.label = label;
            this.explanation = explanation;
        }

        public String label() {
            return label;
        }

        public String explanation() {
            return explanation;
        }

        public int pips() {
            return ordinal();
        }

        static DefensePosture of(int armed, long cycles) {
            // Counts AND cycles, because three cheap defences and one expensive one are different
            // postures even though the first has more of them. Layering is about independent
            // failure modes; committed capacity is about how much you gave up for it.
            if (armed == 0) {
                return NONE;
            }
            if (cycles >= 40) {
                return PARANOID;
            }
            if (armed >= 3) {
                return LAYERED;
            }
            if (armed == 2) {
                return PARTIAL;
            }
            return MINIMAL;
        }
    }
}
