package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
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
        r.add(source("ps", List.of(), "Compute allocation by consumer — what is holding your rig.",
                inv -> {
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

        r.add(source("ss", List.of("netstat"), "Connection table — one row per known node.",
                inv -> {
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

        r.add(source("df", List.of(), "Storage tiers as mount points, with their exposure.",
                inv -> {
                    List<String> out = new ArrayList<>();
                    out.add(pad("MOUNT", 26) + pad("ITEMS", 7) + "EXPOSURE");
                    out.add(pad("/rig/storage/vault", 26)
                            + pad(String.valueOf(inv.session().items(StorageTier.VAULT).size()), 7)
                            + "safe");
                    out.add(pad("/rig/storage/standard", 26)
                            + pad(String.valueOf(inv.session().items(StorageTier.STANDARD_STORAGE).size()), 7)
                            + "exposed while online");
                    out.add(pad("/rig/storage/high", 26)
                            + pad(String.valueOf(inv.session().items(StorageTier.HIGH_HACKABLE_ZONE).size()), 7)
                            + "always exposed");
                    return out;
                }));

        r.add(source("ls", List.of(), "List what is in a place in the namespace.",
                inv -> {
                    String path = inv.stage().argument(0).orElse("/");
                    if (Glob.isGlob(path)) {
                        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
                        String pattern = path.substring(path.lastIndexOf('/') + 1);
                        List<String> matched = new ArrayList<>();
                        for (String entry : Namespace.list(inv.session(), dir)) {
                            String name = entry.split("\\s")[0];
                            if (Glob.matches(pattern, name.endsWith("/") ? name.substring(0, name.length() - 1) : name)) {
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

        r.add(source("ledger", List.of(), "Every movement of ethecoin, newest first.",
                inv -> {
                    List<String> out = new ArrayList<>();
                    out.add(pad("WHEN", 22) + pad("DELTA", 12) + pad("BALANCE", 12) + "WHAT");
                    for (GameSession.LedgerRow row : inv.session().ledger(200)) {
                        out.add(pad(row.at().toString(), 22)
                                + pad(signed(row.deltaMinorUnits()), 12)
                                + pad(money(row.balanceAfterMinorUnits()), 12)
                                + row.description());
                    }
                    if (out.size() == 1) {
                        out.add("(no entries yet)");
                    }
                    return out;
                }));

        r.add(source("log", List.of(), "What the rig has been doing. -p filters by severity.",
                inv -> {
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

        r.add(source("items", List.of(), "Everything you own, across all three tiers.",
                inv -> {
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
        r.add(filter("grep", "Keep only the lines that match. -i ignore case, -v invert, -E extended regex.",
                inv -> {
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

        r.add(filter("sort", "Reorder lines. -r reverses.",
                inv -> {
                    List<String> out = new ArrayList<>(inv.input());
                    out.sort(String::compareToIgnoreCase);
                    if (inv.stage().hasFlag("r")) {
                        java.util.Collections.reverse(out);
                    }
                    return out;
                }));

        r.add(filter("uniq", "Collapse runs of identical neighbouring lines. -c counts them.",
                inv -> {
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

        r.add(filter("head", "Show the first few lines and stop. -n sets how many.",
                inv -> inv.input().stream().limit(countFlag(inv, 10)).toList()));

        r.add(filter("tail", "Show the last few lines.",
                inv -> {
                    long n = countFlag(inv, 10);
                    int from = (int) Math.max(0, inv.input().size() - n);
                    return inv.input().subList(from, inv.input().size());
                }));

        r.add(filter("wc", "Count lines instead of showing them. -l is the only mode here.",
                inv -> List.of(String.valueOf(inv.input().size()))));

        r.add(filter("cut", "Keep chosen whitespace-separated columns. -f selects them, 1-based.",
                inv -> {
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
        r.add(action("mine", List.of(), "Commit cycles to self-mining. --allocate=N, or 0 to stop.",
                inv -> {
                    String value = inv.stage().flag("allocate").orElse(inv.stage().argument(0).orElse(""));
                    if (value.isBlank()) {
                        return Command.Output.usage("mine --allocate=<cycles>");
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
                                "rig total: " + inv.session().computeBudget().total().cycles()
                                        + "  currently available: "
                                        + inv.session().computeBudget().available().cycles());
                    }
                    return Command.Output.of(inv.session().allocateSelfMining(cycles));
                }));

        r.add(action("collect", List.of(), "Sweep deployed-miner yield into your balance.",
                inv -> Command.Output.of(inv.session().collect())));

        r.add(action("scan", List.of(), "Search your own rig for things hiding from routine listings.",
                inv -> {
                    String tier = inv.stage().hasFlag("thorough")
                            ? "thorough"
                            : inv.stage().hasFlag("full") ? "full" : "quick";
                    if (inv.stage().isDryRun()) {
                        long cost = switch (tier) {
                            case "thorough" -> 35;
                            case "full" -> 15;
                            default -> 5;
                        };
                        return Command.Output.ok(
                                "would run scan --" + tier,
                                "published cost: " + cost + " cycles",
                                "available: " + inv.session().computeBudget().available().cycles() + " cycles");
                    }
                    return Command.Output.of(inv.session().scan(tier));
                }));

        r.add(new SimpleCommand("mv", List.of(), 1, "Move an item between storage tiers.", true, false,
                inv -> {
                    String item = inv.stage().argument(0).orElse("");
                    String tier = inv.stage().argument(1).orElse("");
                    if (item.isBlank() || tier.isBlank()) {
                        return Command.Output.usage("mv <item> <vault|standard|high>");
                    }
                    StorageTier to = switch (tier.toLowerCase(Locale.ROOT)) {
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

        r.add(action("abort", List.of(), "Withdraw from the current operation. Always confirms first.",
                inv -> {
                    // 130 is 128 + 2, and signal 2 is SIGINT — what Ctrl-C sends. That is not a
                    // coincidence or a flavour number: it is what a real machine reports when you
                    // interrupt something, and exit-status(7) teaches exactly this.
                    //
                    // There is nothing to abort yet because the breach minigame is [PROPOSAL]
                    // (docs/design/05). Saying so beats reporting a successful abort of nothing.
                    return new Command.Output(
                            List.of("Nothing to abort — no operation is running.",
                                    "",
                                    "When there is one, this reports 130: that is 128 + 2, signal 2 is",
                                    "SIGINT, and SIGINT is what Ctrl-C sends. See exit-status(7)."),
                            ExitStatus.OK);
                }));

        // ---------------------------------------------------------------- information
        r.add(source("id", List.of("whoami"), "Who you are on this rig.",
                inv -> List.of(
                        "handle    " + inv.session().handle(),
                        "mode      " + inv.session().mode().label(),
                        "          " + inv.session().mode().explanation(),
                        "heat      " + inv.session().personalHeat(),
                        "balance   " + inv.session().balance())));

        r.add(source("verify", List.of(), "Check an item's provenance chain.",
                inv -> {
                    String item = inv.stage().argument(0).orElse("");
                    if (item.isBlank()) {
                        return List.of("verify <item>");
                    }
                    return inv.session().items(null).stream()
                            .filter(i -> i.itemId().equals(item) || i.displayName().equalsIgnoreCase(item))
                            .findFirst()
                            .map(i -> i.hasProvenance()
                                    ? List.of(i.displayName() + ": chain verified to genesis")
                                    // Honest rather than reassuring. A solo item has nobody to prove
                                    // anything to, and a chain signed by a key on the same disk would
                                    // prove only that the disk agreed with itself.
                                    : List.of(
                                            i.displayName() + ": no provenance chain.",
                                            "",
                                            "This is a solo game. Items here are not signed, because there is",
                                            "nobody to prove anything to and nothing to prove it against. A chain",
                                            "verifies what a set of keys attested — see provenance-chain(7)."))
                            .orElse(List.of("verify: no such item: " + item));
                }));

        return r;
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

    private static String money(long minorUnits) {
        return String.format(Locale.ROOT, "%d.%02d EC", minorUnits / 100, Math.abs(minorUnits % 100));
    }

    private static String signed(long minorUnits) {
        return (minorUnits >= 0 ? "+" : "") + money(minorUnits);
    }

    private interface Lines {
        List<String> apply(Command.Invocation invocation);
    }

    private interface Runs {
        Command.Output apply(Command.Invocation invocation);
    }

    private static Command source(String name, List<String> aliases, String synopsis, Lines body) {
        return new SimpleCommand(name, aliases, 1, synopsis, false, false,
                inv -> Command.Output.ok(body.apply(inv)));
    }

    private static Command filter(String name, String synopsis, Lines body) {
        return new SimpleCommand(name, List.of(), 1, synopsis, false, true,
                inv -> Command.Output.ok(body.apply(inv)));
    }

    private static Command action(String name, List<String> aliases, String synopsis, Runs body) {
        return new SimpleCommand(name, aliases, 1, synopsis, true, false, body);
    }

    /** The one implementation every built-in uses. Keeps the catalogue a data structure. */
    private static class SimpleCommand implements Command {

        private final String name;
        private final List<String> aliases;
        private final int section;
        private final String synopsis;
        private final boolean sideEffect;
        private final boolean isFilter;
        private final Runs body;

        SimpleCommand(String name, List<String> aliases, int section, String synopsis,
                boolean sideEffect, boolean isFilter, Runs body) {
            this.name = name;
            this.aliases = List.copyOf(aliases);
            this.section = section;
            this.synopsis = synopsis;
            this.sideEffect = sideEffect;
            this.isFilter = isFilter;
            this.body = body;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }

        @Override
        public int section() {
            return section;
        }

        @Override
        public String synopsis() {
            return synopsis;
        }

        @Override
        public boolean hasSideEffect() {
            return sideEffect;
        }

        @Override
        public boolean isFilter() {
            return isFilter;
        }

        @Override
        public Output run(Invocation invocation) {
            return body.apply(invocation);
        }
    }

    /** Exposed so `help` and the palette can describe the catalogue without running anything. */
    public static Map<String, String> synopses(Shell.CommandRegistry registry) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Command c : registry.commands()) {
            out.put(c.name(), c.synopsis());
        }
        return out;
    }
}
