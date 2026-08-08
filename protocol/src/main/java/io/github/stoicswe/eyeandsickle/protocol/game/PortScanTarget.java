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
    VAULT_MEDIUM("Medium-risk vault", "an estimate, never a count", 8),

    /**
     * How many machines hang off a bridge's far side, and what that server is called.
     *
     * <p>⚠ <b>A count and a name, never addresses, kinds, tiers or values.</b> It is information you
     * cannot act on: the far side is still outside the hop ceiling, still unreachable without a
     * breach, a foothold and a {@code connect}. That is what keeps it clear of Invariant <b>I2</b> —
     * it is a property of a machine already in your files, like its firewall tier, not a window past
     * it. See {@code docs/design/17} §3.
     *
     * <p>⚠ Depth 4, sharing with {@link #CYCLE_CAPABILITY} rather than sitting below
     * {@link #VAULT_MEDIUM}. Depth is an <em>order</em>, and applicability decides which rung a given
     * machine actually has at that order — so a bridge's peer count costs a depth-4 scan rather than
     * being the dearest finding in the game, which is what appending it at 9 would have made it.
     */
    PEERS("Peers", "how many machines are on the far side, and what that server is called", 4),

    /**
     * Whether this bridge carries a MonJob — {@code docs/design/17} §4.4, MJ-4.
     *
     * <p>⚠ <b>"Monitored", and nothing else.</b> Never whose, never what tier. One rule for NPC and
     * player MonJobs, and it is what stops tier 1 becoming worthless: an intruder learns that crossing
     * would be seen, and still does not learn whether they <em>were</em> seen, what was logged, or by
     * whom. Turning that one fact into an identity is the Tracer's job (§5).
     *
     * <p>⚠ This makes distance-risk a <b>decision</b> — scout, then choose — rather than a tax the
     * player pays blind. It also means a MonJob deters even when it never fires, which is what real
     * monitoring does: it catches the careless and warns off the careful.
     */
    MONITORED("Monitoring", "whether anything is watching this bridge", 5);

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

    /**
     * Whether this finding exists on this kind of machine at all.
     *
     * <h2>⚠ THE LADDER WAS UNIVERSAL AND THAT WAS ALREADY WRONG, before anything was added to it</h2>
     *
     * {@code NodeReports.write} had no kind-gating whatever, so a port scan of a <b>bridge</b> — a
     * router — dutifully recorded a downloads folder, a high-risk vault count and a medium-vault
     * estimate. Nothing failed; the numbers were simply about a machine that has none of those things.
     * Per-kind applicability fixes that at the same time as it gives {@link #PEERS} and
     * {@link #MONITORED} somewhere to live.
     *
     * <h2>⚠ THE DENOMINATOR IS THE PART THAT WOULD HAVE BROKEN THE GAME QUIETLY</h2>
     *
     * {@code NodeReports.known} divides the findings established by the number of findings there
     * <em>are</em>, and that fraction feeds {@code Balance.breachProtocolShare} — which puzzle a
     * breach draws. Adding two rungs to a universal ladder would have capped <b>every ordinary machine
     * in the game</b> at 8/10, dropping breach-protocol odds for every target a player ever scans,
     * silently, with every screen still rendering correctly. Dividing by the <em>applicable</em> rungs
     * keeps a non-bridge at 8/8 — exactly what it was — and makes a bridge 5/5.
     *
     * <h2>What a bridge has, and what it does not</h2>
     *
     * A bridge is infrastructure you pass <em>through</em>: it has an identity, a firewall you have to
     * get past, a stack that fingerprints, peers on its far side, and possibly something watching. It
     * has no vault, no downloads folder, and no cycles worth measuring — {@code HostArchetypes
     * .acceptsDeployedWork} already refuses it work, so its capacity is not a number anybody can use.
     */
    public boolean appliesTo(HostKind kind) {
        if (kind != HostKind.BRIDGE) {
            // ⚠ Everything that is not a bridge keeps the calibrated eight and gains nothing. This is
            // what makes the change invisible to every existing machine, report and breach draw.
            return this != PEERS && this != MONITORED;
        }
        return switch (this) {
            case IDENTITY, FIREWALL, OS_VERSION, PEERS, MONITORED -> true;
            case CYCLE_CAPABILITY, CYCLE_LOAD, DOWNLOADS, VAULT_HIGH, VAULT_MEDIUM -> false;
        };
    }

    /** How many findings exist on this kind of machine — {@code known}'s denominator. */
    public static int countFor(HostKind kind) {
        int applicable = 0;
        for (PortScanTarget target : values()) {
            if (target.appliesTo(kind)) {
                applicable++;
            }
        }
        return applicable;
    }
}
