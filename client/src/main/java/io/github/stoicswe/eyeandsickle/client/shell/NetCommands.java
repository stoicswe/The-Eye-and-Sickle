package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.view.NetText;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetFolder;
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
    private record Sweep(String flag, String itemId, String label, long cycles, long noise, long seconds) {}

    private static final List<Sweep> SWEEPS = List.of(
            new Sweep("", "net-sweep", "sweep", 2, 35, 20),
            new Sweep("--wide", "net-sweep-wide", "sweep --wide", 5, 55, 45),
            new Sweep("--deep", "net-sweep-deep", "sweep --deep", 9, 80, 90));

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
        registry.add(Commands.read("net")
                .category(CommandCategory.NETWORK)
                .value("docs", "cmd.net.docs")
                .synopsis("Every machine you have discovered, and how far away each one is.")
                .help(
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
                        "vantage, not from your rig — see connect(1).")
                .runs(inv -> {
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
        registry.add(Commands.act("sweep")
                .category(CommandCategory.NETWORK)
                .flag("wide", "cmd.sweep.wide")
                .flag("deep", "cmd.sweep.deep")
                .synopsis("Probe the network around your vantage for machines you have not seen.")
                .help(
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
                        "a DIFFERENT position is what moves it. What a sweep can hear depends on where it",
                        "is standing, so a foothold the same distance away is a second chance at the same",
                        "machine. See connect(1).")
                .runs(inv -> {
                    boolean wide = inv.stage().hasFlag("wide");
                    boolean deep = inv.stage().hasFlag("deep");
                    if (wide && deep) {
                        return Command.Output.usage("sweep: --wide and --deep are two different instruments; pick one");
                    }
                    String flag = deep ? "--deep" : wide ? "--wide" : "";
                    if (inv.stage().isDryRun()) {
                        return Command.Output.ok(dryRun(inv.session(), flag));
                    }
                    return Command.Output.of(inv.session().sweep(flag));
                }));

        registry.add(Commands.act("connect")
                .category(CommandCategory.NETWORK)
                .synopsis("Move your vantage to a machine you hold a foothold on.")
                .help(
                        "connect <address>          operate from there instead",
                        "connect -n <address>       say what that would mean, and move nothing",
                        "",
                        "The hop ceiling is measured from your VANTAGE, not from your rig. That is what",
                        "makes a one-hop ceiling survivable across a whole world: you do not buy your way",
                        "further out, you move further out. Breach a machine, take the foothold, connect",
                        "to it, and sweep again — everything one hop from THERE is now in reach.",
                        "",
                        "A foothold is what a successful breach leaves behind. Without one this refuses,",
                        "and the refusal names what is missing.")
                .runs(inv -> {
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
                        return new Command.Output(List.of("vantage is now " + address), outcome.status());
                    }
                    return Command.Output.of(outcome);
                }));

        registry.add(Commands.act("download")
                .category(CommandCategory.NETWORK)
                .synopsis("Pull a recoverable document off a machine you hold.")
                .help(
                        "download <address>         recover what is there, and print it",
                        "download -n <address>      say what that would mean, and take nothing",
                        "",
                        "Documents are flavour. Some carry schematic material, and only from machines",
                        "hard enough to be worth the risk — a deep but easy machine yields the reading",
                        "and nothing else. NOTHING IN THEM IS REQUIRED TO ADVANCE: a run that never",
                        "downloads a single fragment can reach everything a run that downloads all of",
                        "them can.",
                        "",
                        "`net` marks a machine with `document` when there is something there to take.")
                .runs(inv -> {
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

        registerFolders(registry);
    }

    // ------------------------------------------------------------------ filing

    /**
     * The filing verbs: {@code folders}, {@code mkdir}, {@code mvdir}, {@code rmdir}, {@code file}.
     *
     * <h2>The names are the real ones, and one of them deliberately is not</h2>
     *
     * {@code mkdir} and {@code rmdir} are the Unix verbs a player either already knows or is being
     * taught here to carry back out ({@code docs/client/04-terminology-and-education.md}). Moving is
     * {@code mvdir} rather than {@code mv} for one reason: real {@code mv} moves <em>anything</em>,
     * and a player who learned that {@code mv} in this game meant "re-parent a folder" would have
     * learned something false about the system it is named after. Teaching nothing beats teaching a
     * wrong mapping — {@code CLAUDE.md}'s rule for the curriculum, applied to a verb.
     *
     * <h2>Folders are named by path here and by id in the window</h2>
     *
     * A path is what a person can type; an id is what survives a rename. Both surfaces resolve to the
     * same intent, and the resolution happens in the rules ({@code FolderRules.byPath}) rather than
     * here — a shell that walked the tree itself would be a second implementation of the lookup, and
     * would be the one that disagreed about case.
     */
    private static void registerFolders(Shell.CommandRegistry registry) {

        registry.add(Commands.read("folders")
                .category(CommandCategory.NETWORK)
                .aliases("lsdir")
                .synopsis("How you have filed the machines you have found.")
                .help(
                        "folders                    the whole tree, and what is not filed yet",
                        "",
                        "A source, so it may head a pipeline:  folders | grep 10.0.4",
                        "",
                        "Filing is yours and nothing reads it back. A machine in a folder is not easier",
                        "to breach, cheaper to sweep or more likely to be found; folders cost nothing,",
                        "there is no limit on them, and nothing is gated on how many you have. They are",
                        "somewhere to put the address you will want in an hour and will not remember.",
                        "",
                        "The count after a folder's name is everything filed under it INCLUDING its",
                        "sub-folders, which is the number that tells you whether opening it is worth it.")
                .runs(inv -> {
                    List<NetFolder> folders = inv.session().folders();
                    List<String> unfiled = inv.session().unfiledNodes();
                    if (folders.isEmpty() && unfiled.isEmpty()) {
                        return Command.Output.ok(NetText.NO_FOLDERS);
                    }
                    return Command.Output.ok(NetText.folderRows(folders, unfiled));
                }));

        registry.add(Commands.act("mkdir")
                .category(CommandCategory.NETWORK)
                .arg("path", "cmd.mkdir.arg.path")
                .synopsis("Make a folder to file machines into.")
                .help(
                        "mkdir <name>               a folder at the top level",
                        "mkdir <parent>/<name>      a folder inside an existing one",
                        "",
                        "Paths are '/'-separated and case-insensitive, so a name cannot contain a '/'.",
                        // The depth is quoted from the rules, the same way the sweep table above
                        // quotes the sweep ladder's costs and for the same reason: the port
                        // publishes no per-limit read, and help that could not say what the limit is
                        // would be help worth nothing. It decides nothing — the rules refuse on
                        // their own number regardless of what is printed here.
                        "Sub-folders nest 5 deep at most — deep enough for any real filing of a few",
                        "hundred machines, shallow enough that the deepest row still fits beside its",
                        "address.",
                        "",
                        "Two folders in the same place cannot share a name. Two in different places can.")
                .runs(inv -> {
                    String path = inv.stage().argument(0).orElse("");
                    if (path.isBlank()) {
                        return Command.Output.usage("mkdir <name> — or <parent>/<name> to nest it");
                    }
                    String parentPath = parentOf(path);
                    String name = leafOf(path);
                    String parentId = "";
                    if (!parentPath.isEmpty()) {
                        parentId = folderId(inv.session(), parentPath);
                        if (parentId.isEmpty()) {
                            return Command.Output.usage(
                                    "mkdir: no folder at '" + parentPath + "' — run `folders` for what there is");
                        }
                    }
                    GameSession.Outcome outcome = inv.session().createFolder(parentId, name);
                    return outcome.succeeded()
                            ? new Command.Output(List.of("created " + path), outcome.status())
                            : Command.Output.of(outcome);
                }));

        registry.add(Commands.act("rmdir")
                .category(CommandCategory.NETWORK)
                .arg("path", "cmd.rmdir.arg.path")
                .synopsis("Remove a folder. What was inside it moves up a level.")
                .help(
                        "rmdir <path>               remove one folder",
                        "",
                        "NOT RECURSIVE, AND THAT IS DELIBERATE. Sub-folders and filed machines re-parent",
                        "to wherever the removed folder was, so the worst a mistaken rmdir can do is",
                        "flatten a level. Nothing about a machine is lost — filing is a note you wrote,",
                        "not a thing you own, and there is no risk lesson worth teaching by deleting it.")
                .runs(inv -> {
                    String path = inv.stage().argument(0).orElse("");
                    if (path.isBlank()) {
                        return Command.Output.usage("rmdir <path> — run `folders` for what there is");
                    }
                    String id = folderId(inv.session(), path);
                    if (id.isEmpty()) {
                        return Command.Output.usage("rmdir: no folder at '" + path + "'");
                    }
                    GameSession.Outcome outcome = inv.session().removeFolder(id);
                    return outcome.succeeded()
                            ? new Command.Output(
                                    List.of("removed " + path + "; what was in it moved up a level"), outcome.status())
                            : Command.Output.of(outcome);
                }));

        registry.add(Commands.act("mvdir")
                .category(CommandCategory.NETWORK)
                .value("name", "cmd.mvdir.name")
                .arg("path", "cmd.mvdir.arg.path")
                .optionalArg("new-parent", "cmd.mvdir.arg.new-parent")
                .synopsis("Move or rename a folder.")
                .help(
                        "mvdir <path> <new-parent>  put it inside another folder",
                        "mvdir <path> /             put it back at the top level",
                        "mvdir <path> --name <new>  rename it where it is",
                        "",
                        "NOT `mv`. Real mv(1) moves anything — files, directories, across a filesystem —",
                        "and a verb here that meant only 'reparent a folder' would teach you something",
                        "false about the command it borrowed its name from. To move a MACHINE into a",
                        "folder, that is file(1); a machine is not a file on your disk and does not",
                        "pretend to be one.",
                        "",
                        "A folder cannot be moved inside itself or inside anything it already contains.")
                .runs(inv -> {
                    String path = inv.stage().argument(0).orElse("");
                    if (path.isBlank()) {
                        return Command.Output.usage("mvdir <path> <new-parent>  |  mvdir <path> --name <new>");
                    }
                    String id = folderId(inv.session(), path);
                    if (id.isEmpty()) {
                        return Command.Output.usage("mvdir: no folder at '" + path + "'");
                    }
                    String rename = inv.stage().flag("name").orElse("");
                    if (!rename.isBlank()) {
                        return Command.Output.of(inv.session().renameFolder(id, rename));
                    }
                    String target = inv.stage().argument(1).orElse("");
                    if (target.isBlank()) {
                        return Command.Output.usage(
                                "mvdir <path> <new-parent> — or '/' for the top level, or --name to rename");
                    }
                    String parentId = "/".equals(target.trim()) ? "" : folderId(inv.session(), target);
                    if (parentId.isEmpty() && !"/".equals(target.trim())) {
                        return Command.Output.usage("mvdir: no folder at '" + target + "'");
                    }
                    return Command.Output.of(inv.session().moveFolder(id, parentId));
                }));

        registry.add(Commands.act("file")
                .category(CommandCategory.NETWORK)
                .flag("out", "cmd.file.out")
                .arg("address", "cmd.file.arg.address")
                .optionalArg("folder", "cmd.file.arg.folder")
                .synopsis("Put a machine you have discovered into a folder.")
                .help(
                        "file <address> <path>      file it there",
                        "file <address> --out       take it out of whatever folder it is in",
                        "",
                        "A machine is in one folder or none, the way a file is in one directory. Filing",
                        "it again elsewhere moves it rather than copying it.",
                        "",
                        "Only an address you have actually DISCOVERED can be filed, and the refusal for",
                        "an address you have not found is word-for-word the refusal for one that does not",
                        "exist. That is on purpose: two different answers would let you map the whole",
                        "world one guess at a time without ever running a sweep.")
                .runs(inv -> {
                    String address = inv.stage().argument(0).orElse("");
                    if (address.isBlank()) {
                        return Command.Output.usage("file <address> <folder>  |  file <address> --out");
                    }
                    if (inv.stage().hasFlag("out")) {
                        GameSession.Outcome out = inv.session().fileNode(address, "");
                        return out.succeeded()
                                ? new Command.Output(List.of(address + " is no longer in a folder"), out.status())
                                : Command.Output.of(out);
                    }
                    String path = inv.stage().argument(1).orElse("");
                    if (path.isBlank()) {
                        return Command.Output.usage(
                                "file <address> <folder> — `folders` lists them, `mkdir` makes one");
                    }
                    String id = folderId(inv.session(), path);
                    if (id.isEmpty()) {
                        return Command.Output.usage("file: no folder at '" + path + "'");
                    }
                    GameSession.Outcome outcome = inv.session().fileNode(address, id);
                    return outcome.succeeded()
                            ? new Command.Output(List.of("filed " + address + " under " + path), outcome.status())
                            : Command.Output.of(outcome);
                }));
    }

    /**
     * The id of the folder at a {@code /a/b} path, or {@code ""}.
     *
     * <p>Resolved against the published tree rather than by asking the rules to walk it, because the
     * port hands over {@link NetFolder#path()} already built — matching on it means the shell and the
     * window agree on what a path <em>is</em> by construction, including how a name containing spaces
     * renders. Case-insensitive for the same reason the rules are: the tree is a label the player
     * chose and refusing {@code /Eye} for a folder called {@code eye} helps nobody.
     */
    private static String folderId(GameSession session, String path) {
        String wanted = normalisePath(path);
        if (wanted.isEmpty()) {
            return "";
        }
        for (NetFolder folder : session.folders()) {
            if (normalisePath(folder.path()).equalsIgnoreCase(wanted)) {
                return folder.folderId();
            }
        }
        return "";
    }

    private static String normalisePath(String path) {
        String out = path == null ? "" : path.trim();
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    /** Everything before the last {@code /}, or {@code ""} for a top-level name. */
    private static String parentOf(String path) {
        String normalised = normalisePath(path);
        int cut = normalised.lastIndexOf('/');
        return cut < 0 ? "" : normalised.substring(0, cut);
    }

    /** The last segment. Left untrimmed of inner spaces — a folder may legitimately be called two words. */
    private static String leafOf(String path) {
        String normalised = normalisePath(path);
        int cut = normalised.lastIndexOf('/');
        return cut < 0 ? normalised : normalised.substring(cut + 1);
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
        Sweep sweep =
                SWEEPS.stream().filter(s -> s.flag().equals(flag)).findFirst().orElse(SWEEPS.getFirst());

        List<String> out = new ArrayList<>();
        out.add("would run " + sweep.label() + " (" + sweep.itemId() + ")");
        out.add("published cost: " + sweep.cycles() + " cycles, held for about " + sweep.seconds()
                + "s and released into thermal recovery when it ends");
        out.add("costs no ethecoin, at any tier — the tool is bought once, running it is cycles only");
        out.add("published noise: " + sweep.noise() + " while it runs, and NOTHING after it ends");
        out.add("  a sweep is cheap and loud: it puts packets on machines that are not yours, which is");
        out.add("  what noise measures. It is not your load. When the countdown ends the meter drops.");
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
}
