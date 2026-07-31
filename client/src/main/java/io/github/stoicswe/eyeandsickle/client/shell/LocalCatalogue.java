package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.i18n.Messages;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rig's own commands, described the way the node shell's catalogue describes a machine's.
 *
 * <h2>⚠ Generated from the registry, never written beside it</h2>
 *
 * {@link NodeCommands} can afford a hand-written catalogue because it <em>is</em> the catalogue —
 * {@code NodeCommands.run} parses exactly what {@code NodeCommands.byGroup} offers, so a flag that
 * exists is offerable and a flag that is offered runs. The local shell has no such property: its
 * commands are registered as {@link Command} objects and a second hand-kept list beside them would
 * be free to describe a verb that no longer exists, or to miss one that does.
 *
 * <p>So this <b>derives</b> the menu from {@link Shell.CommandRegistry#commands()}. A command added
 * to the shell appears in the menu with no further edit, and one removed disappears — which is the
 * only arrangement where "all locally available commands" stays true a year from now.
 *
 * <h2>⚠ The options are the command's OWN, and that is new</h2>
 *
 * This used to offer three universal flags and nothing else, because a {@link Command} had no way to
 * say what it took: {@code scan --thorough} and {@code mine --pool=} were parsed inside their bodies
 * and documented only in a man page nothing checked. The restraint was correct at the time —
 * inventing a plausible option list would have produced a menu inserting flags the parser rejects,
 * which is worse than a short menu because the player would reasonably believe the game.
 *
 * <p>{@link CommandSpec} removed the reason for the restraint. A command now declares its options
 * beside the body that parses them and {@code CommandSpecTest} holds both directions, so what this
 * offers is exactly what the parser reads. The universal flags are still appended, because they are
 * genuinely universal.
 *
 * <h2>⚠ Grouped by declared SUBJECT, not by pipeline behaviour</h2>
 *
 * The grouping was {@code isFilter}/{@code hasSideEffect} — true statements about a command, and the
 * wrong question for a menu. See {@link CommandCategory}.
 */
public final class LocalCatalogue {

    private LocalCatalogue() {}

    /**
     * The universal flags, taken from what {@link Command} promises rather than retyped.
     *
     * <p>⚠ {@code -h} is deliberately absent from the builder even though every command has it: the
     * builder writes a line into the input rather than running it, and a player who assembles
     * {@code ps -h} and presses enter gets the help they could have got by typing two characters.
     * {@code --explain} is the one worth surfacing — it is the flag nobody discovers.
     */
    private static List<NodeCommands.CommandOption> universalOptions(Messages messages) {
        return List.of(
                NodeCommands.CommandOption.flag("--explain", messages.get("cmd.universal.explain")),
                NodeCommands.CommandOption.flag("--dry-run", messages.get("cmd.universal.dry-run")),
                NodeCommands.CommandOption.flag("--verbose", messages.get("cmd.universal.verbose")));
    }

    /** {@link CommandSpec.Kind} and {@link NodeCommands.OptionKind} are the same three shapes. */
    private static NodeCommands.OptionKind kindOf(CommandSpec.Kind kind) {
        return switch (kind) {
            case FLAG -> NodeCommands.OptionKind.FLAG;
            case CHOICE -> NodeCommands.OptionKind.CHOICE;
            case VALUE -> NodeCommands.OptionKind.VALUE;
        };
    }

    /** A declared option, with its sentence resolved and its name written the way the parser reads it. */
    private static NodeCommands.CommandOption render(CommandSpec.Option option, Messages messages) {
        return new NodeCommands.CommandOption(
                option.flagText(), messages.get(option.key()), kindOf(option.kind()), option.choices());
    }

    /**
     * A declared argument.
     *
     * <p>⚠ {@code suggestPaths} is false throughout. The node shell sets it for commands that take a
     * path on a machine it can list; the local builder has no equivalent completion to offer, and a
     * path dropdown that suggested nothing would read as a command with no valid arguments.
     */
    private static NodeCommands.CommandArgument render(CommandSpec.Argument argument, Messages messages) {
        return new NodeCommands.CommandArgument(
                argument.name(), messages.get(argument.key()), argument.required(), false);
    }

    /** Every registered command, grouped by declared category, in the shape the shell menu renders. */
    public static Map<String, List<NodeCommands.NodeCommand>> byGroup(Shell.CommandRegistry registry) {
        return byGroup(registry, Messages.load("commands"));
    }

    /** The same, in a chosen language. */
    public static Map<String, List<NodeCommands.NodeCommand>> byGroup(
            Shell.CommandRegistry registry, Messages messages) {

        // ⚠ An EnumMap, so the submenus appear in CommandCategory's declared order rather than in
        // whatever order the registry happened to yield. That order is a design decision — what you
        // look at, then what you do to it, then the deck — and a HashMap would discard it.
        Map<CommandCategory, List<NodeCommands.NodeCommand>> grouped = new EnumMap<>(CommandCategory.class);

        List<Command> commands = new ArrayList<>(registry.commands());
        commands.sort(Comparator.comparing(Command::name));
        for (Command command : commands) {
            List<NodeCommands.CommandOption> options = new ArrayList<>();
            for (CommandSpec.Option option : command.spec().options()) {
                options.add(render(option, messages));
            }
            options.addAll(universalOptions(messages));

            List<NodeCommands.CommandArgument> arguments = new ArrayList<>();
            for (CommandSpec.Argument argument : command.spec().arguments()) {
                arguments.add(render(argument, messages));
            }

            grouped.computeIfAbsent(command.category(), c -> new ArrayList<>())
                    .add(new NodeCommands.NodeCommand(
                            command.name(),
                            messages.get(command.category().key()),
                            command.synopsis(),
                            options,
                            arguments));
        }

        // A category nobody registered into is simply absent — an empty submenu reads as a feature
        // that failed to load rather than as a drawer with nothing in it.
        Map<String, List<NodeCommands.NodeCommand>> out = new LinkedHashMap<>();
        grouped.forEach((category, list) -> out.put(messages.get(category.key()), list));
        return out;
    }
}
