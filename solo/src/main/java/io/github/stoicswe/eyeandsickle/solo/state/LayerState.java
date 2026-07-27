package io.github.stoicswe.eyeandsickle.solo.state;

import java.util.ArrayList;
import java.util.List;

/**
 * One layer of a breach: its budget, its damage, and its board.
 *
 * <h2>Three classes, one class per instance, all three shapes in one file</h2>
 *
 * A layer is an instance of exactly one puzzle class ({@code docs/design/05-hacking-minigame.md}
 * §3.1), so at most one of the three field groups below is populated. That is a union hand-rolled
 * as a flat record rather than a sealed hierarchy, and the reason is the save format: this is a JSON
 * document that outlives the code which wrote it, and polymorphic deserialisation needs a type
 * discriminator that a hand-edited save can get wrong in a way an unused-fields layout cannot. The
 * unused groups serialise as empty lists and zeroes and cost nothing.
 *
 * <h2>⚠ Three fields are the answer and must never leave this object</h2>
 *
 * {@link #secret}, {@link #factDeck} and {@link #objectiveNodeId} are the puzzle. {@code
 * BreachSnapshots} does not read them at all — not "filters them", <em>does not read them</em> — so
 * that a hidden field cannot be leaked by a snapshot builder that forgot a branch. A unit test
 * asserts the secret appears nowhere in any snapshot; see {@code BreachSnapshotsTest}.
 *
 * <p>It would be easy to argue this does not matter in a save file the player can edit. It matters
 * because the same records go over a wire to a real home server, where the client is never
 * authoritative (Invariant I14), and because a puzzle whose answer is one careless field copy away
 * from the renderer is not a puzzle anyone can trust.
 */
public final class LayerState {

    public int index = 0;

    /** {@code PuzzleClass.name()} — one of {@code ENUMERATION}, {@code LOGIC}, {@code TRAVERSAL}. */
    public String puzzleClass = "ENUMERATION";

    /**
     * Attention granted to this layer, after defensive modifiers.
     *
     * <p>{@code docs/design/05-hacking-minigame.md} §4: the budget <em>is</em> the pressure, since
     * §4 removed the wall clock. §3.3's "time pressure" knob is now this number.
     */
    public int budget = 20;

    public int spent = 0;

    public int strikes = 0;

    /** §3.3's "error tolerance": how many wrong moves before the layer locks out. */
    public int strikeLimit = 3;

    /**
     * Probes and loud-tool volleys spent on this layer.
     *
     * <p>P-3's denominator, per layer, so the bot-versus-human gap can be measured on the step that
     * is supposed to produce it rather than averaged across an attempt.
     */
    public int probesUsed = 0;

    /** {@code LayerOutcome.name()} — {@code PENDING}, {@code ACTIVE}, {@code CLEARED}, {@code BYPASSED}, {@code LOCKED}. */
    public String state = "PENDING";

    // ------------------------------------------------------------------ Enumeration

    /**
     * The service banner: {@code EDGE RELAY}, {@code STORAGE ARRAY}, {@code AUTH BROKER} or
     * {@code MEDIA CACHE}. Never blank on an Enumeration layer.
     *
     * <p>This is Enumeration's human-read step ({@code docs/design/05-hacking-minigame.md} §3.2
     * requires each class to have one). The target's role <em>constrains which bands can hold open
     * ports</em>, and the constraint is printed nowhere — a player who reads the banner eliminates
     * whole bands without probing them, and a fixed heuristic cannot, which is §3.2(d) verbatim.
     * The four rules are stated in {@code docs/design/16-breach-implementation.md} §3 for the
     * designer and nowhere the player can read them.
     */
    public String banner = "";

    /** One line of role flavour that gestures at the rule without stating it. */
    public String bannerNote = "";

    public int slots = 0;

    public int bandSize = 4;

    public List<PortSlotState> ports = new ArrayList<>();

    /**
     * Sweep results so far, oldest first, as {@code {fromSlot, toSlot, openCount}}.
     *
     * <p>An {@code int[]} triple rather than a class because it is genuinely three numbers and the
     * array-of-arrays JSON it produces is stable and readable. A sweep returns the <em>count</em> of
     * open ports in a band and never which — that gap is what makes the cheap action worth its
     * price and the expensive one worth more.
     */
    public List<int[]> readingRanges = new ArrayList<>();

    /** Total open ports, when the Side-Channel Reader has established it. {@code -1} when unknown. */
    public int knownOpenTotal = -1;

    /** The set the player is currently composing. Bookkeeping: toggling it costs nothing. */
    public List<Integer> declared = new ArrayList<>();

    // ------------------------------------------------------------------ Logic

    /** ⚠ THE ANSWER. Never snapshotted, never logged, never put in a result string. */
    public List<String> secret = new ArrayList<>();

    public List<String> alphabet = new ArrayList<>();

    /**
     * Whether the code is salted.
     *
     * <p>{@code docs/design/06-intrusion-tools.md} §2 makes the Rainbow Table "hard-countered by
     * salting, by design" — a conditional power spike that is devastating against lazy targets and
     * useless against prepared ones. Public to the player, because the whole point is that recon
     * tells you whether the tool is worth bringing.
     */
    public boolean salted = false;

    public List<ProbeState> probes = new ArrayList<>();

    /** Facts the player has drawn. Each is true of {@link #secret} by construction. */
    public List<String> facts = new ArrayList<>();

    /** ⚠ Undrawn facts. Never snapshotted — the deck is the resource, not the discard. */
    public List<String> factDeck = new ArrayList<>();

    /** One entry per position; {@code ""} means not yet revealed. Written by the Rainbow Table. */
    public List<String> known = new ArrayList<>();

    /** The guess being composed. The player's own working, so it is public. */
    public List<String> draft = new ArrayList<>();

    /** Total candidates at generation: {@code alphabet^length}. Printed, as the class's opening number. */
    public int keyspace = 0;

    /**
     * Candidates still consistent with every response so far.
     *
     * <p>Recomputed when a probe lands and stored, rather than derived on every snapshot: a snapshot
     * is built on every UI refresh and this walks the keyspace, which is up to 100 000 candidates at
     * tier 5. Turn-based play makes the cached value exactly as fresh as the board.
     *
     * <p>{@code KEYSPACE 4096 -> 37} is the readout this feeds and it is the class's diegetic soul:
     * it is what tells a player that deduction is doing something guessing would not.
     */
    public int candidatesRemaining = 0;

    // ------------------------------------------------------------------ Traversal

    public int ranks = 0;

    public int objectiveRank = 0;

    public String currentNodeId = "";

    /** ⚠ THE ANSWER. Never snapshotted. The decoys are only decoys while this is unknown. */
    public String objectiveNodeId = "";

    public List<LatticeNodeState> nodes = new ArrayList<>();

    /**
     * The cross-reference the human read depends on ({@code docs/design/05-hacking-minigame.md}
     * §3.2).
     *
     * <p>Public from the start, and that is the design: the manifest names a service and a time, the
     * candidates' recovered logs each name a service and a time, and exactly one matches both. The
     * information is all on the table; the work is the reading.
     */
    public List<String> manifest = new ArrayList<>();

    public LayerState() {}
}
