package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetFolder;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The network, as text — the one renderer both the map window and the terminal print through.
 *
 * <h2>Why this is a class and not two copies of a format string</h2>
 *
 * Pillar <b>C1</b> ({@code docs/client/00-client-overview.md} §2) requires everything a tool window
 * can do to be reachable from the terminal and the reverse. That is usually read as "add a verb",
 * but the harder half is that the two surfaces must keep <em>agreeing</em>: a player who learns to
 * read {@code ADDRESS SERVER HOPS KIND TIER STATE NOTE} in the map window and then types
 * {@code net} should get the same seven columns in the same order at the same widths, not a second
 * dialect of the same data. {@code BreachCommands}' ledger comment states the rule for the breach —
 * "column order matches the window's, so a player who reads one can read the other" — and keeps it
 * by hand. This keeps it by construction: there is exactly one row renderer and both callers use it.
 *
 * <h2>Pure, and deliberately free of JavaFX</h2>
 *
 * Nothing here touches a scene graph, which is what lets the column arithmetic and the empty state
 * be tested headlessly. No test in this module starts the JavaFX toolkit today, and this class is
 * where the parts worth asserting on live so that none of them has to.
 *
 * <h2>What it may and may not say</h2>
 *
 * Every field printed here comes off {@link NetMap}, which is the player's knowledge and not ground
 * truth. In particular {@link HostKind#UNKNOWN} renders as {@code --------} rather than being
 * guessed from anything else on the row: naming a node's type is what the 15 EC Passive Sniffer
 * sells ({@code docs/design/07-recon-tools.md} §1), and a renderer that inferred it would delete a
 * purchased tool at the point of drawing. A sweep sells existence and adjacency; nothing more
 * reaches this class, so nothing more can leak out of it.
 */
public final class NetText {

    private NetText() {}

    // ── column widths ────────────────────────────────────────────────────────────────────────
    //
    // Fixed, not computed from the data. A table that sizes its columns to its contents jumps every
    // time a sweep lands, and the player loses the ability to read down a column while the numbers
    // underneath are the thing they are comparing. These are the widths in the spec's own worked
    // example and the acceptance narrative, so a screenshot of either matches the shipped output
    // character for character.

    static final int ADDRESS = 16;
    static final int SERVER = 16;
    static final int HOPS = 6;
    static final int KIND = 10;
    static final int TIER = 6;
    /**
     * ⚠ 14, not 12. {@code foothold [i]} is exactly twelve characters, so at the old width the
     * marker ran straight into the next column with no space between them — which on a
     * character-cell surface reads as one field rather than two.
     */
    static final int STATE = 14;

    /** {@code -v} only: what the sweep heard, and how deep the node's server sits from home. */
    static final int SIGNAL = 10;

    static final int DEPTH = 7;

    /** What an unestablished {@link HostKind} prints as. Eight dashes, so the column keeps its shape. */
    static final String UNKNOWN_KIND = "--------";

    /**
     * The empty state, as a single sentence.
     *
     * <p>{@code docs/design/ui-design-language.md} §6: "empty states are an instruction, not a mood
     * piece". The player can already see that nothing is listed; what they cannot see is that the
     * instrument to change that is in the starting kit and costs almost nothing to run. Naming the
     * price is the point — a new player's whole problem is not knowing that discovery is cheap.
     */
    public static final String EMPTY =
            "Nothing discovered. `sweep` is how you find out what is next to you — it costs 2 cycles "
                    + "and about twenty seconds.";

    // ── the table ────────────────────────────────────────────────────────────────────────────

    /** The header row. Same string in the window and in the terminal. */
    public static String header(boolean verbose) {
        StringBuilder out = new StringBuilder();
        out.append(pad("ADDRESS", ADDRESS))
                .append(pad("SERVER", SERVER))
                .append(pad("HOPS", HOPS))
                .append(pad("KIND", KIND))
                .append(pad("TIER", TIER))
                .append(pad("STATE", STATE));
        if (verbose) {
            out.append(pad("SIGNAL", SIGNAL)).append(pad("DEPTH", DEPTH));
        }
        return out.append("NOTE").toString();
    }

    /**
     * One row per sighting, in reading order, without the header.
     *
     * <p>Empty when nothing has been discovered — callers render {@link #EMPTY} rather than a table
     * with no rows, because a header over nothing reads as a broken panel.
     */
    public static List<String> rows(NetMap map, boolean verbose) {
        List<String> out = new ArrayList<>();
        for (Sighting sighting : ordered(map)) {
            out.add(row(map, sighting, verbose));
        }
        return out;
    }

    /**
     * Reading order: the vantage first, then by hop distance, then by address.
     *
     * <p>The address comparison is numeric per octet rather than lexicographic, so
     * {@code 10.0.0.9} sorts before {@code 10.0.0.10} the way a player expects and the way every
     * real tool that prints addresses does. A lexicographic sort is the default and is wrong in a
     * way that is invisible until a server has ten hosts.
     */
    public static List<Sighting> ordered(NetMap map) {
        List<Sighting> sightings = new ArrayList<>(map.sightings());
        // ⚠ Own rig first, THEN the vantage, then outward by hops. Keyed on the vantage alone, the
        // player's own machine sank into the middle of their own host list the moment they moved
        // their vantage — sorted by hop distance, like any stranger's.
        sightings.sort(Comparator.comparing((Sighting s) -> !s.self())
                .thenComparing(s -> !s.vantage())
                .thenComparingInt(Sighting::hopsFromVantage)
                .thenComparing(Sighting::address, NetText::compareAddresses));
        return sightings;
    }

    /** One row. See the class comment for why the widths are constants. */
    public static String row(NetMap map, Sighting sighting, boolean verbose) {
        Map<String, ServerRef> servers = serversById(map);
        ServerRef server = servers.get(sighting.serverId());

        StringBuilder out = new StringBuilder();
        out.append(pad(sighting.address(), ADDRESS))
                .append(pad(serverName(server, sighting.serverId()), SERVER))
                .append(pad(String.valueOf(sighting.hopsFromVantage()), HOPS))
                .append(pad(kind(sighting), KIND))
                .append(pad(tier(sighting), TIER))
                .append(pad(state(sighting), STATE));
        if (verbose) {
            out.append(pad(sighting.signal().name(), SIGNAL))
                    .append(pad(server == null ? "-" : String.valueOf(server.depthFromHome()), DEPTH));
        }
        return (out + note(sighting)).stripTrailing();
    }

    /**
     * {@code KIND}, or eight dashes.
     *
     * <p>⚠ Never inferred. {@link HostKind#UNKNOWN} means recon has not established a type, which is
     * a different statement from "ordinary machine" and must not be rendered as one — the same
     * distinction {@link BreachTargetList} draws for an unestablished firewall tier, and for the
     * same reason: the misreading is what gets a player killed on their second breach.
     */
    static String kind(Sighting sighting) {
        return sighting.kind() == HostKind.UNKNOWN
                ? UNKNOWN_KIND
                : sighting.kind().name();
    }

    /**
     * {@code T1}…{@code T5}, or {@code --} when there is no tier to report.
     *
     * <p>The player's own rig has none — it is not a target — and a sighting whose tier the rules
     * left unset must print as absent rather than as {@code T0}, which would read as "tier zero,
     * trivially easy" and is the same class of lie as a firewall tier of 0 meaning "no firewall".
     */
    static String tier(Sighting sighting) {
        return sighting.tier() == null ? "--" : "T" + sighting.tier().tier();
    }

    /**
     * Where the player stands in relation to this machine — the three states §5.4 fixes.
     *
     * <p>{@code identified} is deliberately <em>not</em> one of them: whether a type has been
     * established is already carried by the {@code KIND} column, and a state column that repeated it
     * would be a second source of truth for one fact, differing from the first the first time
     * somebody edited one of them.
     */
    static String state(Sighting sighting) {
        // ⚠ SELF IS CHECKED FIRST, and it has to be. Keyed on the vantage alone, the player's own
        // rig read "contact" once the vantage moved — describing their own machine as something a
        // sweep had found. It is also why "vantage" is no longer the top branch: the two are
        // different facts and the rig is the one that never changes.
        //
        // ⚠ Fits STATE's 14 columns with the [i] marker: "this rig [i]" is 12. NetHostListTest
        // treats the widths as a contract.
        String standing = sighting.self()
                ? "this rig"
                : sighting.vantage() ? "vantage" : sighting.foothold() ? "foothold" : "contact";
        // ⚠ AFTER the standing, never instead of it. The two say different things — where the player
        // can operate from, and whether there is a file to open — and a marker that replaced the word
        // would trade a fact for a fact. Square brackets because the whole client marks a state that
        // way (§4.4, the tab strips and the sweep ladder both), so it needs no legend.
        //
        // ⚠ ASCII. `i` in brackets rather than a glyph: GlyphCoverageTest fails the build on anything
        // outside the two bundled faces, and a fallback font would break the character-cell alignment
        // this whole column is laid out on.
        return sighting.reported() ? standing + " [i]" : standing;
    }

    /**
     * The trailing note: everything true about this machine that has no column of its own.
     *
     * <p>⚠ A bridge row prints the literal lowercase word {@code bridge} as well as the uppercase
     * {@code BRIDGE} in its {@code KIND} column. That is not redundancy — {@code grep} in this shell
     * is case-sensitive by default ({@code BuiltinCommands}), and {@code net | grep bridge} is the
     * documented intended use of {@code net} as a pipeline source. One of the two spellings has to
     * be there for the pipeline a player will actually type to work.
     */
    static String note(Sighting sighting) {
        List<String> marks = new ArrayList<>();
        if (sighting.looted()) {
            marks.add("looted");
        }
        if (sighting.documentAvailable()) {
            marks.add("document");
        }
        if (sighting.hostsDeployedMiner()) {
            marks.add("miner");
        }
        if (sighting.honeypotSuspected()) {
            // A suspicion, and punctuated as one. The Honeypot Detector sells doubt, not certainty
            // (docs/design/07 §1), and a note reading "trap" would sell the player something the
            // tool does not.
            marks.add("trap?");
        }
        if (!sighting.bridgePeerServerName().isEmpty()) {
            marks.add("bridge -> " + sighting.bridgePeerServerName());
        }
        return String.join(" ", marks);
    }

    // ── the server strip ─────────────────────────────────────────────────────────────────────

    /**
     * The one line that is on screen in both views, always.
     *
     * <p>The brief requires the map to always show the server the player is connected to. This is
     * how, and it is chrome inside the panel rather than a floating badge for the reason the deck's
     * compute readout is a cell in the top strip: chrome has no z-order to lose and no tab to hide
     * behind, so "always visible" is structural instead of maintained by hand.
     *
     * <p>It carries four things and no more: which server, how deep that server sits from home (the
     * danger gradient the whole world generator is built around), how much of it has been seen, and
     * the hop ceiling now in force. The ceiling is on this line rather than in a help page because
     * it is the single number that explains why a sweep did not find something.
     */
    public static String serverStrip(NetMap map) {
        ServerRef server = map.currentServer();
        String name = server == null || server.name().isBlank() ? "--" : server.name();
        int depth = server == null ? 0 : server.depthFromHome();
        int ceiling = map.hopCeiling();
        // ⚠ A SECOND INDICATOR FOR THE VANTAGE, in words, beside the box on the graph.
        //
        // The heavy frame is the primary cue and stays the primary cue — but it only says WHICH node
        // when that node is on screen, and the graph scrolls, the LIST view has no frames at all, and
        // a reader who has just moved cannot tell at a glance where they ended up. Naming it here
        // answers "where am I sweeping from" from every tab, and it is the number that explains a
        // sweep's results as directly as CEILING does.
        //
        // ⚠ §4.4: the frame is a shape and this is a word, so the state survives greyscale and a
        // screen reader both, which a frame alone does not.
        String vantage = map.vantageAddress().isBlank() ? "--" : map.vantageAddress();
        return pad("SERVER", 8)
                + pad(name, 18)
                + pad("DEPTH " + depth + " FROM HOME", 23)
                + pad("HOSTS SEEN " + map.sightings().size(), 18)
                + pad("SWEEPING FROM " + vantage, 26)
                + "CEILING " + ceiling + (ceiling == 1 ? " HOP" : " HOPS");
    }

    // ── the folder tree ──────────────────────────────────────────────────────────────────────

    /**
     * The empty state for the filing view.
     *
     * <p>Same rule as {@link #EMPTY}: an instruction, not a mood piece. What a player cannot see when
     * this is on screen is that folders exist at all, so it names the verb and what goes in one.
     */
    public static final String NO_FOLDERS =
            "No folders yet. A folder is somewhere to put a machine you want to come back to — "
                    + "make one, then file anything you have found into it. Nothing about a machine "
                    + "changes when you do.";

    /** The row a folder holding nothing gets, so an empty folder is visibly a folder and not a gap. */
    static final String EMPTY_FOLDER = "(empty)";

    /**
     * The whole filing, as lines: every folder in order, each followed by what is in it.
     *
     * <p>⚠ <b>The list is walked, never re-traversed.</b> {@link GameSession#folders()} publishes
     * parents before children with siblings already sorted, and the depth to indent by; doing a
     * second traversal here would give this renderer its own opinion about sibling order and let the
     * window and the terminal disagree about the shape of a thing they both draw. Same C1 discipline
     * as the node table above, and the failure it prevents is much harder to spot.
     *
     * <p>Indentation is two spaces per level and the marker is {@code + } for a folder and
     * {@code - } for a machine, so the shape survives a copy-paste out of the terminal into a bug
     * report — which box-drawing glyphs do not, and which is also why they are not used here despite
     * being available (an ASCII tree is one fewer thing for {@code GlyphCoverageTest} to police).
     *
     * @param unfiled discovered machines in no folder; rendered under a trailing pseudo-folder so
     *     they are visible rather than merely absent. A player who cannot see what they have not
     *     filed cannot file it.
     */
    public static List<String> folderRows(List<NetFolder> folders, List<String> unfiled) {
        List<String> out = new ArrayList<>();
        for (NetFolder folder : folders) {
            out.add(folderRow(folder));
            for (String address : folder.addresses()) {
                out.add("  ".repeat(folder.depth() + 1) + "- " + address);
            }
            if (folder.addresses().isEmpty() && folder.subtreeCount() == 0) {
                out.add("  ".repeat(folder.depth() + 1) + "- " + EMPTY_FOLDER);
            }
        }
        if (unfiled != null && !unfiled.isEmpty()) {
            out.add("+ " + UNFILED + " (" + unfiled.size() + ")");
            for (String address : unfiled) {
                out.add("  - " + address);
            }
        }
        return out;
    }

    /**
     * What the unfiled bucket is called.
     *
     * <p>⚠ Not a real folder and it must never become one — it has no id, so nothing can be moved
     * <em>into</em> it by name and no intent can target it. Unfiling is done by filing a machine to
     * a blank folder, which is one operation rather than two, and there is nothing here for a player
     * to accidentally rename or delete.
     */
    static final String UNFILED = "unfiled";

    /** One folder's own line: indent, marker, name, and how much is under it. */
    static String folderRow(NetFolder folder) {
        return "  ".repeat(folder.depth()) + "+ " + folder.name() + " (" + folder.subtreeCount() + ")";
    }

    // ── documents ────────────────────────────────────────────────────────────────────────────

    /**
     * One row per recovered document, oldest first.
     *
     * <p>{@code MATERIAL} is printed even when it is zero, because zero is the interesting case:
     * Invariant <b>I13</b> gates schematic material on the tier of the machine it came off, so a
     * deep-but-easy host yields flavour and nothing else. A column that only appeared when there was
     * something in it would hide the rule at the exact moment it applied.
     */
    public static List<String> documentRows(List<NetDocument> documents) {
        List<String> out = new ArrayList<>();
        out.add(pad("ID", 18) + pad("TITLE", 40) + pad("FROM", 18) + "MATERIAL");
        for (NetDocument document : documents) {
            out.add(pad(document.documentId(), 18)
                    + pad(document.title(), 40)
                    + pad(document.recoveredFrom(), 18)
                    + document.schematicMaterial());
        }
        return out;
    }

    private static final String NETDOCS = "/io/github/stoicswe/eyeandsickle/client/terms/netdocs/";

    /**
     * A recovered document's body, wrapped as shipped.
     *
     * <p>The bodies are client resources rather than rules data on purpose: the rules decide
     * <em>that</em> a fragment was recovered and what it was worth, and prose in a rules module
     * would be a balance file nobody could review as writing. The engine carries an id and a title;
     * this resolves the text.
     *
     * <p>A missing file renders as {@code (recovered fragment — unreadable)} and that is a valid
     * in-fiction outcome rather than an error path — a partial recovery off a defended machine
     * reading as unreadable is exactly what the fiction says happens. It must never throw: a
     * document is flavour (decision <b>N-4</b>) and nothing in the game may stall on one.
     *
     * <p>⚠ The id is validated before it reaches {@code getResource}. It arrives from the rules
     * today, so this is defence against a future caller rather than against the current one — but
     * {@code docs/client/04} §3.1's boundary is that nothing a player can influence is ever
     * concatenated into a path, and an id containing {@code ../} would do precisely that.
     */
    public static List<String> documentBody(String documentId) {
        if (documentId == null || !documentId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            return List.of(UNREADABLE);
        }
        try (InputStream in = NetText.class.getResourceAsStream(NETDOCS + documentId + ".txt")) {
            if (in == null) {
                return List.of(UNREADABLE);
            }
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(List.of(body.stripTrailing().split("\n", -1)));
            while (!lines.isEmpty() && lines.getLast().isBlank()) {
                lines.removeLast();
            }
            return lines.isEmpty() ? List.of(UNREADABLE) : List.copyOf(lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the recovered fragment " + documentId, e);
        }
    }

    static final String UNREADABLE = "(recovered fragment — unreadable)";

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    /**
     * Left-aligns into a fixed cell, clipping rather than pushing the column.
     *
     * <p>Identical behaviour to {@code BreachCommands.pad} — an over-long value loses its last
     * character and keeps the separating space, so a long server name shifts nothing to its right.
     * Copied rather than shared because that method is private to a shell class and a text helper
     * is not worth a dependency between the two packages in the direction that would create.
     */
    static String pad(String value, int width) {
        String text = value == null ? "" : value;
        if (text.length() >= width) {
            return text.substring(0, Math.max(0, width - 1)) + " ";
        }
        return text + " ".repeat(width - text.length());
    }

    private static String serverName(ServerRef server, String fallbackId) {
        if (server != null && !server.name().isBlank()) {
            return server.name();
        }
        // Not "unknown": the id is a real answer and a less useful one, but printing it keeps the
        // row joinable against the rest of the table. A server the map names in a sighting but not
        // in knownServers is a rules-side inconsistency, and hiding it would hide the bug too.
        return fallbackId == null ? "" : fallbackId;
    }

    private static Map<String, ServerRef> serversById(NetMap map) {
        Map<String, ServerRef> byId = new HashMap<>();
        for (ServerRef server : map.knownServers()) {
            byId.put(server.serverId(), server);
        }
        return byId;
    }

    /** Numeric per octet, falling back to a plain comparison for anything that is not dotted. */
    static int compareAddresses(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int limit = Math.min(a.length, b.length);
        for (int i = 0; i < limit; i++) {
            try {
                int compared = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
                if (compared != 0) {
                    return compared;
                }
            } catch (NumberFormatException notAnOctet) {
                return left.compareTo(right);
            }
        }
        return Integer.compare(a.length, b.length);
    }

    /** Uppercase with {@link Locale#ROOT} — see {@code Ui}'s class comment for the Turkish trap. */
    static String upper(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }
}
