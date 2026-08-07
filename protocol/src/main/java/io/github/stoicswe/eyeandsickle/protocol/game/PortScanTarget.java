package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * What a port scan is trying to find out — and, by naming it, how hard it is prepared to look.
 *
 * <h2>⚠ The player picks a QUESTION, not a tier</h2>
 *
 * A tier list ("light / medium / deep") asks the player to guess what a level buys before they have
 * any way to know. Naming the deepest thing you want instead makes the cost self-explanatory: you are
 * paying for that answer, and everything cheaper comes with it because a scan that reached that far
 * necessarily passed through the rest. It is also how the real tools read — you do not run
 * {@code nmap -A} because you wanted level four, you run it because you wanted the OS.
 *
 * <h2>The ladder, and why it is in this order</h2>
 *
 * Each rung needs strictly more of the target's attention than the one above it, and the ordering is
 * the ordering of how <em>loudly</em> you have to ask:
 *
 * <ol>
 *   <li>{@link #IDENTITY} — what the machine calls itself, and whose account runs it. The cheapest
 *       rung there is, and it is cheapest for a real reason: a name is the one thing a network hands
 *       out without being asked. Reverse DNS answers it with no packet sent to the target at all
 *       ({@code nmap -sL} is exactly this), and mDNS, NetBIOS and a login banner all volunteer it.
 *   <li>{@link #FIREWALL} — a closed port answers differently from a filtered one. This is nearly
 *       free, because refusing you <em>is</em> an answer.
 *   <li>{@link #OS_VERSION} — banner grabbing and stack fingerprinting. Still passive-ish, and real:
 *       TCP/IP stacks differ in ways that identify them.
 *   <li>{@link #CYCLE_CAPABILITY} — how big the machine is. Needs enough probing to characterise it.
 *   <li>{@link #CYCLE_LOAD} — what it is doing <em>right now</em>. A snapshot, and stale the moment
 *       it is taken, which is why it says so.
 *   <li>{@link #DOWNLOADS} — how much is sitting in the download folder. You are now touching the
 *       filesystem rather than the network.
 *   <li>{@link #VAULT_HIGH} — how many items are in the exposed tier. Countable, because the hot zone
 *       is exposed by construction ({@code docs/design/01-core-resources.md} §6).
 *   <li>{@link #VAULT_MEDIUM} — what is in the middle tier, and only ever as an <b>estimate</b>. The
 *       vault proper is never readable at any depth, which is what the tiers are for.
 * </ol>
 *
 * <h2>⚠ Depth costs noise, and that is the entire decision</h2>
 *
 * Every rung down costs more cycles, takes longer, and raises the chance the target notices — at
 * which point they can refuse the scan or come back at you. Without that, the deepest scan would be
 * strictly correct every time and there would be no choice to make.
 */
public enum PortScanTarget {
    IDENTITY("Name and operator", "what it calls itself, and whose account runs it", 1),
    FIREWALL("Firewall posture", "what is filtered, and how hard", 2),
    OS_VERSION("OS and version", "banner and stack fingerprint", 3),
    CYCLE_CAPABILITY("Cycle capability", "how big the machine is", 4),
    CYCLE_LOAD("Cycles free / used", "a snapshot, stale the moment it is taken", 5),
    DOWNLOADS("Downloads folder", "how much is sitting in it", 6),
    VAULT_HIGH("High-risk vault", "how many items are in the exposed tier", 7),
    VAULT_MEDIUM("Medium-risk vault", "an estimate, never a count", 8);

    private final String label;
    private final String detail;
    private final int depth;

    PortScanTarget(String label, String detail, int depth) {
        this.label = label;
        this.detail = detail;
        this.depth = depth;
    }

    public String label() {
        return label;
    }

    public String detail() {
        return detail;
    }

    /**
     * How deep the scan has to go, 1–8. Everything at or above this depth comes back with it.
     *
     * <h2>⚠ Depth is an ORDER, and {@code PortScanRules} prices the STEPS above the cheapest rung</h2>
     *
     * {@link #IDENTITY} was added to the bottom of this ladder after the other seven had been
     * calibrated, which shifted every one of them up a number. The costs are keyed on
     * {@code depth − 1} for exactly that reason: {@code CLAUDE.md} makes the economy numbers a set
     * that is re-checked together rather than spot-edited, and a formula reading {@code depth}
     * directly would have raised the price, the duration, the noise and the detection risk of all
     * seven existing rungs as a side effect of inserting one below them — invisibly, since every
     * screen would still have rendered.
     */
    public int depth() {
        return depth;
    }

    /**
     * How many rungs sit below this one. What the cost formulas are actually built on.
     *
     * <p>Zero for {@link #IDENTITY}, which is what makes it the floor rather than a re-tune of
     * everything above it — see {@link #depth()}.
     */
    public int steps() {
        return depth - 1;
    }

    /** Whether a scan aimed at {@code deepest} also answers this one. */
    public boolean reachedBy(PortScanTarget deepest) {
        return deepest != null && depth <= deepest.depth;
    }

    /** The deepest rung there is — what a scan asking for everything is asking for. */
    public static PortScanTarget deepest() {
        return VAULT_MEDIUM;
    }
}
