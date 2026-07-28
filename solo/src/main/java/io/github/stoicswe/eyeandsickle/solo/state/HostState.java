package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Generated ground truth for one machine on the network.
 *
 * <h2>⚠ This is NOT the player's knowledge — {@link NodeState} is</h2>
 *
 * The two classes describe the same machine from opposite sides, and keeping them apart is the whole
 * discovery model. This one is written once, at world generation, and is complete: it knows the
 * host's type, its defences, whether it is a honeypot, and the fixed roll that decides whether a
 * given sweep can see it. {@link NodeState} is written by <em>recon</em>, holds only what the player
 * has paid to learn, and is the only list {@code ls /net/}, tab completion, {@code Targets.available}
 * and the network map are built from — {@code NodeState}'s own class javadoc calls that rule
 * load-bearing rather than tidy, and three subsystems depend on it.
 *
 * <p>So a host that has never been detected exists here and nowhere else, and there is deliberately
 * no aggregate anywhere that counts undiscovered hosts by type, tier or value. The single number a
 * sweep may report about what it did <em>not</em> find is how many machines were inside the hop
 * ceiling — the instrument's own sensitivity, which carries no address, type, tier or value.
 *
 * <h2>Two fields that must never be copied across</h2>
 *
 * {@link #defended} and {@link #honeypot} are truth. Their counterparts on {@link NodeState} —
 * {@code trafficAnalyzed} and {@code honeypotSuspected} — are <em>products</em>: the Traffic
 * Analyzer's and the Honeypot Detector's respectively ({@code docs/design/07-recon-tools.md} §1,
 * §2). A generator that set both sides would hand out proof-of-skill credit for free, because
 * {@code Targets.available} reports {@code LIVE} exactly when {@code trafficAnalyzed && defended}
 * and Invariant I7 requires a live or defended target for credit. Nothing in
 * {@code io.github.stoicswe.eyeandsickle.solo.net} writes either counterpart, and a test asserts it.
 *
 * <h2>Strings rather than enums, for the same reason every other {@code state} class uses them</h2>
 *
 * This is a JSON document that outlives the code that wrote it. An unknown enum constant is a hard
 * deserialisation failure — a save the player cannot open — rather than a field the engine can
 * defend against. {@link #kind} holds a {@code HostKind.name()} and {@link #signal} a
 * {@code SignalStrength.name()}; both are translated at the {@code protocol} boundary and clamped
 * on the way out.
 */
public final class HostState {

    /**
     * The join key between ground truth and player knowledge.
     *
     * <p>{@code 10.<server>.<index/254>.<2 + index%254>} — see
     * {@code io.github.stoicswe.eyeandsickle.solo.net.TopologyGenerator}. Unique across the whole
     * topology, which is what lets {@link NodeState#address} be a foreign key without a second id.
     */
    public String address = "";

    /**
     * A neutral machine name: {@code <server name>-<index>}.
     *
     * <p>⚠ It deliberately does <b>not</b> encode {@link #kind}. A label like {@code terminal-07}
     * would name the host's type at the moment a sweep discovered it, and naming types is what the
     * 15 EC Passive Sniffer sells ({@code docs/design/07-recon-tools.md} §1). Deleting a purchased
     * tool's product at the point of rendering is {@code docs/design/02-unlock-gates.md} §5's
     * pricing check failing.
     */
    public String label = "";

    /** Which {@link ServerState} this sits on. */
    public String serverId = "";

    /** {@code HostKind.name()}. Ground truth; the player sees {@code UNKNOWN} until a tool says otherwise. */
    public String kind = "TERMINAL";

    /**
     * Every host this one is linked to, by address. Symmetric — the generator writes both sides.
     *
     * <p>Undiscovered hosts still conduct: hop distance is BFS over <em>this</em> graph, not over
     * the discovered subgraph, so a machine the player has never seen can still be the reason a
     * further one is two hops away rather than three.
     */
    public List<String> links = new ArrayList<>();

    /**
     * The address on the other server this bridge advertises, or {@code ""}.
     *
     * <p>⚠ One host can be picked as the bridge endpoint for more than one server edge — the draw
     * that picks it is unconditional (the RNG contract forbids a rejection loop), so collisions are
     * rare but real. Both links are written and both remain traversable; only the <em>advertised</em>
     * peer is the last one assigned. Nothing reads this field for routing, so the consequence is
     * cosmetic: the map names one of the two networks on the far side rather than both.
     */
    public String bridgePeer = "";

    /** Breach difficulty, on the shared 1–5 scale. Generated inside range; consumers clamp anyway. */
    public int tier = 1;

    /**
     * {@code SignalStrength.name()} — {@code docs/design/04-mining.md} §2.1's established vocabulary,
     * generalised from miners to hosts.
     *
     * <p>Derived at generation from {@link #kind} rather than drawn, and deliberately not a second
     * {@code noise} field: noise in this game is a <em>player-attribution</em> scalar
     * ({@code docs/design/01-core-resources.md} §3.2, "noise is what reaches other machines"), and
     * giving a node one would quietly redefine the term for everything else that reads it.
     *
     * <p>A host that is currently carrying a deployed miner reads one level louder, which is §2.1's
     * own rule — a bigger, more valuable miner is louder — generalised to the machine under it. That
     * step-up is applied at read time, not stored here, because miners come and go.
     */
    public String signal = "LOW";

    /** 0–3. ⚠ {@code BreachTarget}'s compact constructor throws above 3; never generate a 4. */
    public int firewallTier = 0;

    /** Surcharges every intruder action rather than cutting the budget ({@code docs/design/09} §1). */
    public boolean tarpit = false;

    /** Alerts the owner and tags the toucher's handle — the evidence path in {@code docs/design/12}. */
    public boolean canaries = false;

    /**
     * Whether this machine is actually live and defended.
     *
     * <p>⚠ Ground truth, and never copied to {@link NodeState#trafficAnalyzed}. See the class note.
     */
    public boolean defended = false;

    /**
     * Whether this machine is actually an Eye trap.
     *
     * <p>⚠ Ground truth, and never copied to {@link NodeState#honeypotSuspected}. That flag is the
     * Honeypot Detector's product, and {@code docs/design/07-recon-tools.md} §2 requires the detector
     * to have a false-negative rate — "a perfect detector removes the fear the traps exist to
     * create". A generator that set the suspicion directly would be a perfect detector wearing the
     * generator's clothes.
     */
    public boolean honeypot = false;

    /**
     * The fixed roll a sweep's detection threshold is compared against.
     *
     * <p>⚠ <b>Drawn once, at world generation, and never re-drawn.</b> This is the whole defence
     * against save-scumming discovery, and it is a defence by construction rather than by cooldown:
     * a sweep makes no detection draw at all, it only compares this stored number against a threshold
     * set by the sweep tier, the host's signal and the hop distance. Re-running the same sweep from
     * the same vantage therefore returns a bit-identical candidate set, forever. Quitting without
     * saving changes nothing, because the roll predates the sweep by the whole game.
     *
     * <p>The only two things that move the answer both cost something: a higher sweep tier (ethecoin,
     * plus its own compute, duration and noise) or a closer vantage (a breach, a foothold and a
     * {@code connect}). That is the mechanic, and {@code Rng}'s own javadoc explains why an
     * alternative that re-rolled would make discovery advisory.
     *
     * <p>Defaults to 1.0 — outside every threshold — so a hand-edited or truncated save produces an
     * undiscoverable host rather than a free one.
     */
    public double detectRoll = 1.0;

    /**
     * A one-time payout on a successful breach, in minor units.
     *
     * <p>⚠ <b>A stock, not a flow, and that distinction is the whole economic argument.</b>
     * {@code docs/design/03-economy.md} §5 rule 1 caps any new faucet at 70 EC/hr effective, and
     * §5 rule 3 separates transfers from faucets. This is neither a faucet nor exactly a transfer:
     * it is a finite quantity of currency placed in the world at generation, each unit collectable
     * exactly once ({@link #looted}), which cannot produce a rate at all because nothing about it
     * repeats. The home server's entire pool is ~68 EC — the Passive Sniffer and the T2 sweep with
     * change — and then it is gone.
     *
     * <p>⚠ This does read against {@code BreachRules.resolveOffensive}'s standing note that breach
     * loot is "an item, never ethecoin". Both survive: the breach engine still mints a data cache and
     * no currency, and the currency here is picked up off the <em>host</em> by
     * {@code NetRules.reconcileFootholds} rather than created by the attempt. Flagged for the
     * integrator to log in {@code docs/design/15-open-questions.md} §3.
     */
    public long lootMinorUnits = 0L;

    /** Whether the payout has been taken. Checked before crediting, so a host pays exactly once. */
    public boolean looted = false;

    /**
     * The story fragment this host carries, or {@code ""}.
     *
     * <p>An id only — the prose is a client resource. Rules never carry prose, and a document body
     * in the save would be duplicated into every player's disk copy of a file the client already
     * ships. Home carries none at all, which is decision N-4 made structural: the flavour layer
     * starts one bridge out, so nothing on the early critical path depends on it.
     */
    public String documentId = "";

    public boolean documentTaken = false;

    /**
     * When the document was pulled, or null.
     *
     * <p>⚠ Not in the original field list, and added for one reason: {@code NetDocument} carries a
     * {@code recoveredAt}, and {@code TopologyState.documents} stores ids in order without times.
     * Reconstructing the time from the id would be a guess, and reconstructing the <em>source</em>
     * from the id is ambiguous the moment two hosts draw the same fragment — which they will, since
     * twelve ids are spread across up to 350 hosts. Ordering by this field and falling back to the
     * address is the only reading that is correct for duplicates.
     */
    public Instant documentTakenAt;

    /** Whether a sweep has ever detected this host. Gates its existence in {@link NodeState}. */
    public boolean discovered = false;

    /** Whether a type-revealing tool has run here — the Passive Sniffer's product, not a sweep's. */
    public boolean identified = false;

    /** Whether the player holds a foothold here, and may therefore {@code connect} to it. */
    public boolean foothold = false;

    /**
     * Breached once, and shut out since.
     *
     * <p>⚠ Nothing sets this true yet — no rule patches a host. It is persisted and rendered so the
     * state has one meaning the day a patch mechanic lands; see {@code docs/design/15} for the
     * proposal. A save written today will always read false, which is correct rather than missing.
     */
    public boolean patched = false;
}
