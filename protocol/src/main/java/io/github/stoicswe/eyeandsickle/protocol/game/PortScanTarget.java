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
    FIREWALL("Firewall posture", "what is filtered, and how hard", 1),
    OS_VERSION("OS and version", "banner and stack fingerprint", 2),
    CYCLE_CAPABILITY("Cycle capability", "how big the machine is", 3),
    CYCLE_LOAD("Cycles free / used", "a snapshot, stale the moment it is taken", 4),
    DOWNLOADS("Downloads folder", "how much is sitting in it", 5),
    VAULT_HIGH("High-risk vault", "how many items are in the exposed tier", 6),
    VAULT_MEDIUM("Medium-risk vault", "an estimate, never a count", 7);

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

    /** How deep the scan has to go, 1–7. Everything at or above this depth comes back with it. */
    public int depth() {
        return depth;
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
