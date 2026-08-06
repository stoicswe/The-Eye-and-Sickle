package io.github.stoicswe.eyeandsickle.client.presence;

import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;

/**
 * Everything the client is ever allowed to tell Discord it is doing.
 *
 * <h2>⚠ A CLOSED SET OF CONSTANTS, NOT A FORMAT STRING — this is the whole privacy design</h2>
 *
 * Rich presence is broadcast to everyone on the player's friends list and relayed through a third
 * party's servers. The only implementation of that which is safe to ship is one where <b>there is no
 * mechanism by which game state could reach the wire</b> — not a careful set of call sites that
 * currently pass safe values, because a format string is one interpolation away from carrying a
 * handle, a balance or a target address, and nothing on screen would report it.
 *
 * <p>So the payload is built from this enum and a clock, and from nothing else. {@link
 * RichPresence#activity} takes a {@code PresenceState} and an {@code Instant}; it is not handed a
 * {@code GameSession} and could not read one. {@code PresenceLeakTest} drives every state with a
 * session whose handle, character id and rig name are distinctive probe strings and asserts that not
 * one of them appears in any frame — verified against a deliberately-leaking build first.
 *
 * <p>What this therefore never says, and must never be widened to say: the operator's handle, their
 * DID, their avatar, their balance, their faction or trader standing, the address or name of any
 * machine, any item, or anything at all about a breach in progress beyond that one is happening.
 *
 * <h2>⚠ These strings are NOT translated, and that is deliberate</h2>
 *
 * Every other player-facing caption in {@code view/} goes through {@code Views.t} and can be
 * overlaid by a translation. These do not, because <b>the reader is not the player</b> — it is
 * whoever is on their friends list, and the language the player set their own interface to says
 * nothing about what those people read. This is the same line {@code docs/client/04} draws between
 * structure and prose for command names: what is fixed is fixed for a reason outside the player's
 * locale.
 *
 * <h2>Why one line and not two</h2>
 *
 * Discord renders {@code details} above {@code state}. Only {@code details} is populated. A second
 * line would double the number of places a future edit could put something session-derived, in
 * exchange for saying the same thing at greater length.
 */
public enum PresenceState {

    /** Before a character is open — the login screen and the setup assistant. */
    MENU("In the main menu"),

    /**
     * A character is open and no tool window has focus.
     *
     * <p>Also what a window <em>closing</em> resolves to, since the desk does not say what gained
     * focus in the same breath. The focus event that follows overwrites it, and
     * {@link RichPresence#MIN_INTERVAL}'s coalescing means the transient is never transmitted.
     *
     * <p>⚠ And it is where <b>Settings</b> lands — see {@link #forWindow}.
     */
    DECK("On the deck"),

    // ⚠ Each line names an ACTIVITY, not a location. "At a terminal" and "At the market" were the
    // first draft and they answer the wrong question: presence exists to say what somebody is doing,
    // and a friends list reading "At the compiler" learns only which window is on top. The
    // constraint is that the verb must be one the tool can actually support — ASSEMBL says "Reading
    // a schematic" and not "Building a tool" precisely because the build mechanics are still AS-1
    // and unimplemented, and a presence line claiming an action the game cannot perform is the same
    // class of untruth as a term page stating a fact nobody checked.
    RIG("Counting cycles"),
    SECURITY("Sweeping the rig for parasites"),
    TERMINAL("Working a shell"),
    FILES("Digging through the filesystem"),
    VAULT("Sorting the vault"),
    LEDGER("Watching blocks land"),
    NETWORK("Mapping the network"),
    MARKET("Working the market"),
    ASSEMBL("Reading a schematic"),
    COMMS("Checking messages"),
    LOG("Reading the rig log"),
    // ⚠ "Taking notes", not the note's NAME. A title is text the player typed, and this
    // enum exists so what can be transmitted is the set of constants in it — the whole
    // structural guarantee of PresenceLeakTest. A note called "kyrell's address" would
    // otherwise go to a friends list.
    NOTES("Taking notes"),
    CALC("Doing arithmetic"),
    MANUAL("Reading the manual");

    private final String details;

    PresenceState(String details) {
        this.details = details;
    }

    /** The one line Discord shows. A constant, by construction. */
    public String details() {
        return details;
    }

    /**
     * Which state a focused tool window means.
     *
     * <h2>⚠ An exhaustive switch, and that is the point of writing it this way</h2>
     *
     * A {@code Map} or a lookup by id would let a fifteenth window ship with no presence state and
     * no complaint — it would simply never be reported, which looks exactly like the feature working
     * for the fourteen that do. {@code CLAUDE.md} already records this: an exhaustive switch over an
     * enum is the one place a new constant cannot be forgotten, and {@code RigTab.isTable()} is the
     * entry about what happens when you use the other kind.
     *
     * <p>⚠ A new window forces a decision here, and the decision is allowed to be {@link #DECK} —
     * what it may not be is silence.
     */
    public static PresenceState forWindow(WindowSpec spec) {
        if (spec == null) {
            return DECK;
        }
        return switch (spec) {
            case RIG_MONITOR -> RIG;
            case SECURITY -> SECURITY;
            case TERMINAL -> TERMINAL;
            case FILES -> FILES;
            case STORAGE -> VAULT;
            case LEDGER -> LEDGER;
            case NETMAP -> NETWORK;
            case MARKET -> MARKET;
            case ASSEMBL -> ASSEMBL;
            case COMMS -> COMMS;
            case LOG -> LOG;
            case NOTES -> NOTES;
            case CALC -> CALC;
            case MAN -> MANUAL;
            // ⚠ SETTINGS DELIBERATELY HAS NO LINE OF ITS OWN, on explicit direction, and this is
            // the sanctioned use of the "the decision may be DECK" note above.
            //
            // Three reasons, and the second is the one that settles it. It is not a game activity —
            // every other tool reports something happening in the world and this one reports the
            // player configuring their client. It is the panel somebody opens **to turn this feature
            // off**, so a distinct line means the last thing their friends are told before they go
            // dark is "Changing the settings", which is faintly absurd and slightly indiscreet about
            // an act that was meant to be private. And it is the least interesting thing anyone does
            // in this game.
            case SETTINGS -> DECK;
        };
    }

    /**
     * The same, from the window id the desk publishes on the event bus.
     *
     * <p>The desk narrates itself with {@code WindowSpec.id()} rather than the enum constant, so this
     * resolves the id and defers to {@link #forWindow}. An id nothing matches — a shell session's
     * {@code shell:&lt;address&gt;}, which is not a catalogue window — yields {@link #TERMINAL},
     * because that is what it is; ⚠ note that it resolves to the state and <b>never carries the
     * address</b>, which is the one place an id could have smuggled a machine name onto the wire.
     */
    public static PresenceState forWindowId(String id) {
        if (id == null || id.isBlank()) {
            return DECK;
        }
        if (id.startsWith("shell:")) {
            return TERMINAL;
        }
        return WindowSpec.byId(id).map(PresenceState::forWindow).orElse(DECK);
    }
}
