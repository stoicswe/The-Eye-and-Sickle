package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which order a window's {@code [×] [−] [+]} controls sit in.
 *
 * <h2>⚠ Order only. It does not change which SIDE they are on.</h2>
 *
 * The side is a platform convention the game already follows for its own outer window — macOS puts
 * controls left, everyone else right — and it is not what this setting is about. Two players can
 * disagree about whether close belongs first without disagreeing about which corner it lives in.
 *
 * <h2>⚠ Desk windows only</h2>
 *
 * The outer window keeps following the host OS unconditionally. That one sits <em>beside</em> the
 * player's real windows, so it is judged against them; a desk window sits inside the game and is
 * judged against the game. Making the outer window configurable would let a player put close where
 * their OS puts zoom, which is the one arrangement guaranteed to cost somebody their session.
 */
public enum ControlOrder {

    /**
     * Follow whatever this computer does. The default, because it is the arrangement the player's
     * hand already knows.
     */
    SYSTEM("system", "Match this computer"),

    /** Close, minimise, zoom — left to right. */
    MACOS("macos", "macOS — close first"),

    /** Minimise, maximise, close — left to right. */
    WINDOWS("windows", "Windows — close last");

    private final String id;
    private final String label;

    ControlOrder(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** The three, in the order they are offered. */
    public static List<ControlOrder> selectable() {
        return List.of(values());
    }

    /**
     * Whether close comes first, resolving {@link #SYSTEM} against the running platform.
     *
     * @param mac whether this is macOS — passed in rather than read here, so the decision is
     *     testable without pretending to be a different operating system
     */
    public boolean closeFirst(boolean mac) {
        return switch (this) {
            case MACOS -> true;
            case WINDOWS -> false;
            case SYSTEM -> mac;
        };
    }

    /** An unknown id falls back rather than throwing — a profile outlives the build that wrote it. */
    public static Optional<ControlOrder> byId(String id) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (ControlOrder order : values()) {
            if (order.id.equals(wanted)) {
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }

    /** The stored value, or {@link #SYSTEM}. */
    public static ControlOrder resolve(String id) {
        return byId(id).orElse(SYSTEM);
    }
}
