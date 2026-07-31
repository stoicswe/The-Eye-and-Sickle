package io.github.stoicswe.eyeandsickle.client.shell;

import java.util.ArrayList;
import java.util.List;

/**
 * The one way a command is declared.
 *
 * <h2>Why this exists</h2>
 *
 * There were four. {@code BuiltinCommands} had {@code source}/{@code filter}/{@code action} helpers,
 * {@code NetCommands} and {@code BreachCommands} each had a private {@code Verb} record with the same
 * six components in the same order, and {@code ClientCommands} had a {@code Simple} record with five.
 * Four shapes meant four places to add anything a command needs to carry — and the thing a command
 * needed to carry was its {@link CommandSpec}.
 *
 * <p>That is the real argument for one shape, and it is not tidiness. A positional record with six
 * components is a declaration nobody can read at the call site: {@code new Verb("file", List.of(),
 * "…", true, List.of(…), inv -> …)} says which of those booleans is {@code hasSideEffect} only if you
 * remember. A builder names every part, and — the part that matters here — lets a command declare its
 * <b>flags beside the body that parses them</b>, which is the only arrangement where the two cannot
 * drift apart.
 *
 * <h2>⚠ Declaring a flag is a claim about the body, checked at build time</h2>
 *
 * {@code CommandSpecTest} reads these declarations and the sources they sit in, and holds both
 * directions: a declared flag must be one the body actually reads, and a flag the body reads must be
 * declared. The forward direction stops the terminal's command menu inserting options the parser
 * ignores; the reverse stops a new flag being added and silently never appearing in the menu or the
 * man page. Neither is enforceable while the declaration lives somewhere other than the command.
 *
 * <h2>⚠ Structure is code; prose is text</h2>
 *
 * {@link Builder#flag} and friends take a <b>message key</b>, never a sentence. Flag names themselves
 * are never translated — {@code --thorough} is {@code --thorough} in every locale, because the parser
 * has no other name for it, because real Unix does not localise flags, and because pillar C6 sells
 * skill that transfers to a real terminal. See {@code i18n.Messages}.
 */
public final class Commands {

    private Commands() {}

    /** A command whose body produces lines and changes nothing. May head a pipeline. */
    public static Builder read(String name) {
        return new Builder(name, false, false);
    }

    /** A command that may appear after a {@code |}. Filters never have side effects. */
    public static Builder filter(String name) {
        return new Builder(name, false, true);
    }

    /**
     * A command that changes something.
     *
     * <p>⚠ {@code hasSideEffect} is load-bearing rather than documentation — {@link Shell} refuses a
     * pipeline containing an action <em>before running any stage of it</em>. Declaring an action as a
     * read is how a half-applied pipeline gets to exist.
     */
    public static Builder act(String name) {
        return new Builder(name, true, false);
    }

    /** A body that produces lines; the exit status is {@code OK}. */
    public interface Lines {
        List<String> apply(Command.Invocation invocation);
    }

    /** A body that produces its own exit status — anything that can be refused. */
    public interface Runs {
        Command.Output apply(Command.Invocation invocation);
    }

    /** Names every part of a declaration, and ends at {@link #lines} or {@link #runs}. */
    public static final class Builder {

        private final String name;
        private final boolean sideEffect;
        private final boolean isFilter;
        private List<String> aliases = List.of();
        private int section = 1;
        private String synopsis = "";
        private List<String> helpLines = List.of();
        private CommandCategory category = CommandCategory.SHELL;
        private final List<CommandSpec.Option> options = new ArrayList<>();
        private final List<CommandSpec.Argument> arguments = new ArrayList<>();

        private Builder(String name, boolean sideEffect, boolean isFilter) {
            this.name = name;
            this.sideEffect = sideEffect;
            this.isFilter = isFilter;
        }

        /** Other names the registry answers to. */
        public Builder aliases(String... names) {
            this.aliases = List.of(names);
            return this;
        }

        /**
         * The man section this ships in — 1 for user commands, 8 for rig maintenance.
         *
         * <p>⚠ Purely the man section. Grouping in the terminal's command menu is
         * {@link #category(CommandCategory)} — the two used to be the same call, which meant a page
         * number and a menu drawer could not be chosen independently.
         */
        public Builder section(int section) {
            this.section = section;
            return this;
        }

        /**
         * Which heading this sits under in the terminal's command menu.
         *
         * <p>⚠ The subject a player would look under, not the pipeline behaviour — that is already
         * fixed by whether the declaration started at {@link Commands#read}, {@link Commands#filter}
         * or {@link Commands#act}, and the two are only the same thing by accident.
         */
        public Builder category(CommandCategory category) {
            this.category = category;
            return this;
        }

        /** One line, for {@code help} and the completion list. */
        public Builder synopsis(String synopsis) {
            this.synopsis = synopsis;
            return this;
        }

        /** The body of {@code -h}, between the synopsis and the universal-flag footer. */
        public Builder help(String... lines) {
            this.helpLines = List.of(lines);
            return this;
        }

        /** An option that is present or absent: {@code --thorough}, {@code -i}. */
        public Builder flag(String name, String key) {
            options.add(CommandSpec.Option.flag(name, key));
            return this;
        }

        /** An option taking a free value: {@code --name=home-relay}, {@code -n 20}. */
        public Builder value(String name, String key) {
            options.add(CommandSpec.Option.value(name, key));
            return this;
        }

        /**
         * An option taking one of a fixed set: {@code --fee=priority}.
         *
         * <p>⚠ The choices are <b>code</b>. {@code --fee=priority} is parsed by that literal, so a
         * translated choice would not match; only a choice's label may ever be localised.
         */
        public Builder choice(String name, String key, String... choices) {
            options.add(CommandSpec.Option.choice(name, key, choices));
            return this;
        }

        /** A positional argument the command refuses without. */
        public Builder arg(String name, String key) {
            arguments.add(CommandSpec.Argument.required(name, key));
            return this;
        }

        /** A positional argument that may be left off. */
        public Builder optionalArg(String name, String key) {
            arguments.add(CommandSpec.Argument.optional(name, key));
            return this;
        }

        /** Finishes the declaration with a body that produces lines. */
        public Command lines(Lines body) {
            return runs(invocation -> Command.Output.ok(body.apply(invocation)));
        }

        /** Finishes the declaration with a body that produces its own exit status. */
        public Command runs(Runs body) {
            return new Definition(
                    name,
                    aliases,
                    section,
                    synopsis,
                    sideEffect,
                    isFilter,
                    helpLines,
                    category,
                    new CommandSpec(options, arguments),
                    body);
        }
    }

    /**
     * The one {@link Command} implementation.
     *
     * <p>⚠ Package-private and reachable only through {@link Builder}. A second implementation is how
     * the four shapes happened, and the cost was that {@code spec()} had nowhere to live.
     */
    record Definition(
            String name,
            List<String> aliases,
            int section,
            String synopsis,
            boolean sideEffect,
            boolean isFilter,
            List<String> helpLines,
            CommandCategory category,
            CommandSpec spec,
            Runs body)
            implements Command {

        Definition {
            aliases = List.copyOf(aliases);
            helpLines = List.copyOf(helpLines);
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
