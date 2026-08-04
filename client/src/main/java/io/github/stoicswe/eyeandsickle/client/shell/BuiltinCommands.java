package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.PoolScheme;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The command catalogue, from {@code docs/client/04-terminology-and-education.md} §3.10.
 *
 * <h2>The aliases are the teaching hook</h2>
 *
 * {@code top}, {@code ps}, {@code ss}, {@code netstat}, {@code df}, {@code ls}, {@code kill},
 * {@code jobs}, {@code id}, {@code whoami} and {@code man} are not flavour names — they are the real
 * command names, mapped to the game action that genuinely corresponds. §3.3 calls this out
 * explicitly: a player types {@code top} and discovers their rig monitor <em>is</em> a {@code top}.
 * That is the entire educational bet, and it costs nothing to keep.
 *
 * <h2>Sources, filters, actions</h2>
 *
 * §3.7 divides the catalogue three ways and {@link Shell} enforces it: sources may open a pipeline,
 * filters may sit anywhere after a {@code |}, and actions may never appear in one at all.
 */
public final class BuiltinCommands {

    private BuiltinCommands() {}

    /** Builds the registry. Order here is the order `help` prints. */
    public static Shell.CommandRegistry registry() {
        Shell.CommandRegistry r = new Shell.CommandRegistry();

        // ---------------------------------------------------------------- sources
        r.add(Commands.read("ps")
                .category(CommandCategory.RIG)
                .synopsis("Compute allocation by consumer — what is holding your rig.")
                .lines(inv -> {
                    ComputeBudget b = inv.session().computeBudget();
                    List<String> out = new ArrayList<>();
                    out.add(pad("CONSUMER", 22) + pad("CYCLES", 8) + "STATE");
                    for (ComputeAllocation a : b.allocations()) {
                        out.add(pad(a.consumer().name().toLowerCase(Locale.ROOT), 22)
                                + pad(String.valueOf(a.cycles().cycles()), 8)
                                + (a.isRecovering() ? "recovering" : "active"));
                    }
                    out.add("");
                    out.add("total " + b.total().cycles()
                            + "  allocated " + b.allocated().cycles()
                            + "  recovering " + b.recovering().cycles()
                            + "  available " + b.available().cycles());
                    if (!b.reconciles()) {
                        // design/04 §3.1: the player finds a hidden miner by noticing the numbers do
                        // not add up. If they ever do not, say so loudly rather than hiding it.
                        out.add("WARNING: " + b.unaccountedFor().cycles() + " cycles unaccounted for.");
                    }
                    return out;
                }));

        r.add(Commands.read("ss")
                .category(CommandCategory.NETWORK)
                .aliases("netstat")
                .synopsis("Connection table — one row per known node.")
                .lines(inv -> {
                    List<String> out = new ArrayList<>();
                    out.add(pad("ADDRESS", 20) + pad("TIER", 6) + pad("RECON", 7) + "NOTE");
                    for (GameSession.KnownNode n : inv.session().knownNodes()) {
                        out.add(pad(n.address(), 20)
                                + pad("T" + n.tier(), 6)
                                + pad(String.valueOf(n.reconLevel()), 7)
                                + (n.hostsForeignMiner() ? "foreign miner present" : ""));
                    }
                    if (out.size() == 1) {
                        out.add("(nothing discovered yet — recon a target first)");
                    }
                    return out;
                }));

        r.add(Commands.read("df")
                .category(CommandCategory.FILES)
                .synopsis("Storage tiers as mount points, with their exposure.")
                .lines(inv -> {
                    List<String> out = new ArrayList<>();
                    out.add(pad("MOUNT", 26) + pad("ITEMS", 7) + "EXPOSURE");
                    out.add(pad("/rig/storage/vault", 26)
                            + pad(
                                    String.valueOf(inv.session()
                                            .items(StorageTier.VAULT)
                                            .size()),
                                    7)
                            + "safe");
                    out.add(pad("/rig/storage/standard", 26)
                            + pad(
                                    String.valueOf(inv.session()
                                            .items(StorageTier.STANDARD_STORAGE)
                                            .size()),
                                    7)
                            + "exposed while online");
                    out.add(pad("/rig/storage/high", 26)
                            + pad(
                                    String.valueOf(inv.session()
                                            .items(StorageTier.HIGH_HACKABLE_ZONE)
                                            .size()),
                                    7)
                            + "always exposed");
                    return out;
                }));

        r.add(Commands.read("ls")
                .category(CommandCategory.FILES)
                .optionalArg("path", "cmd.ls.arg.path")
                .synopsis("List what is in a place in the namespace.")
                .lines(inv -> {
                    String path = inv.stage().argument(0).orElse("/");
                    if (Glob.isGlob(path)) {
                        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
                        String pattern = path.substring(path.lastIndexOf('/') + 1);
                        List<String> matched = new ArrayList<>();
                        for (String entry : Namespace.list(inv.session(), dir)) {
                            String name = entry.split("\\s")[0];
                            if (Glob.matches(
                                    pattern, name.endsWith("/") ? name.substring(0, name.length() - 1) : name)) {
                                matched.add(entry);
                            }
                        }
                        // A pattern matching nothing comes back unexpanded, exactly as a real shell
                        // does — which is the behaviour glob(7)'s transfer test asks the reader to
                        // reproduce with `echo zz*`.
                        return matched.isEmpty() ? List.of(path) : matched;
                    }
                    List<String> entries = Namespace.list(inv.session(), path);
                    return entries.isEmpty() ? List.of("ls: " + path + ": no such place") : entries;
                }));

        r.add(Commands.read("ledger")
                .category(CommandCategory.ECONOMY)
                .synopsis("Every movement of ethecoin, newest first.")
                .lines(inv -> {
                    List<String> out = new ArrayList<>();
                    out.add(pad("WHEN", 22) + pad("DELTA", 12) + pad("BALANCE", 12) + "WHAT");
                    for (GameSession.LedgerRow row : inv.session().ledger(200)) {
                        out.add(pad(row.at().toString(), 22)
                                + pad(signed(row.deltaWei()), 12)
                                + pad(Ethecoin.format(row.balanceAfterWei()), 12)
                                + row.description());
                    }
                    if (out.size() == 1) {
                        out.add("(no entries yet)");
                    }
                    return out;
                }));

        r.add(Commands.read("log")
                .category(CommandCategory.RIG)
                .value("p", "cmd.log.p")
                .synopsis("What the rig has been doing. -p filters by severity.")
                .lines(inv -> {
                    // -p is journalctl's own flag and takes journalctl's own semantics: a NUMBER,
                    // where lower is more severe, and the filter is "this level or worse". A player
                    // who learns `-p 4` here can type it into journalctl tonight.
                    int minSeverity = inv.stage()
                            .flag("p")
                            .filter(v -> !v.isBlank())
                            .map(v -> {
                                try {
                                    return Integer.parseInt(v.trim());
                                } catch (NumberFormatException e) {
                                    return 7;
                                }
                            })
                            .orElse(7);

                    List<GameSession.LogLine> lines = inv.session().log(minSeverity, 200);
                    List<String> out = new ArrayList<>();
                    for (GameSession.LogLine line : lines) {
                        out.add(pad(line.at().toString(), 22)
                                + pad(line.glyph() + " " + line.keyword(), 10)
                                + pad(line.facility(), 10)
                                + line.message());
                    }
                    if (out.isEmpty()) {
                        out.add("(nothing logged yet)");
                    }
                    return out;
                }));

        r.add(Commands.read("items")
                .category(CommandCategory.FILES)
                .synopsis("Everything you own, across all three tiers.")
                .lines(inv -> {
                    List<String> out = new ArrayList<>();
                    out.add(pad("NAME", 30) + pad("TIER", 22) + "ORIGIN");
                    for (GameSession.InventoryItem i : inv.session().items(null)) {
                        out.add(pad(i.displayName(), 30)
                                + pad(i.tier().name().toLowerCase(Locale.ROOT), 22)
                                + i.origin());
                    }
                    if (out.size() == 1) {
                        out.add("(nothing owned yet)");
                    }
                    return out;
                }));

        // ---------------------------------------------------------------- filters
        r.add(Commands.filter("grep")
                .category(CommandCategory.TEXT)
                .flag("i", "cmd.grep.i")
                .flag("v", "cmd.grep.v")
                .arg("pattern", "cmd.grep.arg.pattern")
                .synopsis("Keep only the lines that match. -i ignore case, -v invert, -E extended regex.")
                .lines(inv -> {
                    String pattern = inv.stage().argument(0).orElse("");
                    if (pattern.isEmpty()) {
                        return List.of("grep: no pattern given");
                    }
                    boolean ignoreCase = inv.stage().hasFlag("i");
                    boolean invert = inv.stage().hasFlag("v");
                    List<String> out = new ArrayList<>();
                    for (String line : inv.input()) {
                        boolean hit;
                        try {
                            // The pattern is a REGULAR EXPRESSION, not a glob — the * means something
                            // different here than it does in a path, and regular-expression(7) exists
                            // to answer the confusion this deliberately creates.
                            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                                    pattern, ignoreCase ? java.util.regex.Pattern.CASE_INSENSITIVE : 0);
                            hit = p.matcher(line).find();
                        } catch (java.util.regex.PatternSyntaxException bad) {
                            return List.of("grep: bad pattern: " + bad.getDescription());
                        }
                        if (hit != invert) {
                            out.add(line);
                        }
                    }
                    return out;
                }));

        r.add(Commands.filter("sort")
                .category(CommandCategory.TEXT)
                .flag("r", "cmd.sort.r")
                .synopsis("Reorder lines. -r reverses.")
                .lines(inv -> {
                    List<String> out = new ArrayList<>(inv.input());
                    out.sort(String::compareToIgnoreCase);
                    if (inv.stage().hasFlag("r")) {
                        java.util.Collections.reverse(out);
                    }
                    return out;
                }));

        r.add(Commands.filter("uniq")
                .category(CommandCategory.TEXT)
                .flag("c", "cmd.uniq.c")
                .synopsis("Collapse runs of identical neighbouring lines. -c counts them.")
                .lines(inv -> {
                    List<String> out = new ArrayList<>();
                    String previous = null;
                    int run = 0;
                    boolean count = inv.stage().hasFlag("c");
                    for (String line : inv.input()) {
                        if (line.equals(previous)) {
                            run++;
                        } else {
                            if (previous != null) {
                                out.add(count ? run + " " + previous : previous);
                            }
                            previous = line;
                            run = 1;
                        }
                    }
                    if (previous != null) {
                        out.add(count ? run + " " + previous : previous);
                    }
                    return out;
                }));

        r.add(Commands.filter("head")
                .category(CommandCategory.TEXT)
                .value("n", "cmd.head.n")
                .synopsis("Show the first few lines and stop. -n sets how many.")
                .lines(inv -> inv.input().stream().limit(countFlag(inv, 10)).toList()));

        r.add(Commands.filter("tail")
                .category(CommandCategory.TEXT)
                .value("n", "cmd.tail.n")
                .synopsis("Show the last few lines.")
                .lines(inv -> {
                    long n = countFlag(inv, 10);
                    int from = (int) Math.max(0, inv.input().size() - n);
                    return inv.input().subList(from, inv.input().size());
                }));

        r.add(Commands.filter("wc")
                .category(CommandCategory.TEXT)
                .synopsis("Count lines instead of showing them. -l is the only mode here.")
                .lines(inv -> List.of(String.valueOf(inv.input().size()))));

        r.add(Commands.filter("cut")
                .category(CommandCategory.TEXT)
                .value("f", "cmd.cut.f")
                .synopsis("Keep chosen whitespace-separated columns. -f selects them, 1-based.")
                .lines(inv -> {
                    String spec = inv.stage().flag("f").orElse("1");
                    List<Integer> fields = new ArrayList<>();
                    for (String part : spec.split(",")) {
                        try {
                            fields.add(Integer.parseInt(part.trim()));
                        } catch (NumberFormatException ignored) {
                            return List.of("cut: bad field list: " + spec);
                        }
                    }
                    List<String> out = new ArrayList<>();
                    for (String line : inv.input()) {
                        String[] columns = line.trim().split("\\s+");
                        StringBuilder sb = new StringBuilder();
                        for (int f : fields) {
                            if (f >= 1 && f <= columns.length) {
                                if (!sb.isEmpty()) {
                                    sb.append(' ');
                                }
                                sb.append(columns[f - 1]);
                            }
                        }
                        out.add(sb.toString());
                    }
                    return out;
                }));

        // ---------------------------------------------------------------- actions
        r.add(Commands.act("mine")
                .category(CommandCategory.ECONOMY)
                .value("pool", "cmd.mine.pool")
                .flag("solo", "cmd.mine.solo")
                .value("allocate", "cmd.mine.allocate")
                .synopsis("Commit cycles to self-mining. --allocate=N, --pool or --solo, or no argument to report.")
                .runs(inv -> {
                    // Mode first: `mine --solo` takes no allocation and must not be read as a usage
                    // error for the allocation it did not carry.
                    if (inv.stage().hasFlag("pool") || inv.stage().hasFlag("solo")) {
                        MiningMode mode = inv.stage().hasFlag("solo") ? MiningMode.SOLO : MiningMode.POOLED;
                        if (inv.stage().isDryRun()) {
                            return Command.Output.ok(modePreview(inv.session(), mode));
                        }
                        // `--pool=<id>` joins that pool AND switches to pooled, because asking for a
                        // named pool while solo can only mean one thing. Doing the mode switch
                        // silently would be worse than refusing; doing neither would be worst.
                        String named = inv.stage().flag("pool").orElse("");
                        GameSession.Outcome modeOutcome = inv.session().setMiningMode(mode);
                        if (mode == MiningMode.POOLED && !named.isBlank()) {
                            return Command.Output.of(inv.session().setMiningPool(named));
                        }
                        return Command.Output.of(modeOutcome);
                    }
                    String value = inv.stage()
                            .flag("allocate")
                            .orElse(inv.stage().argument(0).orElse(""));
                    if (value.isBlank()) {
                        // No argument at all reports rather than refusing. `mine` is the only view of
                        // the chain in the terminal, and a bare invocation asking "how is mining
                        // going" is the obvious reading.
                        return Command.Output.ok(miningReport(inv.session()));
                    }
                    long cycles;
                    try {
                        cycles = Long.parseLong(value.trim());
                    } catch (NumberFormatException e) {
                        return Command.Output.usage("mine: not a number: " + value);
                    }
                    if (inv.stage().isDryRun()) {
                        // A dry run prints published, static figures and NEVER a verdict — no
                        // "affordable", no computed remainder. Gate evaluation is the server's, and
                        // printing the numbers so the player does the arithmetic is both the correct
                        // architecture and the better teaching (§3.4).
                        return Command.Output.ok(
                                "would allocate " + cycles + " cycles to self-mining",
                                "self-mining yields 0.4 EC per cycle-hour, generates no heat, and is online-only",
                                "rig total: "
                                        + inv.session().computeBudget().total().cycles()
                                        + "  currently available: "
                                        + inv.session()
                                                .computeBudget()
                                                .available()
                                                .cycles());
                    }
                    return Command.Output.of(inv.session().allocateSelfMining(cycles));
                }));

        r.add(Commands.act("send")
                .category(CommandCategory.ECONOMY)
                .choice("fee", "cmd.send.fee", "economy", "standard", "priority")
                .arg("address", "cmd.send.arg.address")
                .arg("amount", "cmd.send.arg.amount")
                .synopsis("Send ethecoin to an address. --fee=economy|standard|priority.")
                .runs(inv -> {
                    String to = inv.stage().argument(0).orElse("");
                    String amount = inv.stage().argument(1).orElse("");
                    FeeTier tier = FeeTier.of(inv.stage().flag("fee").orElse("standard"));
                    if (to.isBlank() || amount.isBlank()) {
                        return Command.Output.usage("send <address> <amount in EC> [--fee=priority]");
                    }
                    // ⚠ Parsed through Ethecoin.ofDecimal, NEVER through a double. This is the one
                    // place a player types an amount, and `0.037097927036961408` is exactly the kind
                    // of amount they can now type — a double holds about 15-16 significant digits, so
                    // parsing it that way would silently send a different number than was typed.
                    // ofDecimal also REFUSES anything finer than 18 places rather than truncating it.
                    java.math.BigInteger wei;
                    try {
                        wei = io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.ofDecimal(amount.trim())
                                .wei();
                    } catch (NumberFormatException | ArithmeticException e) {
                        return Command.Output.usage("send: not an amount: " + amount + " (up to 18 decimal places)");
                    }
                    if (inv.stage().isDryRun()) {
                        ChainMempool pool = inv.session().mempool();
                        // Published figures and no verdict: the player does the arithmetic (pillar C4).
                        return Command.Output.ok(
                                "would send " + Ethecoin.format(wei) + " to " + to,
                                "fee: " + Ethecoin.format(Balance.feeFor(tier)) + " (" + tier.label() + ") — "
                                        + tier.promise(),
                                String.format(
                                        Locale.ROOT,
                                        "the cheapest slot in the next block is going for %.0f; "
                                                + "a block arrives every ~%d min on average",
                                        Ethecoin.format(pool.lowFeeWei()),
                                        Math.round(pool.expectedNextBlockSeconds() / 60)),
                                "the balance moves at once; the chain record confirms when a miner " + "picks it up");
                    }
                    return Command.Output.of(inv.session().send(to, wei, tier));
                }));

        r.add(Commands.act("mempool")
                .category(CommandCategory.ECONOMY)
                .synopsis("What is waiting for a miner, and what the next blocks hold.")
                .runs(inv -> {
                    ChainMempool pool = inv.session().mempool();
                    List<String> out = new ArrayList<>();
                    out.add(String.format(
                            Locale.ROOT,
                            "%d of yours waiting · cheapest slot %s · top of the queue %s",
                            pool.yoursPending(),
                            Ethecoin.format(pool.lowFeeWei()),
                            Ethecoin.format(pool.highFeeWei())));
                    // The mean stays published beside the estimate: the ETA is derived from it, and
                    // a countdown with no stated average is a deadline. The elapsed figure is a fact.
                    out.add(String.format(
                            Locale.ROOT,
                            "a block every ~%d min on average · last one %s ago",
                            Math.round(pool.expectedNextBlockSeconds() / 60),
                            // ⚠ duration() answers "never" at zero — correct for an infinite wait,
                            // nonsense for an elapsed one ("last one never ago").
                            pool.secondsSinceLastBlock() <= 0 ? "0s" : duration(pool.secondsSinceLastBlock())));
                    out.add("");
                    out.add(pad("", 8) + pad("TXS", 6) + pad("YOURS", 7) + pad("FULL", 7) + pad("FEES", 10) + "~WHEN");
                    for (ChainMempool.ProjectedBlock p : pool.projected()) {
                        out.add(pad(p.index() == 0 ? "next" : "+" + (p.index() + 1), 8)
                                + pad(String.valueOf(p.transactions()), 6)
                                + pad(String.valueOf(p.yours()), 7)
                                + pad(String.format(Locale.ROOT, "%.0f%%", p.fullness() * 100), 7)
                                + pad(Ethecoin.format(p.feesWei()), 10)
                                + eta(p, pool.expectedNextBlockSeconds()));
                    }
                    if (!pool.queued().isEmpty()) {
                        out.add("");
                        out.add("YOUR PENDING");
                        for (ChainMempool.Queued q : pool.queued()) {
                            // Padded, because the row gained two columns and an unpadded amount put
                            // every fee at a different indent — a table the eye cannot scan down.
                            out.add("  " + pad(q.tx().shortHash(), 16)
                                    + pad(Ethecoin.format(q.tx().valueWei()), 12)
                                    + pad("fee " + Ethecoin.format(q.tx().feeWei()), 14)
                                    + pad(
                                            q.beyondProjection()
                                                    ? "past +3"
                                                    : q.projectedIndex() == 0
                                                            ? "next block"
                                                            : "block +" + (q.projectedIndex() + 1),
                                            12)
                                    + pad(
                                            q.beyondProjection()
                                                    ? "no estimate"
                                                    : eta(
                                                            pool.projected().get(q.projectedIndex()),
                                                            pool.expectedNextBlockSeconds()),
                                            16)
                                    + q.tx().description());
                        }
                    }
                    out.add("");
                    out.add("Projections are what the next blocks would hold if mined now. Blocks arrive");
                    out.add("at random intervals and more transactions arrive meanwhile — not a schedule.");
                    out.add("~WHEN is an estimate the chain is free to overtake; past it the figure is");
                    out.add("how far into the distribution the wait has got, not how late a block is.");
                    return Command.Output.ok(out);
                }));

        r.add(Commands.act("pools")
                .category(CommandCategory.ECONOMY)
                .synopsis("List the mining pools on the chain and what each one costs.")
                .runs(inv -> {
                    List<MiningPool> pools = inv.session().pools();
                    if (pools.isEmpty()) {
                        return Command.Output.refused("not connected to a chain");
                    }
                    MiningSnapshot m = inv.session().miningChain();
                    String joined = m.pool() == null ? "" : m.pool().id();
                    List<String> out = new ArrayList<>();
                    out.add(pad("", 3)
                            + pad("POOL", 20)
                            + pad("SCHEME", 8)
                            + right("FEE", 7)
                            + right("CHAIN", 7)
                            + right("PAYS", 10));
                    for (MiningPool pool : pools) {
                        // The interval, not just the fee. They are the two axes of the choice and
                        // they pull against each other — a table showing only the fee would read as
                        // a ladder with an obvious top, which is exactly the wrong lesson.
                        double interval = pool.scheme() == PoolScheme.PPLNS
                                ? Balance.CHAIN_TARGET_BLOCK_SECONDS / Math.max(0.0001d, pool.networkShare())
                                : pool.shareSeconds();
                        out.add(pad(pool.id().equals(joined) ? " * " : "   ", 3)
                                + pad(pool.name(), 20)
                                + pad(pool.scheme().name(), 8)
                                + right(pool.feeText(), 7)
                                + right(pool.shareText(), 7)
                                + right("every " + duration(interval), 10));
                    }
                    out.add("");
                    out.add("Only the FEE changes what you earn. A pool's scheme and its size change");
                    out.add("only how lumpily you earn it — and the cheapest pool here pays least often.");
                    out.add("");
                    out.add("`mine --pool=<id>` to join. Ids: "
                            + pools.stream().map(MiningPool::id).collect(java.util.stream.Collectors.joining(", ")));
                    return Command.Output.ok(out);
                }));

        r.add(Commands.act("collect")
                .category(CommandCategory.ECONOMY)
                .synopsis("Sweep deployed-miner yield into your balance.")
                .runs(inv -> Command.Output.of(inv.session().collect())));

        r.add(Commands.act("scan")
                .category(CommandCategory.RIG)
                .flag("thorough", "cmd.scan.thorough")
                .flag("full", "cmd.scan.full")
                .synopsis("Search your own rig for things hiding from routine listings.")
                .runs(inv -> {
                    String tier = inv.stage().hasFlag("thorough")
                            ? "thorough"
                            : inv.stage().hasFlag("full") ? "full" : "quick";
                    if (inv.stage().isDryRun()) {
                        long cost =
                                switch (tier) {
                                    case "thorough" -> 35;
                                    case "full" -> 15;
                                    default -> 5;
                                };
                        return Command.Output.ok(
                                "would run scan --" + tier,
                                "published cost: " + cost + " cycles",
                                "available: "
                                        + inv.session()
                                                .computeBudget()
                                                .available()
                                                .cycles() + " cycles");
                    }
                    return Command.Output.of(inv.session().scan(tier));
                }));

        r.add(Commands.act("mv")
                .category(CommandCategory.FILES)
                .arg("item", "cmd.mv.arg.item")
                .arg("tier", "cmd.mv.arg.tier")
                .synopsis("Move an item between storage tiers.")
                .runs(inv -> {
                    String item = inv.stage().argument(0).orElse("");
                    String tier = inv.stage().argument(1).orElse("");
                    if (item.isBlank() || tier.isBlank()) {
                        return Command.Output.usage("mv <item> <vault|standard|high>");
                    }
                    StorageTier to =
                            switch (tier.toLowerCase(Locale.ROOT)) {
                                case "vault" -> StorageTier.VAULT;
                                case "standard" -> StorageTier.STANDARD_STORAGE;
                                case "high" -> StorageTier.HIGH_HACKABLE_ZONE;
                                default -> null;
                            };
                    if (to == null) {
                        return Command.Output.usage("mv: unknown tier '" + tier + "'");
                    }
                    return Command.Output.of(inv.session().moveItem(item, to));
                }));

        r.add(Commands.act("abort")
                .category(CommandCategory.BREACH)
                .synopsis("Withdraw from the current operation. Always confirms first.")
                .runs(inv -> {
                    // 130 is 128 + 2, and signal 2 is SIGINT — what Ctrl-C sends. That is not a
                    // coincidence or a flavour number: it is what a real machine reports when you
                    // interrupt something, and exit-status(7) teaches exactly this.
                    //
                    // There is nothing to abort yet because the breach minigame is [PROPOSAL]
                    // (docs/design/05). Saying so beats reporting a successful abort of nothing.
                    return new Command.Output(
                            List.of(
                                    "Nothing to abort — no operation is running.",
                                    "",
                                    "When there is one, this reports 130: that is 128 + 2, signal 2 is",
                                    "SIGINT, and SIGINT is what Ctrl-C sends. See exit-status(7)."),
                            ExitStatus.OK);
                }));

        // ---------------------------------------------------------------- arithmetic
        //
        // ⚠ The SAME engine the calculator window drives, deliberately. Pillar C1 says every window
        // action is reachable from the terminal, and the failure mode C1 is hardest to notice is not
        // a missing command — it is a command that quietly disagrees with its window. A second
        // evaluator here would drift on the first edge case somebody fixed in one place.
        //
        // It touches no session at all, which makes it the only command in this file that would
        // behave identically with the game closed. That is correct: the answer to 0xFF + 1 is not
        // the server's opinion, and a calculator that spent compute would be a tax on understanding
        // the rest of the game.
        r.add(Commands.read("calc")
                .category(CommandCategory.SHELL)
                .choice("bits", "cmd.calc.bits", "8", "16", "32", "64")
                .flag("signed", "cmd.calc.signed")
                .aliases("bc")
                .synopsis("Evaluate an expression in hex, decimal, octal or binary. --bits=N, --signed.")
                .runs(inv -> {
                    String expression = calcExpression(inv);
                    if (expression.isBlank()) {
                        return Command.Output.usage("calc <expression>   e.g. calc 0xff xor 0b1010, calc 1 lsh 12");
                    }
                    var width = calcWidth(inv);
                    if (width.isEmpty()) {
                        return Command.Output.usage("calc: --bits must be 8, 16, 32 or 64");
                    }
                    boolean signed = inv.stage().flag("signed").isPresent();
                    var result = io.github.stoicswe.eyeandsickle.client.ui.calc.Calculator.evaluate(
                            expression, width.get(), signed);
                    return result.ok()
                            ? Command.Output.ok(calcLines(result.calculator()))
                            : Command.Output.usage("calc: " + result.error());
                }));

        // ---------------------------------------------------------------- information
        r.add(Commands.read("id")
                .category(CommandCategory.RIG)
                .aliases("whoami")
                .synopsis("Who you are on this rig.")
                .lines(inv -> List.of(
                        "handle    " + inv.session().handle(),
                        "mode      " + inv.session().mode().label(),
                        "          " + inv.session().mode().explanation(),
                        "heat      " + inv.session().personalHeat(),
                        "balance   " + inv.session().balance())));

        r.add(Commands.read("verify")
                .category(CommandCategory.FILES)
                .arg("item", "cmd.verify.arg.item")
                .synopsis("Check an item's provenance chain.")
                .lines(inv -> {
                    String item = inv.stage().argument(0).orElse("");
                    if (item.isBlank()) {
                        return List.of("verify <item>");
                    }
                    return inv.session().items(null).stream()
                            .filter(i ->
                                    i.itemId().equals(item) || i.displayName().equalsIgnoreCase(item))
                            .findFirst()
                            .map(i -> i.hasProvenance()
                                    ? List.of(
                                            i.displayName() + " (" + i.itemId() + ")",
                                            "chain verified to genesis")
                                    // Honest rather than reassuring. A solo item has nobody to prove
                                    // anything to, and a chain signed by a key on the same disk would
                                    // prove only that the disk agreed with itself.
                                    : List.of(
                                            // ⚠ The id is PRINTED, not merely accepted. `verify`
                                            // has always taken an itemId and nothing showed one, so
                                            // the handle existed and was unreachable — and with
                                            // duplicates allowed it is the only way to say which of
                                            // two identically named items this is.
                                            i.displayName() + " (" + i.itemId() + ")",
                                            "no provenance chain.",
                                            "",
                                            "This is a solo game. Items here are not signed, because there is",
                                            "nobody to prove anything to and nothing to prove it against. A chain",
                                            "verifies what a set of keys attested — see provenance-chain(7)."))
                            .orElse(List.of("verify: no such item: " + item));
                }));

        return r;
    }

    // ------------------------------------------------------------------ calc

    /**
     * The expression, taken from the <b>raw</b> line rather than from the parsed arguments.
     *
     * <p>⚠ This is not fussiness. The shell's own parser turns {@code -1} into a short-flag cluster
     * — which is correct for every other command in this file and exactly wrong for one whose
     * arguments are arithmetic. Reading the raw segment and removing only the flags this command
     * actually declares is the one way {@code calc 8 - 1} and {@code calc -1 + 2} can both mean what
     * they say.
     */
    private static String calcExpression(Command.Invocation inv) {
        String raw = inv.stage().raw() == null ? "" : inv.stage().raw().trim();
        int space = raw.indexOf(' ');
        String rest = space < 0 ? "" : raw.substring(space + 1);
        return rest.replaceAll("--bits(=|\\s+)\\S+", " ")
                .replaceAll("--(signed|unsigned)\\b", " ")
                .trim();
    }

    /** {@code --bits=N}, defaulting to 32 — wide enough for an address, narrow enough to read. */
    private static java.util.Optional<io.github.stoicswe.eyeandsickle.client.ui.calc.WordSize> calcWidth(
            Command.Invocation inv) {
        String bits = inv.stage().flag("bits").filter(s -> !s.isBlank()).orElse("32");
        try {
            return io.github.stoicswe.eyeandsickle.client.ui.calc.WordSize.ofBits(Integer.parseInt(bits.trim()));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    /**
     * The answer, in every base at once — the same four rows the window shows.
     *
     * <p>All four rather than the one the input was written in, because the whole claim the tool
     * makes is that they are one value. A command that answered in the base you asked in would be a
     * base converter, and conversion is the part nobody needs help with.
     */
    private static List<String> calcLines(io.github.stoicswe.eyeandsickle.client.ui.calc.Calculator calc) {
        List<String> out = new ArrayList<>();
        for (var radix : io.github.stoicswe.eyeandsickle.client.ui.calc.Radix.values()) {
            out.add(pad(radix.label(), 6) + calc.row(radix));
        }
        out.add("");
        out.add(calc.word().bits() + " bits, " + (calc.signed() ? "signed" : "unsigned")
                + "   set bits " + calc.setBits()
                + "   bytes BE " + calc.bigEndian()
                + "   LE " + calc.littleEndian());
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static long countFlag(Command.Invocation inv, long fallback) {
        return inv.stage()
                .flag("n")
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Long.parseLong(s.trim());
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, Math.max(0, width - 1)) + " ";
        }
        return s + " ".repeat(width - s.length());
    }

    /** Right-aligned in a fixed column, so a table of numbers reads down rather than across. */
    private static String right(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        return " ".repeat(width - s.length()) + s;
    }

    private static String signed(java.math.BigInteger wei) {
        return (wei.signum() >= 0 ? "+" : "") + Ethecoin.format(wei);
    }

    /** Exposed so `help` and the palette can describe the catalogue without running anything. */
    public static Map<String, String> synopses(Shell.CommandRegistry registry) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Command c : registry.commands()) {
            out.put(c.name(), c.synopsis());
        }
        return out;
    }

    /**
     * What `mine` prints with no argument: the chain, this rig's place in it, and what has landed.
     *
     * <h2>⚠ No progress figure, and the readout says why</h2>
     *
     * Mining is memoryless — every hash is an independent trial, so a rig four hours into a block is
     * no closer than one that started a second ago. The line about being overdue is there because
     * players will otherwise infer the opposite from a long dry spell, and the inference is exactly
     * the gambler's fallacy. Naming it is cheaper than letting the interface imply it.
     */
    private static List<String> miningReport(GameSession session) {
        MiningSnapshot m = session.miningChain();
        List<String> out = new ArrayList<>();
        boolean solo = m.mode() == MiningMode.SOLO;

        out.add("chain     height " + m.height()
                + "  difficulty " + String.format(Locale.ROOT, "%.2f", m.difficulty())
                + "  retarget in " + m.blocksUntilRetarget() + " blocks");
        out.add("network   " + hashes(m.networkHashrate()) + "  (one block every 10 min, by design)");
        if (!m.active()) {
            out.add("");
            out.add("this rig is not mining. `mine --allocate=<cycles>` to start.");
            return out;
        }

        out.add("this rig  " + hashes(m.hashrate()) + " from " + m.cycles() + " cycles"
                + String.format(
                        Locale.ROOT,
                        "  (%.2f%% of the chain)",
                        100.0d * m.hashrate() / Math.max(1L, m.networkHashrate())));
        out.add("");
        out.add("mode      "
                + (solo
                        ? "SOLO   no fee, no floor"
                        : "POOLED   " + m.pool().name() + "  " + m.pool().scheme() + ", fee "
                                + m.pool().feeText() + ", " + m.pool().shareText() + " of the chain"));
        // ⚠ Three words, because the payout EVENT is a different thing in each. Solo is paid a
        // block; PPS is paid per share; PPLNS is paid a cut of a block the POOL found, which is
        // neither. Calling all three "share" would undo the distinction mining-pool(7) teaches.
        String unit = solo ? "block" : m.pool().scheme() == PoolScheme.PPLNS ? "payout" : "share";
        out.add("pays      " + Ethecoin.format(m.payoutWei()) + " per " + unit + ", about one every "
                + duration(m.expectedPayoutSeconds()));
        out.add("expected  " + Ethecoin.format(m.expectedWeiPerHour()) + "/hr");
        out.add("odds      " + String.format(Locale.ROOT, "%.0f%%", 100 * m.chanceWithin(3600))
                + " of at least one in the next hour, "
                + String.format(Locale.ROOT, "%.0f%%", 100 * m.chanceWithin(8 * 3600)) + " in eight");
        out.add("");
        out.add("found     " + m.lifetimePayouts() + " " + unit + (m.lifetimePayouts() == 1 ? "" : "s") + ", "
                + Ethecoin.format(m.lifetimeWei()) + " all told");
        if (m.secondsSinceLastPayout() >= 0) {
            out.add("last      " + duration(m.secondsSinceLastPayout()) + " ago");
        } else {
            out.add("last      nothing yet");
        }
        if (!solo && m.pendingWei().signum() > 0) {
            // The pool's unpaid balance. Real dashboards show it, and without it a player watching a
            // static balance between settlements has no way to tell holding from broken.
            out.add("unpaid    " + Ethecoin.format(m.pendingWei()) + " on the pool's books, settles in "
                    + m.secondsUntilSettle() + "s");
        }
        if (solo) {
            // The one line that has to be there. A four-hour dry spell reads as "due", and it is not.
            out.add("");
            out.add("a long gap does not make the next block likelier. Every hash is an independent");
            out.add("try against the same target, so nothing accumulates and nothing is owed.");
        }
        return out;
    }

    /** The `-n` form: what switching would mean, priced, before it happens. */
    private static List<String> modePreview(GameSession session, MiningMode mode) {
        MiningSnapshot m = session.miningChain();
        if (mode == m.mode()) {
            return List.of("already mining " + mode.name().toLowerCase(Locale.ROOT));
        }
        List<String> out = new ArrayList<>();
        out.add("would switch to " + mode.name().toLowerCase(Locale.ROOT));
        if (mode == MiningMode.SOLO) {
            out.add("solo pays the whole block subsidy, keeps the pool's fee, and can pay nothing");
            out.add("  for a very long time. Expected income is the same; the variance is not.");
        } else {
            out.add("pooled pays a fixed amount per accepted share whether or not the pool finds a");
            out.add("  block. The fee is what the pool charges for carrying that risk.");
        }
        out.add("switching costs nothing and forfeits nothing — there is no progress to lose");
        return out;
    }

    /** A hashrate, in the units a mining readout uses. */
    private static String hashes(long perSecond) {
        String[] units = {"H/s", "kH/s", "MH/s", "GH/s", "TH/s"};
        double value = perSecond;
        int unit = 0;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
    }

    private static String duration(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return "never";
        }
        long total = Math.round(seconds);
        if (total < 90) {
            return total + "s";
        }
        if (total < 5400) {
            return Math.round(total / 60.0d) + "m";
        }
        return String.format(Locale.ROOT, "%.1fh", total / 3600.0d);
    }

    /**
     * A projection's ETA: the countdown while it holds, the distribution once it does not.
     *
     * <p>Same rule the LEDGER panel's strip draws, and same reason it does not say "overdue" — an
     * exponential wait runs past its own mean about 37% of the time, so being past the estimate is
     * the ordinary case. {@code ChainMempool} carries the argument at the type level.
     *
     * <p>⚠ Elapsed is reconstructed from the ETA rather than taken from
     * {@code secondsSinceLastBlock}, so the percentile is computed against the same instant the
     * countdown is — the shell renders one snapshot, but the two figures still have to agree.
     */
    private static String eta(ChainMempool.ProjectedBlock p, double meanBlockSeconds) {
        long left = p.etaAt() == null
                ? 0L
                : java.time.Duration.between(java.time.Instant.now(), p.etaAt()).toSeconds();
        if (left > 0) {
            return "~" + clock(left);
        }
        double elapsed = p.expectedSeconds(meanBlockSeconds) - left;
        return "long, >" + Math.round(p.waitPercentile(elapsed, meanBlockSeconds) * 100) + "%";
    }

    /**
     * {@code M:SS}, or {@code H:MM:SS} past an hour — the same format {@code Ui.clock} draws.
     *
     * <p>⚠ Deliberately a second copy rather than a call into {@code client.ui}. This package holds
     * no JavaFX import at all, which is what lets every command be exercised without starting the
     * toolkit; {@code Ui} builds {@code Label}s, so importing it for a time format would trade that
     * away. {@link #money} and {@link #duration} are duplicated across the same boundary for the
     * same reason.
     */
    private static String clock(long seconds) {
        long total = Math.max(0, seconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long rest = total % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }
}
