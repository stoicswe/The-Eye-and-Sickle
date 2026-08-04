package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a parsed command line against a {@link GameSession}.
 *
 * <h2>Pipelines compose queries. They never compose actions.</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.7 makes this a parser-level rule rather than
 * a convention: a pipeline containing a command with a side effect is <em>rejected</em>, with a
 * message naming the offending command. Two reasons. It keeps §3.1's safety boundary simple, and it
 * stops a partially-applied pipeline from ever existing — which matters under pillar C4, because a
 * half-executed action is precisely the state the client must never be in.
 *
 * <h2>Filters work on rendered text, exactly like real Unix</h2>
 *
 * {@code ps | grep miner} matches the word "miner" anywhere on the line, including in a column you
 * did not mean. That is a deliberate fidelity choice with a real cost, and the cost <em>is</em> the
 * lesson — it is why {@code awk}, {@code jq} and structured output exist, and {@code grep(1)}'s
 * CAVEATS says so.
 *
 * <h2>Exit status is the last command's</h2>
 *
 * As in a real shell without {@code pipefail}. If the left side fails and the right side succeeds,
 * the pipeline reports success and the failure is silently lost. {@code shell(7)} explains this and
 * mentions {@code set -o pipefail}, because the surprise is real and worth inoculating against.
 */
public final class Shell {

    /** ⚠ JUL — captured by {@code log/ClientLog} for the CLIENT LOGS tab. */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Shell.class.getName());

    private final GameSession session;
    private final CommandRegistry registry;
    private final List<String> history = new ArrayList<>();

    private int lastStatus = ExitStatus.OK;

    public Shell(GameSession session, CommandRegistry registry) {
        this.session = session;
        this.registry = registry;
    }

    public GameSession session() {
        return session;
    }

    public CommandRegistry registry() {
        return registry;
    }

    public List<String> history() {
        return List.copyOf(history);
    }

    public void seedHistory(List<String> entries) {
        history.clear();
        if (entries != null) {
            history.addAll(entries);
        }
    }

    /** The value the terminal shows as {@code $?}. */
    public int lastStatus() {
        return lastStatus;
    }

    /**
     * Parses and runs a line.
     *
     * <p>Never throws for anything a player can type. A parse failure, an unknown verb and a refusal
     * are all ordinary results with a status and a message, because they are all things the game is
     * expected to say.
     */
    public Result run(String line) {
        if (line == null || line.isBlank()) {
            return new Result(List.of(), lastStatus);
        }
        history.add(line);
        // ⚠ FINER, i.e. TRACE. A shell line is the highest-volume thing a player produces and the
        // most useful to have when reproducing a report — which is exactly the pair that trace
        // exists for: captured always, shown only when somebody has gone looking.
        LOG.log(java.util.logging.Level.FINER, "$ {0}", line);

        CommandLine parsed;
        try {
            parsed = CommandLine.parse(line);
        } catch (CommandLine.ParseException e) {
            return finish(new Result(List.of(e.getMessage()), e.status()));
        }

        // Reject an action inside a pipeline before running any of it.
        if (parsed.isPipeline()) {
            for (CommandLine.Stage stage : parsed.stages()) {
                Optional<Command> command = registry.find(stage.verb());
                if (command.isPresent() && command.get().hasSideEffect()) {
                    return finish(new Result(
                            List.of("`" + stage.verb() + "` changes something, and a pipeline may only "
                                    + "read. Run it on its own. See shell(7)."),
                            ExitStatus.USAGE));
                }
            }
        }

        List<String> stream = List.of();
        int status = ExitStatus.OK;

        for (int i = 0; i < parsed.stages().size(); i++) {
            CommandLine.Stage stage = parsed.stages().get(i);
            Optional<Command> found = registry.find(stage.verb());
            if (found.isEmpty()) {
                return finish(new Result(
                        List.of(stage.verb() + ": no such command. Try `help`, or `apropos <topic>`."),
                        ExitStatus.NO_SUCH_COMMAND));
            }
            Command command = found.get();

            // The five universal flags are handled here, once, rather than in every command —
            // which is what makes them universal rather than merely common (§3.4).
            if (stage.wantsHelp()) {
                stream = command.help();
                status = ExitStatus.OK;
                continue;
            }
            if (stage.wantsExplanation()) {
                stream = command.explain();
                status = ExitStatus.OK;
                continue;
            }

            Command.Invocation invocation = new Command.Invocation(session, stage, stream, i > 0);
            Command.Output output;
            try {
                output = command.run(invocation);
            } catch (RuntimeException e) {
                // A bug in a command must not take the terminal down with it. The player gets an
                // honest message rather than a frozen window.
                output = new Command.Output(
                        List.of(stage.verb() + ": internal error — "
                                + e.getClass().getSimpleName()),
                        ExitStatus.REFUSED);
            }
            stream = output.lines();
            status = output.status();
        }
        return finish(new Result(stream, status));
    }

    private Result finish(Result result) {
        // ⚠ The ONE exit point — every path through run() returns through here, including the parse
        // failure and the pipeline refusal above. Logging at the call sites instead would mean the
        // next `return` somebody adds is the one that goes unrecorded.
        if (result.status() != ExitStatus.OK) {
            LOG.log(
                    java.util.logging.Level.FINE,
                    "command exited {0}: {1}",
                    new Object[] {
                        result.status(), result.lines().isEmpty() ? "" : result.lines().getFirst()
                    });
        }
        lastStatus = result.status();
        return result;
    }

    /** Everything the terminal needs to render one submitted line. */
    public record Result(List<String> lines, int status) {
        public Result {
            lines = List.copyOf(lines);
        }

        public boolean succeeded() {
            return status == ExitStatus.OK;
        }
    }

    /**
     * Completion candidates for a partially typed line.
     *
     * <p>Position-aware, per §3.6: the first word completes against commands, a word beginning with
     * {@code -} completes against that command's flags, and anything else completes against the
     * namespace. Completion never executes, and it never reveals a node the player has not
     * discovered — completing an unscanned address would hand over, free, what recon is priced to
     * sell ({@code docs/design/07-recon-tools.md} §3).
     */
    public List<String> complete(String line) {
        String text = line == null ? "" : line;
        boolean atStart = !text.contains(" ");
        String prefix = text.isEmpty() ? "" : text.substring(text.lastIndexOf(' ') + 1);

        if (atStart) {
            return registry.names().stream()
                    .filter(n -> n.startsWith(prefix.toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        String verb = text.trim().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
        if (prefix.startsWith("-")) {
            return registry.find(verb).map(Command::flagNames).orElse(List.of()).stream()
                    .filter(f -> f.startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return Namespace.complete(session, prefix);
    }

    /** The command catalogue. Closed by construction — an unknown verb is exit 127, never a guess. */
    public static final class CommandRegistry {

        private final Map<String, Command> byName = new LinkedHashMap<>();

        public void add(Command command) {
            byName.put(command.name(), command);
            for (String alias : command.aliases()) {
                byName.put(alias, command);
            }
        }

        public Optional<Command> find(String verb) {
            return Optional.ofNullable(byName.get(verb));
        }

        public List<String> names() {
            return List.copyOf(byName.keySet());
        }

        /** Distinct commands, for `help`. Aliases collapse onto their command. */
        public List<Command> commands() {
            return byName.values().stream().distinct().toList();
        }
    }
}
