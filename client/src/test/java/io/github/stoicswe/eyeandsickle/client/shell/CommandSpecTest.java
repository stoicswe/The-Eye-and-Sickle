package io.github.stoicswe.eyeandsickle.client.shell;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.i18n.Messages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The declared command schema is a claim about the parser, and this is what makes it one.
 *
 * <h2>Why both directions are checked</h2>
 *
 * A {@link CommandSpec} is read by the terminal's right-click command menu and by the help text.
 * Each direction fails differently, and both failures are silent:
 *
 * <ul>
 *   <li><b>Declared but not parsed.</b> The menu offers {@code --thorough}, the player picks it, the
 *       parser ignores it and the command runs as though it were never there. The game has told the
 *       player something false about itself — worse than an incomplete menu, because there is nothing
 *       on screen to suggest the answer was wrong.
 *   <li><b>Parsed but not declared.</b> Somebody adds a flag to a body. It works from the keyboard,
 *       never appears in the menu, and is undiscoverable by anyone who has not read the source. The
 *       menu quietly stops being "all locally available commands" and no test notices.
 * </ul>
 *
 * <h2>⚠ This reads the SOURCE rather than a built registry, and that is not laziness</h2>
 *
 * Two reasons, and the second is the load-bearing one.
 *
 * <ol>
 *   <li>There is no runtime way to ask a lambda which flags it inspects. Scanning the text is the
 *       only evidence available — and it is real evidence: {@code hasFlag("x")} and {@code flag("x")}
 *       are the two, and only two, ways {@code CommandLine.Stage} yields an option.
 *   <li>⚠ <b>{@code ClientCommands.register} takes the deck, the theme manager and the profile</b>,
 *       so a headless test cannot build it — and it holds three of the commands with flags. A test
 *       driven off {@code BuiltinCommands.registry()} silently checks nothing for that whole file
 *       while reporting success, which is the failure mode this class exists to prevent. Reading the
 *       four registries as text covers all of them the same way.
 * </ol>
 */
@DisplayName("the declared command schema")
class CommandSpecTest {

    private static final Path SOURCE = Path.of("src/main/java/io/github/stoicswe/eyeandsickle/client/shell");

    private static final Path BUNDLE =
            Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/i18n/commands_en.properties");

    /** The files that register commands. A new registry belongs in this list. */
    private static final List<String> REGISTRIES =
            List.of("BuiltinCommands.java", "NetCommands.java", "ClientCommands.java", "BreachCommands.java");

    /**
     * Flags {@link Command#flagNames} promises for everything, which no command declares.
     *
     * <p>⚠ {@code n} is here because {@code -n} is the universal dry-run — and {@code head} and
     * {@code tail} also read {@code -n} as a count. That collision is real, is what those two do in a
     * real shell, and is why this set only ever excuses an <em>undeclared</em> flag; it never rejects
     * a declared one.
     */
    private static final Set<String> UNIVERSAL = Set.of("h", "help", "explain", "n", "dry-run", "v", "verbose");

    /** The two ways a body gets at an option. */
    private static final Pattern READS = Pattern.compile("(?:hasFlag|flag)\\(\"([A-Za-z][\\w-]*)\"\\)");

    /** A declaration: {@code .flag("i", "cmd.grep.i")}, {@code .choice("fee", "cmd.send.fee", …)}. */
    private static final Pattern DECLARES_OPTION =
            Pattern.compile("\\.(?:flag|value|choice)\\(\"([\\w-]+)\",\\s*\"([\\w.-]+)\"");

    /** {@code .arg("address", "cmd.send.arg.address")} and its optional twin. */
    private static final Pattern DECLARES_ARGUMENT =
            Pattern.compile("\\.(?:arg|optionalArg)\\(\"([\\w-]+)\",\\s*\"([\\w.-]+)\"");

    private static String source(String file) throws IOException {
        return Files.readString(SOURCE.resolve(file), StandardCharsets.UTF_8);
    }

    private static Set<String> matches(Pattern pattern, String text, int group) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            out.add(m.group(group));
        }
        return out;
    }

    /** A key resolved straight from Java: {@code messages.get("cmd.universal.explain")}. */
    private static final Pattern RESOLVES = Pattern.compile("messages\\.get\\(\"([\\w.-]+)\"\\)");

    /**
     * Every message key anything actually asks for.
     *
     * <p>Three sources, because a key reaches the bundle three ways: declared on an option, declared
     * on an argument, or looked up directly by the code that renders the menu. Leaving the third out
     * made the category headings and the universal flags look dead.
     */
    private static Set<String> declaredKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String file : REGISTRIES) {
            String text = source(file);
            keys.addAll(matches(DECLARES_OPTION, text, 2));
            keys.addAll(matches(DECLARES_ARGUMENT, text, 2));
        }
        keys.addAll(matches(RESOLVES, source("LocalCatalogue.java"), 1));
        for (CommandCategory category : CommandCategory.values()) {
            keys.add(category.key());
        }
        return keys;
    }

    @Nested
    @DisplayName("a declared flag is one the body really parses")
    class Forward {

        @Test
        @DisplayName("every declared option is read somewhere in its own registry")
        void declaredOptionsAreParsed() throws IOException {
            List<String> wrong = new ArrayList<>();
            for (String file : REGISTRIES) {
                String text = source(file);
                for (String flag : matches(DECLARES_OPTION, text, 1)) {
                    if (!text.contains("hasFlag(\"" + flag + "\")") && !text.contains("flag(\"" + flag + "\")")) {
                        wrong.add(file + " declares '" + flag + "' and never reads it");
                    }
                }
            }
            assertThat(wrong).as("declared flags no parser reads").isEmpty();
        }
    }

    @Nested
    @DisplayName("a parsed flag is one the command declared")
    class Reverse {

        @Test
        @DisplayName("no registry reads a flag that nothing in it declares")
        void parsedFlagsAreDeclared() throws IOException {
            List<String> undeclared = new ArrayList<>();
            for (String file : REGISTRIES) {
                String text = source(file);
                // ⚠ Pooled per FILE, not per command. A flag is read inside a lambda and there is no
                // reliable textual way to say which lambda a line sits in — attributing by "nearest
                // declaration above" mis-assigned four flags to helper methods when this was written.
                Set<String> declared = new LinkedHashSet<>(UNIVERSAL);
                declared.addAll(matches(DECLARES_OPTION, text, 1));

                Set<String> read = new TreeSet<>(matches(READS, text, 1));
                read.removeAll(declared);
                for (String flag : read) {
                    undeclared.add(file + " parses '" + flag + "' and no command in it declares it");
                }
            }
            assertThat(undeclared)
                    .as("flags the menu and the help text will never mention")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("the prose exists and is reachable")
    class Text {

        @Test
        @DisplayName("every message key a spec names resolves in the English bundle")
        void everyKeyResolves() throws IOException {
            Messages messages = Messages.load("commands");
            assertThat(messages.problems())
                    .as("the English bundle is a packaging fault if absent")
                    .isEmpty();

            List<String> missing = new ArrayList<>();
            for (String key : declaredKeys()) {
                if (!messages.has(key)) {
                    missing.add(key);
                }
            }
            assertThat(missing)
                    .as("keys that would render as themselves on screen")
                    .isEmpty();
        }

        @Test
        @DisplayName("the English bundle carries no key nothing asks for")
        void noDeadKeys() throws IOException {
            Set<String> used = declaredKeys();
            Set<String> dead = new TreeSet<>();
            for (String line : Files.readAllLines(BUNDLE, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                String key = trimmed.substring(0, trimmed.indexOf('=')).trim();
                if (!used.contains(key)) {
                    dead.add(key);
                }
            }
            // A dead key is a translator's wasted afternoon: somebody renders a sentence into six
            // languages for an option that no longer exists, and nothing anywhere says so.
            assertThat(dead).as("keys no command references").isEmpty();
        }
    }

    @Nested
    @DisplayName("the shape of a declaration")
    class Shape {

        /** The registry a headless test CAN build — enough to prove the spec survives to run time. */
        private List<Command> built() {
            Shell.CommandRegistry registry = BuiltinCommands.registry();
            NetCommands.register(registry);
            BreachCommands.register(registry);
            return List.copyOf(registry.commands());
        }

        @Test
        @DisplayName("the declarations reach the built registry")
        void specsSurviveToRuntime() {
            List<Command> commands = built();
            assertThat(commands).isNotEmpty();
            // Not an assertion about a particular flag — that is the source check's job. This asks
            // the one question source scanning cannot: did the builder actually carry the spec onto
            // the Command, or is spec() still returning NONE for everything?
            assertThat(commands.stream().anyMatch(c -> !c.spec().isEmpty()))
                    .as("some built command carries a non-empty spec")
                    .isTrue();

            Command grep = commands.stream()
                    .filter(c -> c.name().equals("grep"))
                    .findFirst()
                    .orElseThrow();
            assertThat(grep.spec().options())
                    .extracting(CommandSpec.Option::flagText)
                    .contains("-i", "-v");
            assertThat(grep.spec().arguments())
                    .extracting(CommandSpec.Argument::name)
                    .contains("pattern");
        }

        @Test
        @DisplayName("required arguments come before optional ones")
        void requiredComeFirst() {
            List<String> wrong = new ArrayList<>();
            for (Command command : built()) {
                boolean seenOptional = false;
                for (CommandSpec.Argument argument : command.spec().arguments()) {
                    if (!argument.required()) {
                        seenOptional = true;
                    } else if (seenOptional) {
                        // Positionals are matched by index, so a required one after an optional one
                        // is unreachable: nothing can tell whether the caller skipped the optional
                        // slot or the required one.
                        wrong.add(command.name() + " has required <" + argument.name() + "> after an optional");
                    }
                }
            }
            assertThat(wrong).isEmpty();
        }

        @Test
        @DisplayName("every command declares a category rather than defaulting into one")
        void everyCommandIsFiled() throws IOException {
            // ⚠ Command.category() defaults to SHELL so an undeclared command is still findable —
            // which is the right default and also means a missing declaration is INVISIBLE. The
            // menu would show it in the wrong drawer and nothing would say so. Checked at the
            // declaration site, because that is the only place the omission is legible.
            Pattern declaration =
                    Pattern.compile("Commands\\.(?:read|act|filter)\\(\"([a-z-]+)\"\\)\\s*\\.category\\(");
            List<String> unfiled = new ArrayList<>();
            for (String file : REGISTRIES) {
                String text = source(file);
                Set<String> filed = matches(declaration, text, 1);
                for (String name :
                        matches(Pattern.compile("Commands\\.(?:read|act|filter)\\(\"([a-z-]+)\"\\)"), text, 1)) {
                    if (!filed.contains(name)) {
                        unfiled.add(file + ": " + name + " declares no category");
                    }
                }
            }
            assertThat(unfiled).isEmpty();
        }

        @Test
        @DisplayName("every category label has prose")
        void everyCategoryHasALabel() {
            Messages messages = Messages.load("commands");
            for (CommandCategory category : CommandCategory.values()) {
                assertThat(messages.has(category.key()))
                        .as("%s -> %s", category, category.key())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("a short flag is one character and a long flag is more")
        void flagTextFollowsTheParser() {
            for (Command command : built()) {
                for (CommandSpec.Option option : command.spec().options()) {
                    assertThat(option.flagText())
                            .as("%s %s", command.name(), option.name())
                            .isEqualTo((option.name().length() == 1 ? "-" : "--") + option.name());
                }
            }
        }
    }
}
