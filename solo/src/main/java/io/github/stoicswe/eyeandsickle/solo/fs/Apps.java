package io.github.stoicswe.eyeandsickle.solo.fs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The programs the rig runs, and which owned items are upgrades to which.
 *
 * <h2>Why this lives in the rules and not in the client</h2>
 *
 * "Which tool does the Wide Net Sweep upgrade?" is a question about the <em>game</em>, not about a
 * window. The client happens to draw one window per program, but the mapping from an item to the
 * thing it improves would still be true in a text-only build — and it has to be true on the server,
 * because whether a remote actor can take a particular upgrade off a particular program is a rules
 * question (see {@code AccessLog}). A copy of this table in the client would be a second answer.
 *
 * <p>⚠ It deliberately does <b>not</b> import anything from the client, and must not. {@code solo}
 * has no view layer, and the day this file mentions a {@code WindowSpec} the rules engine has
 * acquired an opinion about JavaFX.
 *
 * <h2>An app bundle is a real macOS bundle, and that is the teaching</h2>
 *
 * {@code Terminal.app/Contents/uOS/terminal} is very nearly how a macOS application is laid out — a
 * directory with an extension, a {@code Contents} inside it, the executable in a directory named for
 * the operating system, and everything else beside it. ⚠ On a Mac that directory is called
 * {@code MacOS}; here it is {@link #BINARIES}, because these machines are not Macs and a folder
 * claiming otherwise would be the one dishonest thing in an otherwise real layout. A player who learns here that an "application" on a Mac is a
 * <em>folder</em> has learned something true and slightly surprising, which is the bar
 * {@code docs/education/00} §1.2 sets. ⚠ {@code Upgrades/} is <b>ours</b> and is not part of a real
 * bundle — the rest of the layout is real and this one directory is not, which is a distinction a
 * shipped {@code fs(7)} page ought to state and does not yet (noted in {@code docs/design/15}).
 */
public final class Apps {

    private Apps() {}

    /** The bundle suffix. Real: macOS applications are directories ending in {@code .app}. */
    public static final String SUFFIX = ".app";

    /** Where an app's upgrades sit inside its bundle. ⚠ Ours, not part of a real bundle. */
    public static final String UPGRADES = "Upgrades";

    /**
     * Where the executable sits inside a bundle.
     *
     * <p>⚠ <b>{@code uOS}, not {@code MacOS}.</b> A real macOS bundle really does put its binary in
     * {@code Contents/MacOS/}, and copying that name would have been the more faithful thing —
     * but it would have been faithful to the wrong claim. These machines run <b>uOS</b>
     * ({@code docs/client/03}); a folder inside them naming somebody else's operating system says
     * this rig is a Mac, which is the one thing the layout must not assert. The rest of the bundle
     * keeps its real names, because the rest is a genuine and transferable fact about how an
     * application is packaged. The directory named after the OS is the single part that has to move
     * when the OS does.
     */
    public static final String BINARIES = "uOS";

    /**
     * One program.
     *
     * @param id lowercase, matches the client's window id where there is one — so a reader can line
     *     the two up without a translation table
     * @param name what the bundle is called, before {@link #SUFFIX}
     * @param blurb one line, shown when the bundle is inspected
     * @param itemPrefixes item type prefixes whose items are upgrades to this program
     */
    public record App(String id, String name, String blurb, List<String> itemPrefixes) {

        /** {@code Network.app} */
        public String bundle() {
            return name + SUFFIX;
        }

        /** The lowercase name the executable inside {@code Contents/MacOS/} carries. */
        public String binary() {
            return name.toLowerCase(Locale.ROOT).replace(" ", "-");
        }
    }

    /**
     * Every program, in the order they appear in the Applications folder.
     *
     * <p>Alphabetical by name, because that is what a file manager does and a player scanning for one
     * is scanning by name. Grouping them by purpose would be a second organising principle in a
     * window whose whole job is that its organisation is the filesystem's.
     */
    private static final List<App> CATALOGUE = List.of(
            new App("audit", "Audit", "Processes, connections and storage on this rig.", List.of()),
            new App("botnet", "Botnet", "Bot frames and their loadouts.", List.of("bot-")),
            new App("breach", "Breach", "The exploit console.",
                    List.of("port-sweep", "exploit-", "fuzzer", "zero-day", "logic-", "cipher-")),
            new App("calc", "Calculator", "Hex, decimal, octal and binary at once.", List.of()),
            new App("comms", "Comms", "Messages and contacts.", List.of()),
            new App("defense", "Defense", "What is armed, and what it costs to keep armed.",
                    List.of("detection-array", "canary", "firewall", "honeypot", "auto-counter", "tarpit")),
            new App("files", "Files", "This filesystem, and every machine mounted onto it.", List.of()),
            new App("identity", "Identity", "Who the Eye thinks you are.",
                    List.of("relay-hop", "burner")),
            new App("ledger", "Ledger", "Every ethecoin movement and what caused it.", List.of()),
            new App("log", "Log", "What this rig has been doing.", List.of()),
            new App("man", "Manual", "The offline manual and the term index.", List.of()),
            new App("market", "Market", "What is for sale and which gate stands in front of it.",
                    List.of()),
            new App("mining", "Mining", "Self-mining allocation and deployed-miner collection.",
                    List.of("miner", "pool-")),
            new App("netmap", "Network", "The network as a graph, and the sweeps that find it.",
                    List.of("net-sweep")),
            new App("recon", "Recon", "What is known about a target and what more would cost.",
                    List.of("topology-mapper", "passive-sniffer", "traffic-", "deep-scan", "recon-")),
            new App("rig-monitor", "Rig Monitor", "Where every cycle is.", List.of()),
            new App("settings", "Settings", "Theme, teaching level, desk behaviour.", List.of()),
            new App("terminal", "Terminal", "A shell over this machine.", List.of()),
            new App("vaultstore", "VaultStore", "Items across the three tiers.",
                    List.of("cold-storage", "vault-")));

    public static List<App> catalogue() {
        return CATALOGUE;
    }

    /** The app a bundle name names — {@code Network.app} → Network. */
    public static Optional<App> byBundle(String bundleName) {
        String wanted = bundleName == null ? "" : bundleName.trim();
        return CATALOGUE.stream().filter(a -> a.bundle().equals(wanted)).findFirst();
    }

    /**
     * The program an item upgrades, or empty when it upgrades none.
     *
     * <p>⚠ Empty is a normal and common answer, not a gap to be filled with a default. A consumable,
     * a schematic and a relay hop are things the player owns that do not improve a program, and
     * filing them under some catch-all application would put them in a folder that claims they do.
     * They are still visible — every owned item is in {@code ~/.VaultStore/<tier>/} — so nothing is
     * hidden by not matching here.
     */
    public static Optional<App> forItem(String itemType) {
        String type = itemType == null ? "" : itemType.trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        for (App app : CATALOGUE) {
            for (String prefix : app.itemPrefixes()) {
                if (type.startsWith(prefix)) {
                    return Optional.of(app);
                }
            }
        }
        return Optional.empty();
    }

    /** Every app that has at least one of {@code items} installed, keyed by bundle name. */
    public static Map<String, List<VirtualFs.Installed>> upgradesByBundle(
            List<VirtualFs.Installed> items) {
        Map<String, List<VirtualFs.Installed>> out = new LinkedHashMap<>();
        if (items == null) {
            return out;
        }
        for (VirtualFs.Installed item : items) {
            forItem(item.itemType()).ifPresent(app ->
                    out.computeIfAbsent(app.bundle(), key -> new java.util.ArrayList<>()).add(item));
        }
        return out;
    }
}
