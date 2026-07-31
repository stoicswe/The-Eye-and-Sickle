package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import java.util.List;

/**
 * One verb in the closed catalogue.
 *
 * <p>{@link #hasSideEffect()} is load-bearing rather than documentation: {@link Shell} uses it to
 * refuse a pipeline containing an action before running any stage of it ({@code docs/client/04}
 * §3.7). A command that changes state and reports otherwise would let a half-applied pipeline exist,
 * which is the one thing pillar C4 says the client must never produce.
 */
public interface Command {

    String name();

    default List<String> aliases() {
        return List.of();
    }

    /** The man section this command's page ships in — 1 for user commands, 8 for rig maintenance. */
    default int section() {
        return 1;
    }

    /** One line, for `help` and the completion list. */
    String synopsis();

    /** Whether this changes anything. Sources and filters are false; everything else is true. */
    boolean hasSideEffect();

    /** Whether this may appear after a `|`. Only filters may. */
    default boolean isFilter() {
        return false;
    }

    /**
     * What this command takes, as data.
     *
     * <h2>⚠ Default is {@link CommandSpec#NONE}, and that is honest rather than lazy</h2>
     *
     * A command that has not declared its options says so, and the menu offers it the universal
     * flags alone. The alternative — guessing from {@code flagNames()}, which returns the universal
     * set for everything — would produce a menu confidently offering flags no parser reads. An
     * undeclared command is a gap somebody can fill; a wrongly-declared one is a lie the player has
     * no way to catch.
     */
    default CommandSpec spec() {
        return CommandSpec.NONE;
    }

    /**
     * Which heading this sits under in the terminal's command menu.
     *
     * <p>⚠ The subject, not the pipeline behaviour. {@link #hasSideEffect()} and {@link #isFilter()}
     * remain what {@link Shell} enforces; this is what a player navigates. Defaulting to
     * {@link CommandCategory#SHELL} rather than guessing keeps an undeclared command visible — a
     * command in the wrong drawer is findable, one in no drawer is gone.
     */
    default CommandCategory category() {
        return CommandCategory.SHELL;
    }

    default List<String> flagNames() {
        return List.of("-h", "--help", "--explain", "-n", "--dry-run", "-v", "--verbose", "--");
    }

    /** `-h` output: what it takes. */
    default List<String> help() {
        return List.of(synopsis(), "", "Universal flags: -h  --explain  -n/--dry-run  -v/--verbose  --");
    }

    /** `--explain` output: what it does, without doing it. */
    default List<String> explain() {
        return help();
    }

    Output run(Invocation invocation);

    /** What a command was given, plus whatever the previous pipeline stage produced. */
    record Invocation(GameSession session, CommandLine.Stage stage, List<String> input, boolean piped) {

        public Invocation {
            input = List.copyOf(input);
        }
    }

    /** What a command produced. */
    record Output(List<String> lines, int status) {

        public Output {
            lines = List.copyOf(lines);
        }

        public static Output ok(List<String> lines) {
            return new Output(lines, ExitStatus.OK);
        }

        public static Output ok(String... lines) {
            return new Output(List.of(lines), ExitStatus.OK);
        }

        public static Output refused(String message) {
            return new Output(List.of(message), ExitStatus.REFUSED);
        }

        public static Output usage(String message) {
            return new Output(List.of(message), ExitStatus.USAGE);
        }

        /** Carries a session Outcome straight through, so `$?` matches what the rules decided. */
        public static Output of(GameSession.Outcome outcome) {
            return new Output(outcome.message().isBlank() ? List.of() : List.of(outcome.message()), outcome.status());
        }
    }
}
