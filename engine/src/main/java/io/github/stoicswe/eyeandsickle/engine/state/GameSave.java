package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The root of a single-player save.
 *
 * <p>This is a plain mutable tree on purpose. It is the <em>serialization</em> shape, not the domain
 * model the UI sees: the engine reads it, applies rules, and hands the client immutable {@code
 * protocol} value types. Keeping the two apart means the save format can gain a field without every
 * screen in the client learning about it, and it means Jackson never has to be taught how to
 * construct a {@link io.github.stoicswe.eyeandsickle.protocol.game.Cycles}.
 *
 * <h2>This file is player-controlled infrastructure, and that is the whole point</h2>
 *
 * A player can open this file in a text editor and give themselves a thousand ethecoin. That is not a
 * vulnerability to be closed — it is a single-player game, the only person affected is the person
 * doing it, and every anti-tamper measure available here is theatre against an attacker who owns the
 * machine.
 *
 * <p>What matters is that this can never become <em>someone else's</em> problem. {@link #federable}
 * is permanently {@code false} for a locally-created character, and the client refuses to submit a
 * save-derived item to any federated server. Invariant I14 is preserved not by pretending this file
 * is trustworthy but by ensuring nothing downstream ever trusts it.
 */
public final class GameSave {

    /**
     * Bumped whenever the shape changes incompatibly. A save from the future is refused rather than
     * silently half-read — see {@code SaveStore}.
     */
    public static final int CURRENT_FORMAT = 1;

    public int format = CURRENT_FORMAT;

    /** Stable id for this character. Not a DID: a solo character has no cryptographic identity. */
    public String characterId = UUID.randomUUID().toString();

    public String handle = "operator";
    public String faction = "NONE";

    /**
     * The operator's picture, as a base64 PNG. Empty means "none chosen".
     *
     * <h2>⚠ The IMAGE is stored, never a path to one</h2>
     *
     * {@code docs/client/00} §4.5 makes the profile directory the only host filesystem this client
     * touches, and §7 makes that a security boundary rather than a scope decision. Choosing a
     * picture reads one file the player explicitly picked in their own OS's dialog — once — and what
     * is kept is the <b>pixels</b>. Keeping the path instead would mean the game reading an
     * arbitrary host location on every launch, forever, which is exactly the boundary the rule
     * exists to hold.
     *
     * <p>It also means the picture travels with the character: a save copied to another machine
     * still has its face.
     */
    public String avatarPng = "";

    /**
     * Always false for a save created locally, and the client must never offer to change it.
     *
     * <p>Kept as an explicit field rather than an implicit rule so that the refusal is greppable and
     * so a future migration path (a real DID-binding step, per {@code docs/architecture/02} §4) has
     * somewhere honest to write its answer.
     */
    public boolean federable = false;

    public Instant createdAt = Instant.now();
    public Instant lastPlayedAt = Instant.now();

    /** Total seconds of wall-clock play. Drives nothing mechanical; shown in {@code identity}. */
    public long playedSeconds = 0L;

    public RigState rig = new RigState();

    /**
     * The chain the rig mines against.
     *
     * <p>Nullable so a save written before 2026-07-27 loads; {@code GameEngine.backfill} builds one on
     * the way in. A character who predates the chain joins it at its current height rather than at
     * block zero, which is the same thing that happens to anyone who installs a wallet today.
     */
    public ChainState chain;
    public BigInteger ethecoinWei = BigInteger.ZERO;

    /** Long-horizon Eye attention. Distinct from noise, which decays and is not persisted. */
    public int personalHeat = 0;

    /**
     * ⚠ <b>A THIRD REPUTATION, and it must never share a field with the other two.</b>
     *
     * <p>Whether this trader delivers what they were paid for — see {@code rules/SecondaryMarket}.
     * {@link #factionReputationEye} / {@link #factionReputationSickle} are standing with a faction,
     * and {@code validatorReputation} (server-side) is federation trust weighting. All three are
     * independent on purpose: a Sickle hero can be a thief, and a scrupulous trader can be a
     * validator nobody trusts. {@code CLAUDE.md} and the glossary already forbid conflating the
     * first two; this is the third and the same rule applies.
     */
    public int traderReputation = 0;

    /** Sales delivered. Counted separately from reputation, which is a judgement rather than a tally. */
    public int traderDeliveries = 0;

    /** Sales taken and not delivered — caught or not. Drives the rising detection chance. */
    public int traderDefections = 0;

    public int factionReputationEye = 0;
    public int factionReputationSickle = 0;

    public List<ItemState> items = new ArrayList<>();
    public List<LedgerEntryState> ledger = new ArrayList<>();
    public List<NodeState> knownNodes = new ArrayList<>();

    /**
     * The intelligence file on each machine a scan has come back from.
     *
     * <p>Separate from {@link #knownNodes} because they answer different questions and are written by
     * different things: a node is "this machine exists and here is where it sits", established by a
     * sweep; a report is "here is what we have learned about it and when", established by a scan.
     * Folding the second into the first would put seven nullable findings and a timestamp map on every
     * row of a list that is mostly machines nobody has looked at.
     */
    public List<NodeReportState> nodeReports = new ArrayList<>();

    /**
     * The player's own filing of what they have discovered — see {@link FolderState}.
     *
     * <p>Sits beside {@link #knownNodes} rather than inside {@link #topology} on purpose. A folder is
     * not part of the world; it is an annotation <em>over</em> the world, it survives independently of
     * whether a topology has been generated yet, and putting it under a field that is null on an old
     * save would make the whole feature inaccessible for exactly the characters most likely to have
     * a long list of machines to file.
     */
    public List<FolderState> netFolders = new ArrayList<>();

    /**
     * Open shell sessions, one per machine the player is sitting on.
     *
     * <p>⚠ A session is <b>not</b> the vantage. The vantage is singular and is what a sweep measures
     * hop distance from ({@code docs/design/07} §2, Invariant <b>I2</b>); this is a list, because
     * having a shell open on a machine you already hold costs compute and buys no reach. Merging the
     * two would multiply reach by the number of windows a player had open.
     */
    public List<SessionState> sessions = new ArrayList<>();

    /**
     * Every remote access to this rig — {@code /var/log/remote-access.log}.
     *
     * <p>⚠ Written only by <b>remote actors</b>, so in single player it stays empty for the life of
     * the character. That is correct rather than unfinished: the log exists and is readable from the
     * first minute precisely so a player learns to look at it before they have a reason to. See
     * {@code rules/AccessLog}, including why an intruder blanks an address rather than deleting a
     * line.
     */
    public List<AccessEntry> remoteAccessLog = new ArrayList<>();

    /**
     * What the operator has looked at lately — {@code ~/.local/share/recently-used}.
     *
     * <p>⚠ A {@link java.util.LinkedList} because {@code Recents} pushes to the front and trims from
     * the back on every access, and doing that to an ArrayList copies the whole thing each time. It
     * is capped at thirty, so this is a correctness-of-shape point rather than a performance one:
     * the type says which end is which.
     */
    public java.util.LinkedList<RecentEntry> recents = new java.util.LinkedList<>();

    /**
     * Files that have actually been downloaded, wherever the player put them.
     *
     * <p>The one part of the filesystem that is stored rather than generated — see
     * {@link StoredFileState}. Kept small by construction: only four kinds of thing transfer.
     */
    public List<StoredFileState> files = new ArrayList<>();

    public List<DefenseState> defenses = new ArrayList<>();

    /**
     * Work with a wall-clock duration that is currently running.
     *
     * <p>Persisted, so a six-minute Thorough Scan survives quitting — see {@link TaskState}. A task
     * whose end has passed while the game was closed completes on the first tick after load, which
     * is the same catch-up path deployed-miner buffers already take.
     */
    public List<TaskState> tasks = new ArrayList<>();

    /**
     * Bought and waiting to come down the wire, in the order they will arrive.
     *
     * <h2>⚠ ORDER IS THE MODEL. There is no "running" flag anywhere.</h2>
     *
     * The active download is the first entry that is not paused; everything after it is held. That
     * makes reordering and pausing the same operation seen twice, and it means the list cannot ever
     * describe a state the rules disagree with — a stored {@code running} boolean would be a second
     * answer to a question the list's own order already settles, and the two part company the first
     * time something is moved.
     *
     * <p>⚠ Persisted, because the money already moved. A queue that lived in the client would lose
     * paid-for downloads when the window closed, which is indistinguishable from being robbed.
     */
    public List<DownloadOrderState> downloadQueue = new ArrayList<>();

    /**
     * The player's own resting orders on the Shadow Market.
     *
     * <p>⚠ The ONLY part of that market that is stored. Prices, the book, the tape and the candles
     * are pure functions of (character, item, clock) — see {@code rules/ShadowMarket} — so storing
     * any of them would be a cache of a derived thing that eventually disagrees with it, on a screen
     * whose entire subject is what a price is. What cannot be recomputed is what the player
     * committed: a buy holds ethecoin in escrow and a sell holds a specific item by id.
     */
    public List<ShadowOrderState> shadowOrders = new ArrayList<>();

    /**
     * Listings this character has up for sale on the Shadow Market.
     *
     * <p>⚠ An {@code ATTACHED} listing HOLDS the items — they are removed from {@link #items} when it
     * is created and returned only on cancel or handed over on sale. That is what makes the two
     * delivery modes different in mechanism rather than in promise.
     */
    /**
     * What this character holds on AnonShare.
     *
     * <p>⚠ Nullable-safe by initialisation, like every other collection here. Nothing about the
     * <em>prices</em> is stored — those come from a feed and are nobody's state — only what was
     * bought, for how much, and how the player has filed it.
     */
    public BrokerageState brokerage = new BrokerageState();

    /**
     * The standing instruction to audit this rig on a timer.
     *
     * <p>⚠ Nullable rather than initialised, because {@code ScanSchedule} treats a missing schedule
     * and a disabled one identically and every accessor guards for it — a save written before this
     * existed loads with no schedule and nothing has to migrate.
     */
    public ScanScheduleState scanSchedule = new ScanScheduleState();

    public List<ShadowListingState> shadowListings = new ArrayList<>();

    /**
     * Trades where somebody still owes somebody something.
     *
     * <p>⚠ The market has <b>no escrow</b>, so this list is the entire enforcement mechanism: the
     * money has already moved and all that remains is an obligation, a deadline and the reputation
     * cost of missing it.
     */
    public List<ShadowObligationState> shadowObligations = new ArrayList<>();

    /**
     * What this character has taken off the market's shelf, keyed {@code <offeringId>@<day>}.
     *
     * <p>⚠ The COUNT TAKEN, never the stock level. How much the shop stocked is derived from the item
     * and the day ({@code rules/MarketStock}); storing the remaining level instead would be a second
     * copy of a derived number, and the two would disagree the first time the ration was re-tuned.
     *
     * <p>⚠ Solo only. On a server this is the server's own table — the shelf is shared, and a
     * per-character count would give every player a private one. See {@code MarketStock.Held}.
     *
     * <p>⚠ Keyed by DAY, so yesterday's entries are simply never read again. They are pruned on load
     * rather than accumulating: a save played daily for a year would otherwise carry a few thousand
     * dead keys, which is not a size problem but is a file nobody can read.
     */
    public Map<String, Integer> marketTaken = new LinkedHashMap<>();

    /**
     * Completed audits, oldest first, capped at {@link ScanReportState#LIMIT}.
     *
     * <p>⚠ Trimmed from the FRONT when it overflows, so the hundred kept are the hundred most
     * recent. Dropping the newest instead would leave the list frozen at whatever the player did
     * first, which is the opposite of what a history is for.
     */
    public List<ScanReportState> scanReports = new ArrayList<>();

    public List<String> schematics = new ArrayList<>();

    /** Terminal history, so `history` and Ctrl-R survive a restart the way a real shell's does. */
    public List<String> commandHistory = new ArrayList<>();

    /**
     * The rig's log, newest last.
     *
     * <p>Persisted, so `log` after a restart shows what happened before it — which is what a real
     * journal does and what makes the log usable for the thing it exists for: working out what
     * happened while you were not watching.
     *
     * <p>Capped at {@link #LOG_CAPACITY}. An uncapped log in a save file that is rewritten every
     * thirty seconds is an unbounded write amplification bug waiting for a long session.
     */
    public List<RigEvent> log = new ArrayList<>();

    /** Roughly a long session's worth. Old entries are dropped from the front. */
    public static final int LOG_CAPACITY = 500;

    // ------------------------------------------------------------------ the breach (design/05)

    /**
     * Seeded, persisted PRNG state — see {@link io.github.stoicswe.eyeandsickle.engine.breach.Rng} and
     * {@code docs/design/16-breach-implementation.md} §2.
     *
     * <p>Persisted because a draw that is not persisted is a draw the player can reroll by
     * reloading, and both things this engine draws — a breach board and a scan's false positive —
     * would become advisory if they were rerollable. The default is splitmix64's own golden-ratio
     * constant so that a save written before this field existed still has a usable, non-degenerate
     * seed rather than zero.
     *
     * <p>{@code GameEngine.newCharacter} overwrites it with {@code Rng.derive(characterId, now)}.
     */
    public long rngSeed = 0x9E3779B97F4A7C15L;

    /**
     * The breach in progress, or null.
     *
     * <p>Turn-based, so it needs no settlement — {@code docs/design/05-hacking-minigame.md} §4
     * removed the wall clock from the breach entirely. Nothing here has a deadline, so nothing here
     * can complete while the game is closed, so {@code resume()} and {@code tick()} have no work to
     * do on it. Contrast {@link #tasks}, two fields up, which exists for exactly the opposite case.
     */
    public BreachState activeBreach;

    /**
     * One row per breach attempt, oldest first — the persisted {@code resolutionRecord} from
     * {@code docs/design/05-hacking-minigame.md} §2.
     *
     * <p>⚠ <b>Never counted.</b> Both readers ask for the highest tier solved against a live target
     * — proof-of-skill ({@code 02} §2.4, Invariant I7) and the salvage guard ({@code 10} §1a,
     * Invariant I13). A count over this list rewards farming the softest target available, which is
     * the exact failure the gate rule exists to prevent; {@code ResolutionRecord}'s javadoc calls
     * reaching for one "the exploit arriving".
     */
    public List<ResolutionState> resolutions = new ArrayList<>();

    // ------------------------------------------------------------------ the network (design/17)

    /**
     * The generated world: virtual servers, their machines, and the links between them.
     *
     * <p>Written once by {@code TopologyGenerator.generate} and never regenerated — {@code NetRules}
     * treats a non-null value as final, and {@code generate} returns immediately when it finds one.
     *
     * <p>⚠ <b>A save written before this field existed is backfilled on load, in
     * {@code GameEngine.open}, and that is not the same thing as regenerating.</b> This javadoc used to
     * say the opposite — that an old character "keeps working with an empty map" — and the sentence
     * was wrong in the only way that matters: a null topology is not a small world, it is <em>no</em>
     * world, so {@code NetRules.view} returns {@link
     * io.github.stoicswe.eyeandsickle.protocol.game.NetMap#empty()} and {@code beginSweep} refuses
     * every sweep at every tier, forever, with no wording that could tell the player why. That is not
     * a character that keeps working; it is one whose entire network half is permanently dead.
     * Backfilling costs nothing (the world is rolled from the save's own persisted seed) and is the
     * only reading under which the pre-topology character can ever reach the feature.
     *
     * <p>⚠ {@link #CURRENT_FORMAT} is deliberately <b>not</b> bumped for this. {@code SaveStore}
     * refuses only saves whose format is <em>greater</em> than the build's, and Jackson leaves a
     * missing field at its initialiser — so a bump would refuse nothing and protect nothing, while
     * costing every existing save a compatibility scare.
     *
     * <p>⚠ It is by far the largest thing in this file — up to 350 hosts, rewritten on every
     * autosave. See {@link TopologyState}'s note on why nothing derived is cached inside it.
     */
    /**
     * Cycles of transient noise, and when they stop counting.
     *
     * <p>A short burst the rig radiates after an event that was conspicuous but is over — currently
     * only abandoning a breach. ⚠ Held as an <b>instant</b> rather than a countdown so it settles
     * correctly across a quit: a remaining-seconds field would pause with the game and leave a spike
     * waiting to be served the next time the player opened the client.
     */
    public long noiseSpikeCycles = 0L;

    public Instant noiseSpikeUntil = Instant.EPOCH;

    public TopologyState topology;

    /**
     * Generic schematic contribution material — {@code docs/design/02-unlock-gates.md} §2.2 and
     * {@code docs/design/10-botnets.md} §1a.
     *
     * <p>Partial progress toward schematic unlocks, gated on engagement tier (Invariant I13) so it
     * sets pace and never reach. See {@link io.github.stoicswe.eyeandsickle.engine.rules.SalvageRules}.
     */
    public int schematicMaterial = 0;
}
