package io.github.stoicswe.eyeandsickle.engine.net;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The twelve recoverable story fragments — <b>ids and titles only</b>.
 *
 * <h2>⚠ There is no prose in this file, and there must never be</h2>
 *
 * The bodies are client resources ({@code client/.../terms/netdocs/<id>.txt}). Rules never carry
 * prose: a document body here would be duplicated into every player's save-adjacent jar, it would put
 * a content edit inside a module whose tests assert arithmetic, and it would make a typo in a story
 * fragment a change to the rules engine. The rules decide <em>which</em> fragment a host carries; the
 * client decides what it says. A missing body renders as an unreadable recovered fragment, which is a
 * valid and in-fiction outcome rather than an error.
 *
 * <h2>Flavour, never a critical path (decision N-4)</h2>
 *
 * Deep nodes carry readable lore, and a document-bearing host at tier 3 or above also yields
 * schematic material — so danger has a real pull. But <b>progression must not depend on any of it</b>:
 * {@code docs/design/15-open-questions.md} N-2's ordered critical-path beats are unwritten, and wiring
 * this system to them would block the whole feature on a narrative pass that has not happened. Nothing
 * in the twelve is required to advance, nothing gates anything, and the schematic material they yield
 * flows into the same pooled {@code SCHEMATIC_MATERIAL_PER_UNLOCK} denominator as every other source
 * — pace, never reach (Invariant I13).
 *
 * <h2>Which host gets which fragment</h2>
 *
 * Chosen by hashing the host's address, not by drawing. The generation sequence budgets exactly ten
 * draws per host and a selection draw would be an eleventh — and the RNG contract is absolute about
 * draw counts being a pure function of the world's shape. Hashing is deterministic, costs no draw,
 * and — unlike reusing the roll that decided whether a document exists at all — leaves the choice
 * uncorrelated with depth, so the deep world does not systematically get the high-numbered fragments.
 */
public final class DocumentPool {

    private DocumentPool() {}

    /**
     * The twelve, in a fixed order. Ids are stable identifiers the client resolves to files; changing
     * one orphans every save that already recovered it.
     */
    private static final Map<String, String> TITLES = titles();

    private static Map<String, String> titles() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("doc.rota", "SHIFT ROTA, SUBSTATION 9");
        map.put("doc.tariff", "INTERNAL TARIFF SCHEDULE (SUPERSEDED)");
        map.put("doc.notice", "NOTICE OF SCHEDULED MAINTENANCE");
        map.put("doc.transcript", "TRANSCRIPT: ESCALATION CALL, PARTIAL");
        map.put("doc.manifest", "CARGO MANIFEST, UNSIGNED");
        map.put("doc.memo", "MEMO: ON THE REPORTING OF ANOMALIES");
        map.put("doc.roster", "DECOMMISSIONING ROSTER");
        map.put("doc.letter", "LETTER, UNSENT");
        map.put("doc.audit", "AUDIT FINDING 14-C");
        map.put("doc.log", "OPERATOR LOG, RECOVERED");
        map.put("doc.spec", "COOLING LOOP SPECIFICATION, FRAGMENT");
        map.put("doc.index", "INDEX OF INDEXES");
        return java.util.Collections.unmodifiableMap(map);
    }

    private static final List<String> IDS = List.copyOf(TITLES.keySet());

    /** Every fragment id, in the fixed order. */
    public static List<String> ids() {
        return IDS;
    }

    /**
     * The fragment a host at {@code address} carries.
     *
     * <p>FNV-1a over the address rather than {@code String.hashCode}. Both are deterministic — the
     * JDK specifies {@code String.hashCode}'s algorithm — but FNV mixes low bits properly, and these
     * addresses differ only in their last octet: {@code String.hashCode} on {@code 10.2.0.31} and
     * {@code 10.2.0.32} lands two apart, so a modulo would walk the twelve fragments in lockstep with
     * the host index and every server's documents would arrive in the same order.
     */
    public static String forAddress(String address) {
        long h = 0xCBF29CE484222325L;
        for (byte b : String.valueOf(address).getBytes(StandardCharsets.UTF_8)) {
            h = (h ^ (b & 0xFFL)) * 0x100000001B3L;
        }
        return IDS.get((int) Math.floorMod(h ^ (h >>> 32), (long) IDS.size()));
    }

    /**
     * The title for an id, or {@code ""}.
     *
     * <p>Empty rather than a placeholder for an id this build does not know: the client already
     * renders an unresolvable fragment as unreadable, and inventing a title for a body that does not
     * exist would promise the player something to read.
     */
    public static String title(String documentId) {
        return TITLES.getOrDefault(documentId == null ? "" : documentId, "");
    }

    public static boolean known(String documentId) {
        return TITLES.containsKey(documentId == null ? "" : documentId);
    }
}
