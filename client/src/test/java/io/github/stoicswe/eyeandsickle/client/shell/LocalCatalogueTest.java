package io.github.stoicswe.eyeandsickle.client.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The terminal's right-click menu lists every local command, and invents none.
 *
 * <h2>Why this is generated rather than written</h2>
 *
 * {@code NodeCommands} can afford a hand-written catalogue because it <em>is</em> the catalogue — the
 * same list the menu offers is the one its parser reads, so a flag that is offered runs. The local
 * shell has no such property: its commands are registered as {@link Command} objects, and a second
 * hand-kept list beside them would be free to offer a verb the shell no longer has, or to miss one it
 * gained. That failure is silent — the menu inserts a line, the shell answers 127, and the game looks
 * like it lied.
 *
 * <p>So the catalogue derives from the registry, and these tests hold the two properties that makes
 * worth having: <b>everything registered is offered</b>, and <b>nothing offered was invented</b>.
 */
class LocalCatalogueTest {

    /** The registry the client actually builds — see {@code EyeAndSickleClient}. */
    private static Shell.CommandRegistry fullRegistry() {
        Shell.CommandRegistry registry = BuiltinCommands.registry();
        BreachCommands.register(registry);
        NetCommands.register(registry);
        return registry;
    }

    @Test
    @DisplayName("every registered command appears in the menu, exactly once")
    void everyCommandIsOffered() {
        Shell.CommandRegistry registry = fullRegistry();
        List<NodeCommands.NodeCommand> offered = LocalCatalogue.byGroup(registry).values().stream()
                .flatMap(List::stream)
                .toList();

        Set<String> registered = registry.commands().stream().map(Command::name).collect(Collectors.toSet());
        Set<String> inMenu =
                offered.stream().map(NodeCommands.NodeCommand::name).collect(Collectors.toSet());

        assertThat(inMenu)
                .as("the menu is generated from the registry, so it cannot be missing a verb")
                .isEqualTo(registered);
        assertThat(offered)
                .as("and cannot list one twice — aliases collapse onto their command")
                .hasSize(registered.size());
    }

    /**
     * ⚠ The restraint that keeps the menu honest — same property, measured against the declaration.
     *
     * <p>This used to assert the menu offered <b>only</b> the three universal flags, because a
     * {@link Command} had no way to say what else it took: {@code scan --thorough} and
     * {@code mine --pool=} were parsed inside their bodies, so any richer list here would have been
     * invented. {@link CommandSpec} removed the reason for that restraint without removing the
     * restraint itself — what may be offered is now exactly {universal} ∪ {declared}, and
     * {@code CommandSpecTest} separately proves every declared flag is one the body really parses.
     *
     * <p>⚠ Together those two are what the old assertion was standing in for. Weakening this one
     * without the other in place would let the menu invent flags again.
     */
    @Test
    @DisplayName("no flag is invented — every one is universal or declared by the command")
    void nothingIsInvented() {
        Set<String> universal = Set.of("--explain", "--dry-run", "--verbose");
        Shell.CommandRegistry registry = fullRegistry();

        for (Command command : registry.commands()) {
            Set<String> allowed = command.spec().options().stream()
                    .map(CommandSpec.Option::flagText)
                    .collect(Collectors.toCollection(java.util.HashSet::new));
            allowed.addAll(universal);

            NodeCommands.NodeCommand offered = LocalCatalogue.byGroup(registry).values().stream()
                    .flatMap(List::stream)
                    .filter(c -> c.name().equals(command.name()))
                    .findFirst()
                    .orElseThrow();

            assertThat(offered.options())
                    .as("%s must offer only flags it declares, plus the universal ones", command.name())
                    .allSatisfy(option -> assertThat(allowed).contains(option.name()));
            assertThat(offered.arguments())
                    .as("%s must claim exactly the arguments it declares", command.name())
                    .hasSameSizeAs(command.spec().arguments());
        }
    }

    /**
     * ⚠ The grouping is the declared SUBJECT, not the pipeline behaviour.
     *
     * <p>It used to be {@code isFilter}/{@code hasSideEffect}. Those are true statements about a
     * command and are still what {@link Shell} enforces — but they answer the wrong question for a
     * menu, which sorted {@code send}, {@code theme} and {@code mkdir} together under "Act". See
     * {@link CommandCategory}.
     */
    @Test
    @DisplayName("commands group under the category they declare")
    void groupsFollowTheDeclaredCategory() {
        Shell.CommandRegistry registry = fullRegistry();
        var catalogue = LocalCatalogue.byGroup(registry);

        for (Command command : registry.commands()) {
            String heading = io.github.stoicswe.eyeandsickle.client.i18n.Messages.load("commands")
                    .get(command.category().key());
            assertThat(catalogue.getOrDefault(heading, List.of()))
                    .as("%s declares %s", command.name(), command.category())
                    .anySatisfy(c -> assertThat(c.name()).isEqualTo(command.name()));
        }

        // `grep` is text handling and `scan` inspects the rig. If those two ever land in the same
        // drawer the categories have stopped meaning anything.
        assertThat(registry.find("grep").orElseThrow().category())
                .isNotEqualTo(registry.find("scan").orElseThrow().category());
    }

    @Test
    @DisplayName("an empty group is dropped rather than rendered as a blank submenu")
    void emptyGroupsAreDropped() {
        // A submenu with nothing in it reads as a feature that failed to load, not as a category
        // that happens to be empty.
        assertThat(LocalCatalogue.byGroup(fullRegistry()).values())
                .allSatisfy(commands -> assertThat(commands).isNotEmpty());
    }
}
