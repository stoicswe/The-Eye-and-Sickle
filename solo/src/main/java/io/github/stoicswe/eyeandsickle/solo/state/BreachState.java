package io.github.stoicswe.eyeandsickle.solo.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A breach attempt in progress, or one that has resolved and not yet been dismissed.
 *
 * <h2>Why this needs no settlement path</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §4 decided the breach is <b>turn-based, with no wall
 * clock anywhere in it</b>. Nothing here has a deadline, so nothing here can finish while the game
 * is closed, so there is nothing for {@code SoloGame.resume()} or {@code settleTasks} to settle. A
 * breach survives a quit for free: it is a document, and reloading it puts the player back on the
 * same turn.
 *
 * <p>That is worth stating explicitly because it is the <em>opposite</em> of {@link TaskState},
 * which sits two files away and exists entirely to model work with a deadline. A future reader
 * looking for "where does the breach settle" should find this paragraph instead of a bug.
 *
 * <h2>The compute is held, not spent (D-5)</h2>
 *
 * {@link #allocationId} names one {@code ACTIVE_TOOL} reservation that is held for the whole attempt
 * and released into recovery at resolution — exactly the scan's hold-then-recover shape (UI-6,
 * {@code docs/design/04-mining.md} §3.2). Because it is a plain allocation and not a task, it needs
 * no clock either.
 *
 * <h2>Resolution lives here until dismissed</h2>
 *
 * The {@code resolved*} fields below are filled at resolution and this object is <em>not</em>
 * cleared — {@code dismiss} does that. So the outcome slate survives a reload, which matters
 * because the slate is where a loss becomes comprehensible ({@code 05} §1 constraint 4) and a
 * player who quits in disgust and comes back deserves to still be able to read why.
 */
public final class BreachState {

    public String breachId = UUID.randomUUID().toString();

    public String targetId = "";

    public String targetLabel = "";

    /** The Enumeration banner, when recon or a cleared layer has established it. */
    public String targetRole = "";

    public int difficultyTier = 1;

    /** {@code TargetState.name()}. Only {@code LIVE} can earn proof-of-skill credit ({@code 02} §2.4). */
    public String liveOrDormant = "DORMANT";

    /**
     * True when this is a crack against a foreign miner on the player's <em>own</em> rig.
     *
     * <p>Invariant I9 keys off this and nothing else: a crack generates <b>zero heat on every
     * outcome, including failure</b>. That is what makes the crack the safest possible introduction
     * to the breach and why {@code docs/design/04-mining.md} §5.1 calls it the tutorial vector — you
     * can lose it repeatedly and the only thing it costs is the buffer you were never holding.
     */
    public boolean minerCrack = false;

    /**
     * The target's defence profile, frozen at the moment the attempt opened —
     * {@code docs/design/05-hacking-minigame.md} §2, which instantiates a breach with "a node with a
     * defense profile ... drawn from {@code 09-defense-and-hardening.md}".
     *
     * <p>Copied onto the attempt rather than looked up through the target each turn, and the reason
     * is not convenience: the two defences work at different times. A Firewall is spent once, at
     * generation, as attention taken off every layer's budget ({@code 09} §1's "flat difficulty
     * increase"). A Tarpit is spent on <em>every action</em>, which means every turn for the rest of
     * the attempt — including after a save and reload, when the target may no longer be reachable
     * from the save at all. An attempt that could silently lose its Tarpit surcharge on the next
     * load would be a defence that stops working when the player quits, which is the worst possible
     * time for it to stop.
     */
    public int targetFirewallTier = 0;

    /** See {@link #targetFirewallTier}. Surcharges every action for the whole attempt. */
    public boolean targetTarpit = false;

    /** Whether the target carries canary tokens — they tag the handle on a failed attempt ({@code 09} §2). */
    public boolean targetCanaries = false;

    /** The held {@code ACTIVE_TOOL} allocation. Released into recovery at resolution. */
    public String allocationId = "";

    public long reservedCycles = 0L;

    /** Index into {@link #layers}. {@code -1} once resolved. */
    public int activeLayer = 0;

    /** Noise generated so far, summed per action ({@code docs/design/01-core-resources.md} §3). */
    /**
     * Which minigame this whole attempt is playing, drawn once at commission.
     *
     * <p>One roll per attempt rather than per layer — see {@code BoardFactory}. A target that opened
     * with a protocol grid and followed with a cipher would make the deeper layers of a hard target a
     * coin flip between the thing the player is good at and the thing they are not.
     */
    public String puzzleClass = "BREACH_PROTOCOL";

    public int noise = 0;

    /** Strikes across every layer. Feeds the noise total — alarms are loud. */
    public int alarms = 0;

    /** Ledger sequence counter, per breach rather than per layer. */
    public int sequence = 0;

    public List<LayerState> layers = new ArrayList<>();

    public List<AttentionEntryState> ledger = new ArrayList<>();

    /** {@code ""} while live; else {@code BreachOutcome.name()}. */
    public String outcome = "";

    public int resolvedNoise = 0;

    /** Always 0 when {@link #minerCrack} — Invariant I9, on every outcome. */
    public int resolvedHeat = 0;

    /**
     * What was taken, in minor units.
     *
     * <p>Only ever positive on a successful crack, and it is a <b>transfer, not a faucet</b>: the
     * buffer already existed on the host's machine, so no currency enters the economy ({@code
     * docs/design/03-economy.md} §5 rule 3, {@code 04} §5.1). An offensive breach yields items, never
     * ethecoin, for the same reason plus Invariants I1 and I2.
     */
    public long resolvedLootMinorUnits = 0L;

    public String resolvedLootLabel = "";

    /** Tier-gated partial progress toward a schematic ({@code 02} §2.2, {@code 10} §1a, I13). */
    public int resolvedSchematicMaterial = 0;

    /**
     * What this attempt cost, itemised in words.
     *
     * <p>⚠ A {@code FAILED} attempt must never leave this empty. A failure with no stated
     * consequence is exactly the "the game decided" reading {@code 05} §1 constraint 4 forbids.
     */
    public List<String> consequences = new ArrayList<>();

    public BreachState() {}
}
