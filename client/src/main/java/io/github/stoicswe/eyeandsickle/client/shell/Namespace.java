package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.ArrayList;
import java.util.List;

/**
 * The virtual namespace — {@code docs/client/04-terminology-and-education.md} §3.2.
 *
 * <pre>
 *   /rig/compute/            one entry per compute consumer   → ps(1)
 *   /rig/storage/vault/      the three tiers as mount points  → df(1)
 *   /rig/storage/standard/
 *   /rig/storage/high/
 *   /rig/tools/              owned tools and consumables
 *   /rig/bots/               running bot instances            → jobs(1)
 *   /rig/defense/            armed defenses
 *   /net/&lt;node-address&gt;/     the network graph — KNOWN NODES ONLY
 *   /ledger/                 ledger entries by period
 *   /man/&lt;section&gt;/&lt;term&gt;     the term database                → man(1)
 * </pre>
 *
 * <h2>Two rules that are not decoration</h2>
 *
 * <b>No path here ever resolves to a host path.</b> These are keys into an in-memory tree built from
 * session state. Nothing a player types is concatenated into a filesystem call — §3.1 rule 3, and the
 * reason the terminal can look exactly like a shell without being one.
 *
 * <b>{@code /net/} contains only what the player has discovered.</b> Recon is a paid service
 * ({@code docs/design/07-recon-tools.md} §3), so a namespace that listed unscanned nodes would be a
 * free Passive Sniffer. The same rule governs tab completion.
 */
public final class Namespace {

    private Namespace() {}

    /** Lists the entries directly under a path. Never touches a real filesystem. */
    public static List<String> list(GameSession session, String path) {
        String p = normalise(path);
        List<String> out = new ArrayList<>();

        switch (p) {
            case "/" -> out.addAll(List.of("rig/", "net/", "ledger/", "man/"));
            case "/rig" -> out.addAll(List.of("compute/", "storage/", "tools/", "bots/", "defense/"));
            case "/rig/storage" -> out.addAll(List.of("vault/", "standard/", "high/"));
            case "/rig/compute" ->
                session.computeBudget()
                        .allocations()
                        .forEach(a -> out.add(a.consumer().name().toLowerCase(java.util.Locale.ROOT) + "  "
                                + a.cycles().cycles() + " cycles"));
            case "/rig/storage/vault" -> session.items(StorageTier.VAULT).forEach(i -> out.add(i.displayName()));
            case "/rig/storage/standard" ->
                session.items(StorageTier.STANDARD_STORAGE).forEach(i -> out.add(i.displayName()));
            case "/rig/storage/high" ->
                session.items(StorageTier.HIGH_HACKABLE_ZONE).forEach(i -> out.add(i.displayName()));
            case "/rig/tools" ->
                session.items(null).stream()
                        .filter(GameSession.InventoryItem::equipped)
                        .forEach(i -> out.add(i.displayName()));
            case "/net" -> session.knownNodes().forEach(n -> out.add(n.address() + "/"));
            case "/ledger" -> session.ledger(50).forEach(r -> out.add(r.at() + "  " + r.description()));
            default -> {
                if (p.startsWith("/net/")) {
                    String address = p.substring("/net/".length());
                    boolean known = session.knownNodes().stream()
                            .anyMatch(n -> n.address().equals(address));
                    if (known) {
                        out.add("miners/");
                    }
                }
            }
        }
        return out;
    }

    public static boolean exists(GameSession session, String path) {
        String p = normalise(path);
        return p.equals("/")
                || !list(session, parent(p)).isEmpty()
                || !list(session, p).isEmpty();
    }

    static String normalise(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    static String parent(String path) {
        String p = normalise(path);
        int slash = p.lastIndexOf('/');
        return slash <= 0 ? "/" : p.substring(0, slash);
    }

    /**
     * Completion candidates for a path prefix.
     *
     * <p>Built from known state only. Completing an address the player has not paid to discover
     * would be the least obvious way this client could accidentally become authoritative, and it is
     * a one-line mistake to make.
     */
    public static List<String> complete(GameSession session, String prefix) {
        String p = prefix == null ? "" : prefix;
        String dir = p.contains("/") ? p.substring(0, p.lastIndexOf('/') + 1) : "/";
        String leaf = p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p;

        List<String> out = new ArrayList<>();
        for (String entry : list(session, dir)) {
            String name = entry.split("\\s")[0];
            if (name.startsWith(leaf)) {
                out.add(dir.equals("/") ? "/" + name : dir + name);
            }
        }
        return out;
    }
}
