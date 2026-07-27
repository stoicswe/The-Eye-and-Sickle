package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.view.NetText;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The network verbs — the terminal half of the map window.
 *
 * <h2>Pillar C1, and the two halves of it</h2>
 *
 * {@code docs/client/00-client-overview.md} §2 requires everything a tool window can do to be
 * reachable from the terminal and the reverse. The map window has three intents and two reads, and
 * all five are here: {@code sweep} discovers, {@code connect} repositions, {@code download}
 * recovers, {@code net} lists what is known, and {@code net --docs} reads what has been recovered.
 *
 * <p>The second half of C1 is subtler and is why {@link NetText} exists: the two surfaces have to
 * keep <em>agreeing</em>. The columns printed here are the columns drawn in the window, at the same
 * widths in the same order, because both call the same renderer. A player who learns to read one has
 * learned to read the other, and neither can drift when somebody edits a column.
 *
 * <h2>One of these is a source, and that is a real distinction</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.7 divides the catalogue into sources,
 * filters and actions, and {@link Shell} refuses a pipeline containing an action before running any
 * stage of it. {@code net} changes nothing, so it may head a pipeline — {@code net | grep bridge} is
 * the intended use, and it is why a bridge row carries the literal lowercase word {@code bridge} in
 * its note as well as the uppercase {@code BRIDGE} in its kind column. {@code grep} here is
 * case-sensitive by default, so one spelling alone would break the pipeline a player actually types.
 * The three that change something never appear after a {@code |}.
 *
 * <h2>`sweep` is not `scan`, and the distinction is worth teaching</h2>
 *
 * {@code scan} audits the player's <em>own</em> rig for things hiding from routine listings
 * ({@code docs/design/04-mining.md} §3.2). {@code sweep} probes a network the player does not own.
 * Two activities, two words, and every help text below says so, because a player who conflates them
 * will eventually run the loud one at the wrong moment.
 *
 * <h2>Costs are printed, verdicts are not</h2>
 *
 * {@code sweep -n} prints the tool's published cost, its duration and the hop ceiling now in force,
 * and stops. It does not say "affordable", does not subtract, and does not predict what would be
 * found — the first two because gate evaluation belongs to the rules ({@code docs/client/04} §3.4,
 * Invariant <b>I14</b>), and the third because a sweep's outcome is decided by a roll made at world
 * generation and stored, so predicting it here would be handing over the answer for free.
 */
public final class NetCommands {

    private NetCommands() {}

    /**
     * The three sensitivities, and their published figures.
     *
     * <p>⚠ These numbers are duplicated from the rules, exactly as {@code scan -n} duplicates
     * {@code 5 / 15 / 35}. That is a real cost and it is taken deliberately: the port publishes no
     * per-tool cost read, and a dry run that could not say what a thing costs would be a dry run
     * worth nothing. They are the published catalogue figures from the sweep ladder, they are not
     * used to decide anything, and the rules refuse or accept on their own numbers regardless of
     * what is printed here.
     *
     * <p>If the port ever grows a published-cost read — the shape {@code BreachTarget.computeCost}
     * already has for the breach — delete this table and read it instead. Until then, changing the
     * sweep ladder's cost or duration means changing this too.
     */
    private record Sweep(String flag, String itemId, String label, long cycles, long seconds) {}

    private static final List<Sweep> SWEEPS = List.of(
            new Sweep("", "net-sweep", "sweep", 2, 20),
            new Sweep("--wide", "net-sweep-wide", "sweep --wide", 5, 45),
            new Sweep("--deep", "net-sweep-deep", "sweep --deep", 9, 90));

    /**
     * Registers the four verbs.
     *
     * <p>Separate from {@link BuiltinCommands} for the same reason {@link BreachCommands} is:
     * {@code SimpleCommand} there is private, and these verbs need a real {@code -h} body. They are
     * game-facing commands in every other respect and belong in the catalogue a player sees.
     * {@link BuiltinCommands#registry()} calls this.
     */
    public static void register(Shell.CommandRegistry registry) {

        // ---------------------------------------------------------------- the read
        registry.add(new Verb(
                "net",
                List.of(),
                "Every machine you have discovered, and how far away each one is.",
                false,
                List.of(
                        "net                        one row per discovered machine",
                        "net -v                     adds SIGNAL and DEPTH, and the server strip",
                        "net --docs                 the story fragments recovered so far",
                        "net --docs <id>            read one of them",
                        "",
                        "A source, so it may head a pipeline:  net | grep bridge",
                        "",
                        "KIND prints -------- until a type-revealing tool has run. That is not the same",
                        "as 'ordinary machine': a sweep sells existence and adjacency, and naming what a",
                        "machine IS is what the Passive Sniffer sells. HOPS is measured from your",
                        "vantage, not from your rig — see connect(1)."),
                inv -> {
                    if (inv.stage().hasFlag("docs")) {
                        return documents(inv.session(), inv.stage().flag("docs").orElse(""));
                    }
                    boolean verbose = inv.stage().isVerbose();
                    NetMap map = inv.session().net();
                    List<String> rows = NetText.rows(map, verbose);

                    List<String> out = new ArrayList<>();
                    if (verbose) {
                        // The window keeps this line permanently on screen. In the terminal it is
                        // behind -v so the plain form stays a clean table that a pipeline can eat.
                        out.add(NetText.serverStrip(map));
                    }
                    if (rows.isEmpty()) {
                        out.add(NetText.EMPTY);
                        return Command.Output.ok(out);
                    }
                    out.add(NetText.header(verbose));
                    out.addAll(rows);
                    return Command.Output.ok(out);
                }));

        // ---------------------------------------------------------------- the intents
        registry.add(new Verb(
                "sweep",
                List.of(),
                "Probe the network around your vantage for machines you have not seen.",
                true,
                List.of(
                        "sweep                      the base sweep; in the starting kit",
                        "sweep --wide               a wider sweep of the same distance",
                        "sweep --deep               the widest sweep of the same distance",
                        "sweep -n                   print what it would cost, and take nothing",
                        "",
                        "This is not scan(1). `scan` audits YOUR OWN rig for things hiding from routine",
                        "listings; `sweep` probes machines you do not own, which is why it is the one",
                        "that can be answered.",
                        "",
                        "Sensitivity is what the tiers buy. REACH IS NOT FOR SALE: the hop ceiling moves",
                        "only for a schematic, and no amount of ethecoin changes it. What does change is",
                        "how quiet a machine can be and still be heard. If a sweep finds nothing new,",
                        "running the same one again will find nothing new again — a louder instrument or",
                        "a closer position is what moves it. See connect(1)."),
                inv -> {
                    boolean wide = inv.stage().hasFlag("wide");
                    boolean deep = inv.stage().hasFlag("deep");
                    if (wide && deep) {
                        return Command.Output.usage(
                                "sweep: --wide and --deep are two different instruments; pick one");
                    }
                    String flag = deep ? "--deep" : wide ? "--wide" : "";
                    if (inv.stage().isDryRun()) {
                        return Command.Output.ok(dryRun(inv.session(), flag));
                    }
                    return Command.Output.of(inv.session().sweep(flag));
                }));

        registry.add(new Verb(
                "connect",
                List.of(),
                "Move your vantage to a machine you hold a foothold on.",
                true,
                List.of(
                        "connect <address>          operate from there instead",
                        "connect -n <address>       say what that would mean, and move nothing",
                        "",
                        "The hop ceiling is measured from your VANTAGE, not from your rig. That is what",
                        "makes a one-hop ceiling survivable across a whole world: you do not buy your way",
                        "further out, you move further out. Breach a machine, take the foothold, connect",
                        "to it, and sweep again — everything one hop from THERE is now in reach.",
                        "",
                        "A foothold is what a successful breach leaves behind. Without one this refuses,",
                        "and the refusal names what is missing."),
                inv -> {
                    String address = address(inv);
                    if (address.isBlank()) {
                        return Command.Output.usage(
                                "connect <address> — run `net` for the addresses you have discovered");
                    }
                    if (inv.stage().isDryRun()) {
                        return Command.Output.ok(
                                "would move the vantage to " + address,
                                "hop distance is then measured from there, not from your rig",
                                "current vantage: " + vantage(inv.session()),
                                "current ceiling: " + ceiling(inv.session()),
                                "whether you hold a foothold there is the rules' to decide, not this");
                    }
                    GameSession.Outcome outcome = inv.session().connectTo(address);
                    if (outcome.succeeded() && outcome.message().isBlank()) {
                        // Status preserved, so `$?` still carries what the rules decided; only the
                        // wording is ours, because a command that prints nothing looks like one
                        // that did nothing.
                        return new Command.Output(
                                List.of("vantage is now " + address), outcome.status());
                    }
                    return Command.Output.of(outcome);
                }));

        registry.add(new Verb(
                "download",
                List.of(),
                "Pull a recoverable document off a machine you hold.",
                true,
                List.of(
                        "download <address>         recover what is there, and print it",
                        "download -n <address>      say what that would mean, and take nothing",
                        "",
                        "Documents are flavour. Some carry schematic material, and only from machines",
                        "hard enough to be worth the risk — a deep but easy machine yields the reading",
                        "and nothing else. NOTHING IN THEM IS REQUIRED TO ADVANCE: a run that never",
                        "downloads a single fragment can reach everything a run that downloads all of",
                        "them can.",
                        "",
                        "`net` marks a machine with `document` when there is something there to take."),
                inv -> {
                    String address = address(inv);
                    if (address.isBlank()) {
                        return Command.Output.usage(
                                "download <address> — `net | grep document` lists what has something");
                    }
                    if (inv.stage().isDryRun()) {
                        return Command.Output.ok(
                                "would recover a document from " + address,
                                "documents are flavour: nothing in one is required to advance",
                                "schematic material comes only off hard machines, and this does not",
                                "  guess which those are");
                    }
                    GameSession.Outcome outcome = inv.session().download(address);
                    if (!outcome.succeeded()) {
                        return Command.Output.of(outcome);
                    }
                    List<String> out = new ArrayList<>();
                    if (!outcome.message().isBlank()) {
                        out.add(outcome.message());
                    }
                    recovered(inv.session(), address).ifPresent(document -> {
                        out.add("");
                        out.add(document.title());
                        out.add("");
                        out.addAll(NetText.documentBody(document.documentId()));
                    });
                    return new Command.Output(out, outcome.status());
                }));
    }

    // ------------------------------------------------------------------ rendering

    /**
     * What {@code sweep -n} prints.
     *
     * <p>Four published facts and one figure that is the player's own. There is deliberately no
     * verdict: no "affordable", no subtraction, and above all no estimate of what would be found.
     * The last of those is not squeamishness — detection is settled by a roll made once at world
     * generation and stored, so a client that estimated it would be reading the answer out of a save
     * file the player already has, and the mechanic that makes re-sweeping pointless would stop
     * teaching anything.
     */
    private static List<String> dryRun(GameSession session, String flag) {
        Sweep sweep = SWEEPS.stream()
                .filter(s -> s.flag().equals(flag))
                .findFirst()
                .orElse(SWEEPS.getFirst());

        List<String> out = new ArrayList<>();
        out.add("would run " + sweep.label() + " (" + sweep.itemId() + ")");
        out.add("published cost: " + sweep.cycles() + " cycles, held for about "
                + sweep.seconds() + "s and released into thermal recovery when it ends");
        out.add("the cycles it holds ARE its noise: a sweep is work that reaches other machines");
        out.add("vantage: " + vantage(session));
        out.add("ceiling: " + ceiling(session) + " — a tier buys sensitivity inside that, never more of it");
        out.add("available: " + session.computeBudget().available().cycles() + " cycles");
        return out;
    }

    /**
     * {@code net --docs} — the recovered fragments, or one of them.
     *
     * <p>Reading is a read, so it lives on the source verb rather than on {@code download}, which
     * changes something and therefore may never appear in a pipeline. That split means
     * {@code net --docs | grep material} works and {@code download | ...} correctly does not.
     */
    private static Command.Output documents(GameSession session, String documentId) {
        List<NetDocument> documents = session.documents();
        if (documents.isEmpty()) {
            return Command.Output.ok(
                    "(nothing recovered yet)",
                    "",
                    "Documents sit on file stores and defended machines, never on the home server —",
                    "the reading starts one bridge out. `download <address>` takes one.");
        }
        if (documentId.isBlank()) {
            return Command.Output.ok(NetText.documentRows(documents));
        }
        Optional<NetDocument> found = documents.stream()
                .filter(d -> d.documentId().equalsIgnoreCase(documentId))
                .findFirst();
        if (found.isEmpty()) {
            return Command.Output.usage(
                    "net: nothing recovered called '" + documentId + "' — run `net --docs` for the ids");
        }
        List<String> out = new ArrayList<>();
        out.add(found.get().title());
        out.add("recovered from " + found.get().recoveredFrom());
        out.add("");
        out.addAll(NetText.documentBody(found.get().documentId()));
        return Command.Output.ok(out);
    }

    /** The fragment a download just produced, matched on where it came from. */
    private static Optional<NetDocument> recovered(GameSession session, String address) {
        List<NetDocument> documents = session.documents();
        for (int i = documents.size() - 1; i >= 0; i--) {
            if (documents.get(i).recoveredFrom().equalsIgnoreCase(address)) {
                return Optional.of(documents.get(i));
            }
        }
        return Optional.empty();
    }

    /**
     * The address a verb was given, whichever side of the parser it came out on.
     *
     * <p>⚠ Measured against {@link CommandLine}: {@code -n} is in that parser's {@code takesValue}
     * set — it is {@code head -n 5}'s flag as much as it is {@code --dry-run}'s short form — so a
     * clustered short {@code -n} followed by a word swallows that word as the flag's <em>value</em>
     * and it never reaches the positional list. {@code connect -n 10.0.0.9} therefore parses as
     * {@code flags{n: "10.0.0.9"}} with no arguments at all, and a verb that read only
     * {@code argument(0)} would answer "connect &lt;address&gt;" to a line that plainly named one.
     * The long form has the same shape for a different reason: {@code --dry-run} takes the next
     * non-flag word as its value.
     *
     * <p>The same trap sits under {@code breach -n &lt;target&gt;} today. It is not this lane's file
     * to fix and is raised in the integration note.
     */
    private static String address(Command.Invocation invocation) {
        String positional = invocation.stage().argument(0).orElse("");
        if (!positional.isBlank()) {
            return positional;
        }
        String shortForm = invocation.stage().flag("n").orElse("");
        return shortForm.isBlank() ? invocation.stage().flag("dry-run").orElse("") : shortForm;
    }

    private static String vantage(GameSession session) {
        String address = session.net().vantageAddress();
        return address.isBlank() ? "your own rig" : address;
    }

    private static String ceiling(GameSession session) {
        int hops = session.net().hopCeiling();
        return hops + (hops == 1 ? " hop" : " hops");
    }

    // ------------------------------------------------------------------ the verb type

    private interface Body {
        Command.Output apply(Command.Invocation invocation);
    }

    /**
     * One network verb.
     *
     * <p>Structurally identical to {@code BreachCommands.Verb} and separate from it for the same
     * reason that one is separate from {@code BuiltinCommands.SimpleCommand}: both are private to
     * their file. Three near-identical private records is a real smell, and the right fix is one
     * package-private verb type — but that is a change to two files this lane does not own, so it is
     * raised in the integration note rather than made here.
     */
    private record Verb(
            String name,
            List<String> aliases,
            String synopsis,
            boolean sideEffect,
            List<String> helpLines,
            Body body)
            implements Command {

        Verb {
            aliases = List.copyOf(aliases);
            helpLines = List.copyOf(helpLines);
        }

        @Override
        public boolean hasSideEffect() {
            return sideEffect;
        }

        @Override
        public List<String> help() {
            List<String> out = new ArrayList<>();
            out.add(synopsis);
            if (!helpLines.isEmpty()) {
                out.add("");
                out.addAll(helpLines);
            }
            out.add("");
            out.add("Universal flags: -h  --explain  -n/--dry-run  -v/--verbose  --");
            return out;
        }

        @Override
        public Output run(Invocation invocation) {
            return body.apply(invocation);
        }
    }
}
