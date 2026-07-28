package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * How much of a hurry a transaction is in.
 *
 * <h2>A tier rather than a number, and that is a deliberate narrowing</h2>
 *
 * A real wallet lets you type any fee rate you like, and a real user has no idea what to type — so
 * every wallet worth using also offers three buttons, because the underlying question is "how long am
 * I willing to wait" and not "what is a sensible number of units per byte". This game offers the
 * buttons only. The mechanic being taught is <em>fee rate buys position in the queue</em>, and a free
 * text field teaches spreadsheet arithmetic instead.
 *
 * <p>⚠ Every tier gets in <b>eventually</b>. {@link #ECONOMY} is slow, not stuck: a mempool that could
 * strand a transaction forever would make a purchase a gamble on other people's traffic, which is a
 * different game from the one being played. What the tiers buy is position, and position is time.
 */
public enum FeeTier {

    /** Cheapest. Rides at the back and waits for a quiet block. */
    ECONOMY("Economy", "in a few blocks, when the queue thins"),

    /** The default. Sized to clear the next block or two under ordinary traffic. */
    STANDARD("Standard", "usually the next block or the one after"),

    /** Pays to jump the queue. For when the thing is needed now. */
    PRIORITY("Priority", "the next block, unless everyone else is paying this too");

    private final String label;
    private final String promise;

    FeeTier(String label, String promise) {
        this.label = label;
        this.promise = promise;
    }

    public String label() {
        return label;
    }

    /** What this tier buys, in words a player can act on. Never a guarantee — see the type comment. */
    public String promise() {
        return promise;
    }

    /** Tolerant of a hand-edited save or an unknown value; falls back to the safe middle. */
    public static FeeTier of(String name) {
        for (FeeTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return STANDARD;
    }
}
