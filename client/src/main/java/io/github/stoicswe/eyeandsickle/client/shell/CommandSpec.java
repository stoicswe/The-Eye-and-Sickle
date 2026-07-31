package io.github.stoicswe.eyeandsickle.client.shell;

import java.util.List;

/**
 * What a command takes, declared as data.
 *
 * <h2>Why this exists</h2>
 *
 * A {@link Command} used to declare its name and one line of synopsis, and its flags were read
 * inside its body — {@code inv.stage().hasFlag("thorough")}, buried a hundred lines from anything
 * that describes it. Two things followed. The terminal's command menu could not offer a command's
 * real options, because nothing published them; and the only description of a flag lived in a man
 * page that nothing checked against the parser, so a renamed flag left the page quietly wrong.
 *
 * <p>Declaring the shape here fixes both at the source: the menu is generated from the same list the
 * command is documented from, and a flag that is offered is a flag the command said it has.
 *
 * <h2>⚠ Structure is CODE. Prose is TEXT. The split is the whole point.</h2>
 *
 * {@link Option#name} and {@link Argument#name} are <b>never translated</b> — {@code --verbose} is
 * {@code --verbose} in every locale, because the parser has no other name for it and because real
 * Unix does not localise flags either. What is translatable is the sentence describing them, which
 * is why each carries a {@code key} rather than a string. See
 * {@code io.github.stoicswe.eyeandsickle.client.i18n.Messages} for the argument in full — the short
 * version is that localising a flag would take transferable skill away from exactly the players a
 * translation exists to serve.
 *
 * <h2>⚠ A spec describes what the body ACTUALLY parses</h2>
 *
 * This is a claim about the code, not a wish about it. An option here that the body never reads is a
 * menu entry that inserts a flag the parser ignores — worse than no entry, because the player has
 * been told something false by the game itself. {@code CommandSpecTest} holds the line by checking
 * every declared flag appears in its command's source.
 */
public record CommandSpec(List<Option> options, List<Argument> arguments) {

    /** A command that takes nothing beyond the universal flags. */
    public static final CommandSpec NONE = new CommandSpec(List.of(), List.of());

    public CommandSpec {
        options = List.copyOf(options);
        arguments = List.copyOf(arguments);
    }

    public static CommandSpec of(Option... options) {
        return new CommandSpec(List.of(options), List.of());
    }

    public static CommandSpec of(List<Option> options, List<Argument> arguments) {
        return new CommandSpec(options, arguments);
    }

    /** Whether there is anything to build — a builder with no controls is a dialog to dismiss. */
    public boolean isEmpty() {
        return options.isEmpty() && arguments.isEmpty();
    }

    /** What shape an option is on the command line. */
    public enum Kind {
        /** Present or absent: {@code --thorough}. */
        FLAG,
        /** Takes one of a fixed set: {@code --fee=priority}. */
        CHOICE,
        /** Takes a free value: {@code --name=home-relay}. */
        VALUE
    }

    /**
     * One option.
     *
     * @param name the flag as the parser knows it, without dashes — <b>never translated</b>
     * @param kind what shape it is
     * @param key the message key for its description, resolved against the current bundle
     * @param choices the allowed values for {@link Kind#CHOICE}; empty otherwise. ⚠ These are also
     *     code: {@code --fee=priority} is parsed by that literal, so a translated choice would not
     *     match. Their <em>labels</em> may be translated; their values may not
     */
    public record Option(String name, Kind kind, String key, List<String> choices) {

        public Option {
            choices = List.copyOf(choices);
        }

        public static Option flag(String name, String key) {
            return new Option(name, Kind.FLAG, key, List.of());
        }

        public static Option value(String name, String key) {
            return new Option(name, Kind.VALUE, key, List.of());
        }

        public static Option choice(String name, String key, String... choices) {
            return new Option(name, Kind.CHOICE, key, List.of(choices));
        }

        /**
         * {@code --fee}, or {@code -i} — how it is written on the line.
         *
         * <p>⚠ <b>Derived from the length, because that is what the parser actually does.</b>
         * {@code CommandLine} stores a flag under its token with the dashes stripped, so {@code -i}
         * and {@code --ignore} are two different keys and {@code hasFlag("h") || hasFlag("help")}
         * has to ask for both. Every one-character flag in this shell is therefore a short flag and
         * every longer one is a long flag — {@code grep -i}, {@code head -n}, {@code scan
         * --thorough}. Writing {@code --i} into the input would parse as a flag named {@code i}
         * <em>by accident</em> and would still be wrong on screen, since no Unix writes it that way.
         */
        public String flagText() {
            return (name.length() == 1 ? "-" : "--") + name;
        }
    }

    /**
     * One positional argument.
     *
     * @param name a short placeholder — {@code address}, {@code amount}. ⚠ Shown in usage, so it is
     *     the one structural string a translation MAY render: it is a description of a slot, not a
     *     token the parser matches. It is keyed for that reason
     * @param key the message key for its description
     * @param required whether the command refuses without it
     */
    public record Argument(String name, String key, boolean required) {

        public static Argument required(String name, String key) {
            return new Argument(name, key, true);
        }

        public static Argument optional(String name, String key) {
            return new Argument(name, key, false);
        }
    }
}
