package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.ui.breach.AsciiCanvas;

/**
 * Every non-ASCII character the network map draws that {@link AsciiCanvas} does not already name.
 *
 * <h2>⚠ This file is the network map's glyph blast radius</h2>
 *
 * The implementation spec makes this <b>the second file in the client permitted to carry box-drawing and
 * block-element literals</b>, {@link AsciiCanvas} being the first, and says plainly: do not introduce a
 * third vocabulary. So every glyph {@link NetLayout}, {@link NetCanvas}, {@link NetGraph} and
 * {@link NetLegend} draw arrives from here or from {@code AsciiCanvas}. The audit for "does the bundled
 * font actually contain this?" stays two files and one {@code grep}.
 *
 * <p>The only non-ASCII characters left elsewhere in this package are two em dashes in player-facing
 * prose in {@link NetGraph}, which are punctuation rather than vocabulary — the same exemption
 * {@code AsciiCanvas} records for the three in the breach package, and measured present in both bundled
 * faces rather than assumed.
 *
 * <p>That audit is {@code GlyphCoverageTest}, which parses the bundled TTF {@code cmap} and fails the
 * build on any character absent from IBM Plex Mono. It exists because the characters a network renderer
 * reaches for by reflex are precisely the ones that are missing: the whole Geometric Shapes block
 * (U+25A0–U+25FF) has <b>one</b> character in Plex, the lozenge, so {@code ● ○ ◆ ■ ▲}, which is what a
 * node marker wants to be, are none of them present. That absence is the reason a node here is
 * <em>two block-element cells</em> rather than a shape: the block-element range is complete in Plex, and
 * two cells is also the aspect correction, since a character cell is about twice as tall as it is wide
 * and a single cell would read as a vertical sliver.
 *
 * <h2>Weight first, colour second</h2>
 *
 * Every state distinction on the map is carried by a <em>different character</em> before it is carried by
 * an ink level: {@code ██} vantage, {@code ▓▓} foothold, {@code ▒▒} identified, {@code ░░} contact. The
 * map has to survive greyscale, and it has to survive the palette rule that {@code -es-amber} means
 * live/earning data — a network node is not earning, so there is no amber here to lean on even if the
 * design language allowed it.
 *
 * <h2>Rounded corners are load-bearing, not decoration</h2>
 *
 * {@link #ROUND_TL} and friends are how a <em>lateral</em> edge (one that stays inside a hop layer) is
 * told apart from a <em>forward</em> edge (one that crosses into the next), <b>by shape rather than by
 * colour</b>. They were measured present in IBM Plex Mono and absent from Martian Mono — which is fine,
 * because the whole map is pinned to Plex, and it is one more reason it must stay pinned.
 */
public final class NetGlyphs {

    private NetGlyphs() {}

    // ── Node markers. Two cells each: aspect-corrected, and the only complete range Plex has. ────────

    /** Where the player is operating from, right now. Paired with the one heavy frame on the map. */
    public static final String NODE_VANTAGE = "██";

    /** Breached: the player may {@code connect} here, which is what makes reach out of position. */
    public static final String NODE_FOOTHOLD = "▓▓";

    /** Detected <em>and</em> typed — the Passive Sniffer has run. {@code docs/design/07-recon-tools.md} §1. */
    public static final String NODE_IDENTIFIED = "▒▒";

    /**
     * Detected, type not established. The honest reading of {@code HostKind.UNKNOWN}.
     *
     * <p>⚠ This is <b>not</b> "undiscovered but suspected", and the difference is the whole discovery
     * model. An undiscovered machine has no {@code Sighting} and is drawn <em>nowhere at all</em> — no
     * cell, no placeholder, no count. This glyph means "something is there and I do not know what",
     * which is exactly what a sweep sells; upgrading it to {@link #NODE_IDENTIFIED} is what the 15 EC
     * Passive Sniffer sells.
     */
    public static final String NODE_CONTACT = "░░";

    /** An identified cross-server link. It advertises the server on its far side and nothing else. */
    public static final String NODE_BRIDGE = "╪╪";

    /** A suspected honeypot — the one and only {@code -es-alarm} on the whole panel. */
    public static final String NODE_TRAP = "‡‡";

    /**
     * The far side of an identified bridge: beyond the horizon, and the one thing drawn outside the
     * discovered set.
     *
     * <p>It is legitimate precisely because a bridge's published function is to name the network on the
     * other side. It carries the peer <b>server</b> name and nothing more — never a peer address, never
     * a host count, never anything about what is over there. It is drawn without a frame, because a
     * frame would claim it is a machine the player has mapped.
     */
    public static final String NODE_DARK = "··";

    // ── Lateral edges. Arcs, so a same-layer edge reads differently from a forward one in greyscale. ─

    public static final char ROUND_TL = '╭';

    public static final char ROUND_TR = '╮';

    public static final char ROUND_BL = '╰';

    public static final char ROUND_BR = '╯';

    /**
     * Elision, for a server name too wide for the column header that has to carry it.
     *
     * <p>Not decoration: a header truncated without a mark reads as a machine with a mangled name,
     * which is exactly the kind of thing a player learns to distrust the whole instrument over. This
     * says "there is more of this word" in one cell.
     *
     * <p>Measured against the bundled {@code cmap} rather than assumed: U+2026 is present in IBM Plex
     * Mono <em>and</em> in Martian Mono, so it is safe even on the one line of this panel whose face is
     * inherited rather than pinned.
     */
    public static final char ELLIPSIS = '…';

    // ── Motion ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * The travelling packet.
     *
     * <p>The same character as {@link AsciiCanvas#BULLET}, deliberately declared under its own name: it
     * is the moving mark on this map, and a reader following the animation rule wants to find it under
     * a name that says so rather than under the punctuation constant.
     */
    public static final char PACKET = '·';

    // Light and heavy frame characters, the four-way junction table and the destination arrow are
    // reused from AsciiCanvas and are deliberately NOT redeclared here. A second copy of `┌` under a
    // different name is exactly the third vocabulary the spec forbids.
}
