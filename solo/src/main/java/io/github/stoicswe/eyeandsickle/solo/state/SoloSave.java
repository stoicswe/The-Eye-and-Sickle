package io.github.stoicswe.eyeandsickle.solo.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
public final class SoloSave {

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
     * <p>Nullable so a save written before 2026-07-27 loads; {@code SoloGame.backfill} builds one on
     * the way in. A character who predates the chain joins it at its current height rather than at
     * block zero, which is the same thing that happens to anyone who installs a wallet today.
     */
    public ChainState chain;
    /**
     * ⚠ {@code @JsonAlias} carries the PRE-WEI key, and a save is lost without it.
     *
     * <p>The field was {@code ethecoinMinorUnits} when an ethecoin was 100 minor units. Jackson has
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} off — deliberately, so a save from a newer build still opens
     * — which means a key it does not recognise is <b>silently dropped</b>. Renaming the field without
     * this alias therefore does not fail: it loads the save, leaves every amount at its initialiser,
     * and hands the player a balance of zero with nothing anywhere saying why.
     *
     * <p>Measured, not theorised. A real pre-migration save loaded as {@code 0 EC} across the board,
     * and the only reason it was noticed at all is that one field had no initialiser and threw.
     */
    @com.fasterxml.jackson.annotation.JsonAlias("ethecoinMinorUnits")
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
     * Which representation this save's money is written in.
     *
     * <h2>⚠ Absent means the OLD one, and that is the whole mechanism</h2>
     *
     * Jackson leaves a missing field at its declared default, so every save written before this field
     * existed loads as {@code 0} — which is exactly the set of saves whose amounts are in hundredths
     * of an ethecoin. {@link #MONEY_SCHEMA} is the current value, and {@code SoloGame.rescaleMoney}
     * runs once for anything below it.
     *
     * <p>A version number rather than a heuristic: "is this balance suspiciously small?" is not
     * answerable — a player can legitimately have 8 wei — and a migration that guesses is a migration
     * that eventually multiplies somebody's balance twice.
     */
    public int moneySchema = 0;

    /** The current money representation: wei, 18 decimals. See {@link #moneySchema}. */
    public static final int MONEY_SCHEMA = 1;

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
     * Seeded, persisted PRNG state — see {@link io.github.stoicswe.eyeandsickle.solo.breach.Rng} and
     * {@code docs/design/16-breach-implementation.md} §2.
     *
     * <p>Persisted because a draw that is not persisted is a draw the player can reroll by
     * reloading, and both things this engine draws — a breach board and a scan's false positive —
     * would become advisory if they were rerollable. The default is splitmix64's own golden-ratio
     * constant so that a save written before this field existed still has a usable, non-degenerate
     * seed rather than zero.
     *
     * <p>{@code SoloGame.newCharacter} overwrites it with {@code Rng.derive(characterId, now)}.
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
     * {@code SoloGame.open}, and that is not the same thing as regenerating.</b> This javadoc used to
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
     * sets pace and never reach. See {@link io.github.stoicswe.eyeandsickle.solo.rules.SalvageRules}.
     */
    public int schematicMaterial = 0;
}
