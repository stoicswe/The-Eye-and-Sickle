package io.github.stoicswe.eyeandsickle.client.shell;

/**
 * Which heading a command sits under in the terminal's right-click menu.
 *
 * <h2>Why declared rather than derived</h2>
 *
 * {@code LocalCatalogue} used to group on what a command <em>is</em> — read, filter, act — which is
 * real (it is what {@link Shell} refuses a pipeline on) but answers the wrong question. A player
 * opening the menu is looking for a <b>subject</b>: "the thing that shows me the network", "the thing
 * that moves money". Sorting fifty verbs into three boxes labelled by their pipeline behaviour puts
 * {@code send}, {@code theme} and {@code mkdir} together under "Act", which is a list to read rather
 * than a menu to navigate.
 *
 * <p>So the subject is declared and the pipeline behaviour stays where it belongs — on
 * {@code hasSideEffect}/{@code isFilter}, still load-bearing, still enforced. The two were only ever
 * the same thing by accident.
 *
 * <h2>⚠ A closed enum, not a string</h2>
 *
 * A free-text group is a typo away from a second menu with one command in it, and nothing would
 * report it. Being an enum also fixes the menu's order — {@link #values()} order is the order the
 * submenus appear, which is chosen here (what you look at, then what you do to it, then the deck)
 * rather than by whatever order the registry happened to yield.
 *
 * <h2>⚠ The label is a KEY</h2>
 *
 * Heading text is prose and translatable; the command and flag names inside it are not. See
 * {@code i18n.Messages} for the full argument.
 */
public enum CommandCategory {

    /** {@code ps}, {@code top}, {@code df}, {@code scan} — this rig and what is holding it. */
    RIG("cmd.cat.rig"),

    /** {@code ls}, {@code mkdir}, {@code items} — the namespace and what is in it. */
    FILES("cmd.cat.files"),

    /** {@code net}, {@code sweep}, {@code connect} — other machines and the way to them. */
    NETWORK("cmd.cat.network"),

    /** {@code targets}, {@code breach}, {@code probe} — the core minigame. */
    BREACH("cmd.cat.breach"),

    /** {@code ledger}, {@code send}, {@code mine} — ethecoin and the chain. */
    ECONOMY("cmd.cat.economy"),

    /** {@code grep}, {@code sort}, {@code head} — the things that go after a pipe. */
    TEXT("cmd.cat.text"),

    /** {@code theme}, {@code window}, {@code wallpaper} — how the deck looks and behaves. */
    DESK("cmd.cat.desk"),

    /** {@code help}, {@code history}, {@code calc} — the tools that are about using the shell. */
    SHELL("cmd.cat.shell");

    private final String key;

    CommandCategory(String key) {
        this.key = key;
    }

    /** The message key for this heading's label. */
    public String key() {
        return key;
    }
}
