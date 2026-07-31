package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.FsKind;
import io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The commands that run <em>on a machine</em> rather than on the client.
 *
 * <h2>Why a second catalogue and not more entries in {@link BuiltinCommands}</h2>
 *
 * Every command in {@code BuiltinCommands} answers a question about the player's own position —
 * {@code ps} is <em>your</em> compute, {@code ledger} is <em>your</em> money. These all take a
 * machine as an implied subject: {@code ls} means "list on the box this window is attached to". A
 * single registry would need every command to carry a target it mostly ignores, and the first time
 * somebody forgot, a command meant for a foreign host would quietly answer about the player's rig.
 * Two registries make the subject structural.
 *
 * <h2>⚠ Every command carries its own options as DATA</h2>
 *
 * {@link NodeCommand#options()} is not documentation. It is what the shell window's right-click
 * builder reads to offer a command's flags with the values they accept, and it is what
 * {@link #run} parses. One source: a flag that exists is offerable, and a flag that is offered
 * runs. The alternative — a menu written by hand beside a parser written by hand — is two lists that
 * agree on the day they are written and never again.
 *
 * <h2>Nothing here decides anything</h2>
 *
 * Listings, readability and refusals all come from {@link GameSession}. This class formats. When a
 * file cannot be read, the {@code readable} flag that says so was set by the rules (I14) — a shell
 * that worked out permissions itself would be answering the one question the authoritative side
 * exists to answer.
 */
public final class NodeCommands {

    private NodeCommands() {}

    // ── The shapes the builder and the parser share ───────────────────────────────────────────

    /** What kind of thing an option takes, which is what the builder needs to draw a control for. */
    public enum OptionKind {
        /** A bare switch: present or absent. A checkbox. */
        FLAG,

        /** Takes one of a fixed set. A dropdown — and the set is the whole reason this kind exists. */
        CHOICE,

        /** Takes free text. A field. */
        VALUE
    }

    /**
     * One option a command accepts.
     *
     * @param name the flag as typed, {@code -l} or {@code --human}
     * @param help one line, shown beside the control. Never a restatement of the flag letter
     * @param kind what it takes
     * @param choices for {@link OptionKind#CHOICE}, the accepted values, first one being the default
     */
    public record CommandOption(String name, String help, OptionKind kind, List<String> choices) {

        public static CommandOption flag(String name, String help) {
            return new CommandOption(name, help, OptionKind.FLAG, List.of());
        }

        public static CommandOption choice(String name, String help, String... choices) {
            return new CommandOption(name, help, OptionKind.CHOICE, List.of(choices));
        }
    }

    /**
     * A positional argument.
     *
     * @param name what it is called in the synopsis, e.g. {@code path}
     * @param help one line
     * @param required whether the command refuses without it
     * @param suggestPaths whether the builder should offer the current directory's entries. This is
     *     the difference between a builder that helps and a builder that is a second keyboard
     */
    public record CommandArgument(String name, String help, boolean required, boolean suggestPaths) {}

    /** One command: how it is described, what it accepts, and what it does. */
    public record NodeCommand(
            String name, String group, String synopsis, List<CommandOption> options, List<CommandArgument> arguments) {

        /** {@code ls [-l] [-a] [path]} — built from the data, so it cannot describe a flag that is gone. */
        public String usage() {
            StringBuilder out = new StringBuilder(name);
            for (CommandOption option : options) {
                out.append(" [").append(option.name());
                if (option.kind() != OptionKind.FLAG) {
                    out.append("=<")
                            .append(option.kind() == OptionKind.CHOICE ? String.join("|", option.choices()) : "value")
                            .append('>');
                }
                out.append(']');
            }
            for (CommandArgument argument : arguments) {
                out.append(argument.required() ? " <" : " [")
                        .append(argument.name())
                        .append(argument.required() ? '>' : ']');
            }
            return out.toString();
        }
    }

    /** What a command produced: lines to print, and whether the window should act on it. */
    public record Result(List<String> lines, boolean closeSession) {

        static Result of(List<String> lines) {
            return new Result(lines, false);
        }

        static Result of(String... lines) {
            return new Result(List.of(lines), false);
        }
    }

    // ── The catalogue ─────────────────────────────────────────────────────────────────────────

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault());

    /**
     * Every command, grouped the way the right-click menu shows them.
     *
     * <p>Grouped by what a player is trying to <em>do</em> — look around, read something, take
     * something — rather than alphabetically. A menu of eighteen names in alphabetical order is a
     * list you read; a menu in four groups of four is a list you use.
     */
    private static final List<NodeCommand> CATALOGUE = List.of(
            new NodeCommand(
                    "ls",
                    "Look around",
                    "List what is in a directory.",
                    List.of(
                            CommandOption.flag("-l", "Long form: mode, owner, size and date."),
                            CommandOption.flag("-a", "Include entries whose name starts with a dot."),
                            CommandOption.flag("-h", "Sizes in K/M rather than bytes.")),
                    List.of(new CommandArgument("path", "Where to list. Default is here.", false, true))),
            new NodeCommand(
                    "cd",
                    "Look around",
                    "Change directory.",
                    List.of(),
                    List.of(new CommandArgument("path", "Where to go. `..` goes up.", true, true))),
            new NodeCommand("pwd", "Look around", "Print the working directory.", List.of(), List.of()),
            new NodeCommand(
                    "find",
                    "Look around",
                    "Search this directory and below for a name.",
                    List.of(CommandOption.choice("-type", "Restrict to one kind.", "f", "d")),
                    List.of(new CommandArgument("name", "Text the name must contain.", true, false))),
            new NodeCommand(
                    "cat",
                    "Read",
                    "Print a file.",
                    List.of(),
                    List.of(new CommandArgument("file", "Which file.", true, true))),
            new NodeCommand(
                    "stat",
                    "Read",
                    "Everything known about one entry.",
                    List.of(),
                    List.of(new CommandArgument("file", "Which entry.", true, true))),
            new NodeCommand(
                    "head",
                    "Read",
                    "The first lines of a file.",
                    List.of(CommandOption.choice("-n", "How many lines.", "10", "5", "20", "40")),
                    List.of(new CommandArgument("file", "Which file.", true, true))),
            new NodeCommand("whoami", "The machine", "Which account this session is running as.", List.of(), List.of()),
            new NodeCommand("hostname", "The machine", "This machine's name.", List.of(), List.of()),
            new NodeCommand(
                    "uname",
                    "The machine",
                    "What this machine is running.",
                    List.of(CommandOption.flag("-a", "Everything, on one line.")),
                    List.of()),
            new NodeCommand(
                    "df",
                    "The machine",
                    "Mounted filesystems and what is on them.",
                    List.of(CommandOption.flag("-h", "Human-readable sizes.")),
                    List.of()),
            new NodeCommand(
                    "get",
                    "Take",
                    "Copy a file to your own rig. Upgrades arrive as packages Repac makes installable.",
                    List.of(),
                    List.of(
                            new CommandArgument("file", "Which file.", true, true),
                            new CommandArgument("into", "Where to put it. Default is ~/Downloads.", false, false))),
            new NodeCommand(
                    "rm",
                    "Take",
                    "Delete a file from your own rig. Not undoable, and it does not ask.",
                    List.of(),
                    List.of(new CommandArgument("file", "Which file.", true, true))),
            new NodeCommand("help", "Session", "List these commands.", List.of(), List.of()),
            new NodeCommand("exit", "Session", "Close this shell and hand its cycles back.", List.of(), List.of()));

    public static List<NodeCommand> catalogue() {
        return CATALOGUE;
    }

    /** The catalogue by group, in the order the groups were declared. What the menu is built from. */
    public static Map<String, List<NodeCommand>> byGroup() {
        Map<String, List<NodeCommand>> out = new LinkedHashMap<>();
        for (NodeCommand command : CATALOGUE) {
            out.computeIfAbsent(command.group(), key -> new ArrayList<>()).add(command);
        }
        return out;
    }

    public static Optional<NodeCommand> find(String name) {
        String wanted = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return CATALOGUE.stream().filter(c -> c.name().equals(wanted)).findFirst();
    }

    // ── Running one ───────────────────────────────────────────────────────────────────────────

    /**
     * Runs a line against a machine.
     *
     * <p>⚠ Parses with the shell's own {@link CommandLine}, not with a second splitter. The window
     * looks like a terminal and a player will type quoted arguments into it on the assumption that it
     * is one; a private {@code split(" ")} here would make {@code cat "my file"} behave differently
     * in this window than in the main terminal, which is exactly the kind of difference nobody
     * reports as a bug and everybody stops trusting.
     *
     * @param address the machine, or blank for the player's own rig
     * @param cwd where the session currently is
     */
    public static Result run(GameSession session, String address, String cwd, String line) {
        String raw = line == null ? "" : line.trim();
        if (raw.isEmpty()) {
            return Result.of(List.of());
        }
        CommandLine parsed;
        try {
            parsed = CommandLine.parse(raw);
        } catch (RuntimeException e) {
            return Result.of("sh: " + e.getMessage());
        }
        CommandLine.Stage stage = parsed.stages().getFirst();
        Optional<NodeCommand> command = find(stage.verb());
        if (command.isEmpty()) {
            // 127's wording, because it is the message every shell gives and the one a player will
            // meet again. `help` is named because a dead end that does not say where to go is a wall.
            return Result.of(stage.verb() + ": command not found — try `help`");
        }
        return switch (stage.verb()) {
            case "ls" -> ls(session, address, cwd, stage);
            case "cd" -> Result.of(List.of());
            case "pwd" -> Result.of(cwd);
            case "find" -> find(session, address, cwd, stage);
            case "cat", "head" -> cat(session, address, cwd, stage);
            case "stat" -> stat(session, address, cwd, stage);
            case "whoami" -> Result.of(userOf(session, address, cwd));
            case "hostname" -> Result.of(address.isBlank() ? "rig" : address);
            case "uname" -> uname(stage);
            case "df" -> df(session, address, stage);
            case "get" -> get(session, address, cwd, stage);
            case "rm" -> rm(session, address, cwd, stage);
            case "help" -> help();
            case "exit" -> new Result(List.of("logout"), true);
            default -> Result.of(stage.verb() + ": not implemented");
        };
    }

    private static Result ls(GameSession session, String address, String cwd, CommandLine.Stage stage) {
        String path = VirtualFs.resolve(cwd, stage.argument(0).orElse(""));
        List<FsEntry> entries = session.list(address, path);
        boolean all = stage.hasFlag("a");
        boolean human = stage.hasFlag("h");
        List<FsEntry> shown =
                entries.stream().filter(e -> all || !e.name().startsWith(".")).toList();
        if (shown.isEmpty()) {
            return Result.of(List.of());
        }
        List<String> out = new ArrayList<>();
        if (stage.hasFlag("l")) {
            out.add("total " + shown.size());
            for (FsEntry entry : shown) {
                out.add(String.format(
                        Locale.ROOT,
                        "%-11s %-10s %-10s %8s %s %s",
                        entry.mode(),
                        entry.owner(),
                        entry.group(),
                        size(entry, human),
                        STAMP.format(entry.modifiedAt()),
                        decorate(entry)));
            }
            return Result.of(out);
        }
        // Short form is one per line rather than columns. A column layout has to know the terminal
        // width, and this window's width is the player's to change at any moment.
        for (FsEntry entry : shown) {
            out.add(decorate(entry));
        }
        return Result.of(out);
    }

    /**
     * The trailing marker {@code ls -F} adds.
     *
     * <p>Real, and worth having for a reason beyond fidelity: neither surface that draws it has a
     * colour vocabulary of its own, so a slash is the only thing distinguishing a directory from a
     * file at a glance — the same argument {@code docs/design/ui-design-language.md} §4.4 makes for
     * the map.
     *
     * <p>⚠ <b>Real markers for real kinds, and two invented ones for the two game kinds.</b>
     * {@code /}, {@code *} and {@code @} are exactly what {@code ls -F} prints, so a player who
     * learns them here has learned them everywhere. A recovered fragment and an ethecoin cache are
     * not file types anybody will meet outside this game, so they get characters {@code ls} does not
     * define — which mirrors the real/game split {@link FsKind} already documents, rather than
     * putting a game meaning on a real marker.
     *
     * <p>A mount is {@code /} because a mount point <em>is</em> a directory you can enter; the Type
     * column is where the two are told apart.
     *
     * <p>Shared with the file manager on purpose. Two surfaces drawing the same tree with two
     * marker alphabets would be two things to learn for one fact.
     */
    public static String marker(FsEntry entry) {
        return switch (entry.kind()) {
            case DIRECTORY, MOUNT -> "/";
            case EXECUTABLE -> "*";
            case SYMLINK -> "@";
            // Not ls's. See above — these two are game kinds and must not borrow a real marker.
            case DOCUMENT -> "+";
            case LOOT -> "$";
            case FILE -> "";
        };
    }

    private static String decorate(FsEntry entry) {
        return entry.name() + marker(entry);
    }

    private static Result find(GameSession session, String address, String cwd, CommandLine.Stage stage) {
        String needle = stage.argument(0).orElse("").toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return Result.of("find: a name to look for is required");
        }
        String type = stage.flag("type").orElse("");
        List<String> hits = new ArrayList<>();
        // Bounded walk. An unbounded one on a generated tree is a promise about a shape this class
        // does not own; 400 is far more than any host has and small enough that a typo cannot hang
        // the window.
        walk(session, address, cwd, hits, needle, type, new java.util.HashSet<>(), 400);
        return hits.isEmpty() ? Result.of(List.of()) : Result.of(hits);
    }

    private static void walk(
            GameSession session,
            String address,
            String path,
            List<String> hits,
            String needle,
            String type,
            java.util.Set<String> seen,
            int budget) {
        if (hits.size() >= budget || !seen.add(path)) {
            return;
        }
        for (FsEntry entry : session.list(address, path)) {
            boolean kindOk = type.isBlank()
                    || (type.equals("d") && entry.directory())
                    || (type.equals("f") && !entry.directory());
            if (kindOk && entry.name().toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add(entry.path());
            }
            if (entry.directory()) {
                walk(session, address, entry.path(), hits, needle, type, seen, budget);
            }
        }
    }

    /**
     * Prints a file — or says, in the rules' terms, why it will not.
     *
     * <p>⚠ The contents are a <b>description</b>, not a body. Only two kinds of file in this game
     * have real text behind them, and {@code get} is what fetches one; everything else is a plausible
     * artefact whose job is to make a directory look like a directory. Printing invented log lines as
     * though they were readable data would be the client fabricating game content, so what comes back
     * instead names what the file is and how big it is.
     */
    private static Result cat(GameSession session, String address, String cwd, CommandLine.Stage stage) {
        Optional<FsEntry> entry = entry(session, address, cwd, stage.argument(0).orElse(""));
        if (entry.isEmpty()) {
            return Result.of("cat: " + stage.argument(0).orElse("") + ": No such file or directory");
        }
        FsEntry file = entry.get();
        if (file.directory()) {
            return Result.of("cat: " + file.name() + ": Is a directory");
        }
        // ⚠ The directory check stays ABOVE this — see FileManagerView.open. `cat` on a folder is
        // "Is a directory" and never a read, whatever the rules would say about the path.
        session.noteAccess(address, file.path());
        List<String> early = session.read(address, file.path());
        if (!early.isEmpty()) {
            return Result.of(early);
        }
        if (!file.readable()) {
            // 13 EACCES's own wording. A refusal that reads like the real one teaches the real one.
            return Result.of("cat: " + file.name() + ": Permission denied");
        }
        // ⚠ Real contents first, and they come from the RULES. The remote-access log is the file
        // this matters most for: a player reading it is investigating, and a shell that rendered a
        // plausible-looking log of its own invention would be lying on the one surface where a lie
        // does real damage.
        return switch (file.kind()) {
            case DOCUMENT ->
                Result.of(
                        "[ recovered fragment — " + file.sizeBytes() + " bytes ]",
                        "Use `get " + file.name() + "` to pull it back to your rig and read it in `recon`.");
            case LOOT ->
                Result.of(
                        "[ wallet — " + file.sizeBytes() + " minor units ]",
                        "Use `get " + file.name() + "` to take it.");
            default ->
                Result.of(
                        "[ " + file.sizeBytes() + " bytes, " + file.mode() + " ]",
                        "Nothing in this file is modelled. The two that are — recovered fragments and",
                        "wallets — say so when you `cat` them.");
        };
    }

    private static Result stat(GameSession session, String address, String cwd, CommandLine.Stage stage) {
        Optional<FsEntry> entry = entry(session, address, cwd, stage.argument(0).orElse(""));
        if (entry.isEmpty()) {
            return Result.of("stat: cannot statx '" + stage.argument(0).orElse("") + "': No such file or directory");
        }
        FsEntry file = entry.get();
        List<String> out = new ArrayList<>(List.of(
                "  File: " + file.path(),
                "  Size: " + file.sizeBytes() + "  Kind: " + file.kind().name().toLowerCase(Locale.ROOT),
                "Access: " + file.mode() + "  Uid: " + file.owner() + "  Gid: " + file.group(),
                "Modify: " + STAMP.format(file.modifiedAt()),
                "  Read: " + (file.readable() ? "yes" : "no")));
        // The same note the file manager's Get info shows. One source, two surfaces — a `stat` that
        // said less than a right-click would send players to the mouse to learn things.
        List<String> note = session.info(address, file.path());
        if (!note.isEmpty()) {
            out.add("");
            out.addAll(note);
        }
        return Result.of(out);
    }

    /**
     * Deletes a file from the rig.
     *
     * <h2>⚠ It does not ask, and that is correct here</h2>
     *
     * A real {@code rm} does not ask; the whole reason {@code rm -i} exists is that the default does
     * not. The file manager confirms because a click is cheap and easy to make by accident, and a
     * typed {@code rm mining-firmware.frm} is not. Making the terminal ask would also break the
     * habit the shell is teaching — this client's manual pages are about real commands, and one that
     * behaves differently from the real one teaches something false.
     *
     * <p>The refusal for somebody else's machine comes from the rules, not from here, so the terminal
     * and the file manager give the same answer for the same reason.
     */
    private static Result rm(GameSession session, String address, String cwd, CommandLine.Stage stage) {
        String named = stage.argument(0).orElse("");
        if (named.isBlank()) {
            // Real `rm`'s own wording. ⚠ This is also what `rm -rf /` reaches: the shell does not
            // know `-rf`, so the flag swallows the operand and nothing is named — which is a safe
            // outcome arrived at by accident, so it is spelled out here rather than left to chance.
            return Result.of("rm: missing operand");
        }
        Optional<FsEntry> entry = entry(session, address, cwd, named);
        if (entry.isEmpty()) {
            // ⚠ The ROOT resolves to no entry at all — `entry` finds a path by listing its PARENT,
            // and `/` has none. Without this, `rm /` reports "No such file or directory" about the
            // one directory that certainly exists, which reads as the filesystem being broken rather
            // than as the command being refused.
            String target = VirtualFs.resolve(cwd, named);
            if (!session.list(address, target).isEmpty() || "/".equals(target)) {
                return Result.of("rm: cannot remove '" + target + "': Is a directory");
            }
            return Result.of("rm: cannot remove '" + named + "': No such file or directory");
        }
        if (entry.get().directory()) {
            // ⚠ Real `rm`'s own wording, and the reason `rm -rf /` is safe here: it resolves to the
            // root, the root is a directory, and this refuses by name. There is no recursive delete
            // in this shell and there should not be — a filesystem generated from game state has no
            // tree to walk, and the one thing that would make such a command meaningful is the one
            // thing it must never do.
            return Result.of("rm: cannot remove '" + entry.get().path() + "': Is a directory");
        }
        return Result.of(session.delete(address, entry.get().path()).message());
    }

    private static Result uname(CommandLine.Stage stage) {
        // uOS is the game's own system (docs/client/03), and the kernel line is Linux's shape because
        // that is what the fiction says it is. It is NOT a claim to be Ubuntu.
        return stage.hasFlag("a") ? Result.of("uOS 4.2.0-19-generic #19-uOS SMP x86_64 GNU/Linux") : Result.of("uOS");
    }

    private static Result df(GameSession session, String address, CommandLine.Stage stage) {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "%-34s %-10s %s", "Mounted on", "Entries", "Exposure"));
        // ⚠ The tiers moved out of /mnt and into ~/.VaultStore on 2026-07-28, so this walks the
        // store rather than the mount table. They were never really mounts — nobody mounted them —
        // and a /mnt/vault sitting in an intruder's sidebar was a signpost to the one place that is
        // meant to be safe.
        String store = vaultStore(session, address);
        for (FsEntry tier : session.list(address, store)) {
            long count = session.list(address, tier.path()).size();
            out.add(String.format(Locale.ROOT, "%-34s %-10d %s", tier.path(), count, exposure(tier.name())));
        }
        for (FsEntry mount : session.list(address, "/mnt")) {
            long count = session.list(address, mount.path()).size();
            out.add(String.format(Locale.ROOT, "%-34s %-10d %s", mount.path(), count, "remote"));
        }
        if (out.size() == 1) {
            out.add("(nothing mounted)");
        }
        return Result.of(out);
    }

    /** {@code ~/.VaultStore} for whoever this session is running as. */
    private static String vaultStore(GameSession session, String address) {
        return session.list(address, io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.USERS).stream()
                .findFirst()
                .map(home -> home.path() + "/" + io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.VAULTSTORE)
                .orElse("/");
    }

    /** The one column a real {@code df} has no equivalent for — see {@code df(1)}'s CAVEATS. */
    private static String exposure(String tier) {
        return switch (tier) {
            case "vault" -> "safe — never exposed";
            case "standard" -> "exposed while you are online";
            case "hot" -> "always exposed, raidable offline";
            default -> "remote";
        };
    }

    /**
     * Takes something off the machine.
     *
     * <p>⚠ Delegates to {@link GameSession#download} rather than doing anything itself. Whether a
     * fragment comes back, whether it is partial and what it costs are rules questions
     * ({@code docs/design/07}); this command's whole contribution is that the player found the file
     * by looking rather than by pressing a button labelled DOWNLOAD.
     */
    private static Result get(GameSession session, String address, String cwd, CommandLine.Stage stage) {
        Optional<FsEntry> entry = entry(session, address, cwd, stage.argument(0).orElse(""));
        if (entry.isEmpty()) {
            return Result.of("get: " + stage.argument(0).orElse("") + ": No such file or directory");
        }
        FsEntry file = entry.get();
        // ⚠ The same call the file manager makes. `get` and the Download menu item must not be two
        // paths to the same act — the moment they are, one of them grows a rule the other lacks.
        // The shell's `get` takes an optional second argument for where to put it, which is the
        // same choice the file manager's Save-as submenu offers. Blank means the default.
        String destination = stage.argument(1).orElse("");
        GameSession.Outcome outcome = session.download(address, file, destination);
        return Result.of(outcome.message());
    }

    private static Result help() {
        List<String> out = new ArrayList<>();
        out.add("Commands on this machine. Right-click for a menu that fills in the options.");
        out.add("");
        byGroup().forEach((group, commands) -> {
            out.add(group.toUpperCase(Locale.ROOT));
            for (NodeCommand command : commands) {
                out.add("  " + pad(command.usage(), 30) + command.synopsis());
            }
            out.add("");
        });
        return Result.of(out);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private static Optional<FsEntry> entry(GameSession session, String address, String cwd, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String target = VirtualFs.resolve(cwd, name);
        return session.list(address, VirtualFs.parentOf(target)).stream()
                .filter(e -> e.path().equals(target))
                .findFirst();
    }

    private static String userOf(GameSession session, String address, String cwd) {
        // Read off the home directory the session is in rather than stored twice. The rules put the
        // session there; deriving it back is one fact, not two.
        String home = io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.USERS + "/";
        String path = VirtualFs.normalise(cwd);
        if (path.startsWith(home)) {
            String rest = path.substring(home.length());
            int slash = rest.indexOf('/');
            return slash < 0 ? rest : rest.substring(0, slash);
        }
        return address.isBlank() ? session.handle() : "root";
    }

    private static String size(FsEntry entry, boolean human) {
        long bytes = entry.sizeBytes();
        if (!human) {
            return String.valueOf(bytes);
        }
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "K";
        }
        return (bytes / (1024 * 1024)) + "M";
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text + " " : text + " ".repeat(width - text.length());
    }
}
