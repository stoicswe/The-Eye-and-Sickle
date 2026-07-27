package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.AttentionEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachAction;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachResolution;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.TargetState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The breach verbs — the terminal half of the core hacking minigame.
 *
 * <h2>Pillar C1, and why it is not optional here of all places</h2>
 *
 * {@code docs/client/00-client-overview.md} §2 requires everything a tool window can do to be
 * reachable from the terminal and the reverse. The breach is the largest window in the client and the
 * one with the most actions, so it is also the easiest place for that pillar to quietly stop being
 * true. Every intent on the port has a verb here: {@code breach} opens, {@code probe} moves,
 * {@code disengage} withdraws, {@code dismiss} clears, and {@code targets} and {@code attention} are
 * the two reads.
 *
 * <h2>Two of these are sources, and that is a real distinction</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.7 divides the catalogue into sources,
 * filters and actions, and {@link Shell} refuses a pipeline containing an action before running any
 * stage of it. {@code targets} and {@code attention} change nothing, so they may head a pipeline —
 * {@code attention | grep STRIKE} is the intended use and is why every strike row carries the literal
 * word {@code STRIKE} in a column of its own rather than only a colour. The four that change
 * something never appear after a {@code |}.
 *
 * <h2>Costs are printed, verdicts are not</h2>
 *
 * {@code breach -n} prints the target's published compute cost and defence profile and stops there.
 * It does not say "affordable", does not subtract, and does not predict an attention budget — the
 * same discipline {@code scan -n} keeps, and for the same reason: gate evaluation belongs to the
 * rules ({@code docs/client/04} §3.4, Invariant <b>I14</b>), and printing the figures so the player
 * does the arithmetic is both the correct architecture and the better teaching.
 */
public final class BreachCommands {

    private BreachCommands() {}

    /**
     * Registers the six verbs.
     *
     * <p>Separate from {@link BuiltinCommands} only because {@code SimpleCommand} there is private;
     * these are game-facing commands in every other respect and belong in the same catalogue a player
     * sees. {@link BuiltinCommands#registry()} calls this.
     */
    public static void register(Shell.CommandRegistry registry) {

        // ---------------------------------------------------------------- reads
        registry.add(new Verb(
                "targets",
                List.of(),
                "What there is to breach, what it would cost, and what is guarding it.",
                false,
                List.of(
                        "targets                    one row per target the rules will accept right now",
                        "",
                        "A source, so it may head a pipeline:  targets | grep crack",
                        "",
                        "DEFENCES shows what recon has ESTABLISHED, not what is there. `fw?` means no",
                        "firewall tier has been established — which is not the same as none, and",
                        "assuming otherwise is how a second breach goes wrong (docs/design/07 §2)."),
                inv -> {
                    List<BreachTarget> targets = inv.session().breachTargets();
                    List<String> out = new ArrayList<>();
                    out.add(pad("ID", 22) + pad("ADDRESS", 19) + pad("TIER", 6) + pad("STATE", 10)
                            + pad("CYCLES", 8) + pad("DEFENCES", 26) + "NOTE");
                    for (BreachTarget t : targets) {
                        out.add(pad(t.targetId(), 22)
                                + pad(t.address(), 19)
                                + pad("T" + t.difficultyTier().tier(), 6)
                                + pad(t.liveOrDormant().name().toLowerCase(Locale.ROOT), 10)
                                + pad(String.valueOf(t.computeCost()), 8)
                                + pad(defences(t), 26)
                                + note(t));
                    }
                    if (targets.isEmpty()) {
                        out.add("(nothing to breach — a foreign miner on your own rig is the safest first");
                        out.add(" target, and `scan --quick` is how you find one)");
                    }
                    return Command.Output.ok(out);
                }));

        registry.add(new Verb(
                "attention",
                List.of(),
                "The itemised attention ledger for the open breach — what each action cost.",
                false,
                List.of(
                        "attention                  every action this attempt, oldest first",
                        "",
                        "A source, so it may head a pipeline:  attention | grep STRIKE",
                        "",
                        "docs/design/05 §4 makes this load-bearing rather than decorative: a loss has to",
                        "read as 'I was too loud', never as 'the game decided'. Every row carries what",
                        "the move was, what it cost, the running total, and what came back."),
                inv -> {
                    Optional<BreachSnapshot> found = inv.session().breach();
                    if (found.isEmpty()) {
                        return Command.Output.ok(
                                "(no breach is open — the ledger is per attempt)",
                                "",
                                "`targets` lists what there is, `breach <target>` opens one.");
                    }
                    return Command.Output.ok(ledger(found.get()));
                }));

        // ---------------------------------------------------------------- intents
        registry.add(new Verb(
                "breach",
                List.of(),
                "Open a breach on a target. Reserves compute for the whole attempt.",
                true,
                List.of(
                        "breach <target>            open a breach; `targets` lists the ids",
                        "breach -n <target>         print what it would cost, and take nothing",
                        "",
                        "There is no clock (docs/design/05 §4). Each layer grants an attention budget",
                        "and every action spends from it; running the budget out is the failure. The",
                        "compute is held for the whole attempt and returns to thermal recovery when the",
                        "attempt ends, however it ends."),
                inv -> {
                    String id = inv.stage().argument(0).orElse("");
                    if (id.isBlank()) {
                        return Command.Output.usage("breach <target> — run `targets` for the ids");
                    }
                    if (!inv.stage().isDryRun()) {
                        return Command.Output.of(inv.session().beginBreach(id));
                    }
                    Optional<BreachTarget> target = inv.session().breachTargets().stream()
                            .filter(t -> t.targetId().equalsIgnoreCase(id))
                            .findFirst();
                    if (target.isEmpty()) {
                        return Command.Output.usage("breach: no target called '" + id + "'");
                    }
                    return Command.Output.ok(dryRun(inv.session(), target.get()));
                }));

        registry.add(new Verb(
                "probe",
                List.of(),
                "Take a move in the open breach. With no argument, lists the legal ones and their cost.",
                true,
                List.of(
                        "probe                      the moves that are legal right now, with their price",
                        "probe <action> [argument]  take one",
                        "probe -n <action>          print that move's published cost and take nothing",
                        "",
                        "Every price here is the engine's, computed for this layer and this loadout, and",
                        "it is printed before it is spent. Quiet reads cost 1, an ordinary probe 2, a",
                        "loud tool 6, the Side-Channel Reader 0, and a bypass most of the bar."),
                inv -> {
                    Optional<BreachSnapshot> found = inv.session().breach();
                    if (found.isEmpty()) {
                        return Command.Output.refused(
                                "no breach is open — `targets`, then `breach <target>`");
                    }
                    BreachSnapshot snapshot = found.get();
                    String id = inv.stage().argument(0).orElse("");
                    if (id.isBlank()) {
                        return Command.Output.ok(moves(snapshot));
                    }
                    Optional<BreachAction> action = snapshot.actions().stream()
                            .filter(a -> a.actionId().equalsIgnoreCase(id))
                            .findFirst();
                    if (inv.stage().isDryRun()) {
                        return action
                                .map(a -> Command.Output.ok(price(a)))
                                .orElseGet(() -> Command.Output.usage(
                                        "probe: '" + id + "' is not a move on this layer — run `probe`"));
                    }
                    String argument = inv.stage().argument(1).orElse("");
                    return Command.Output.of(inv.session().breachAction(id, argument));
                }));

        registry.add(new Verb(
                "disengage",
                // `detach` is gdb's own word for walking away from a process you are attached to, and
                // the breach window stands in for ptrace/gdb. Free teaching, and it costs nothing.
                //
                // ⚠ NOT named `abort`: BuiltinCommands already registers one and ShortcutsTest
                // asserts it exists. Re-registering would silently replace it in the registry map.
                List.of("detach"),
                "Withdraw from the open breach. A recorded outcome, not a failure.",
                true,
                List.of(
                        "disengage                  withdraw; also `detach`, and Shortcut+. anywhere",
                        "",
                        "Attention already spent is gone and noise already generated stays generated.",
                        "The attempt is recorded as aborted: no loot, no proof-of-skill credit. This is",
                        "the escape hatch for when a read goes bad, and using it is not a mistake —",
                        "docs/client/01 §2.2.7 calls `aborted` deliberately neutral for that reason."),
                inv -> {
                    if (inv.stage().isDryRun()) {
                        Optional<BreachSnapshot> found = inv.session().breach();
                        if (found.isEmpty()) {
                            return Command.Output.ok("would abort nothing — no breach is open");
                        }
                        BreachSnapshot s = found.get();
                        return Command.Output.ok(
                                "would abort the breach on " + s.targetLabel(),
                                s.totalAttention().spent() + " attention already spent is not refunded",
                                s.noiseSoFar() + " noise already generated stays generated",
                                s.reservedCycles() + " cycles return to thermal recovery");
                    }
                    return Command.Output.of(inv.session().abortBreach());
                }));

        registry.add(new Verb(
                "dismiss",
                List.of(),
                "Clear a finished breach's outcome and go back to the target list.",
                true,
                List.of(
                        "dismiss                    clear the outcome of an attempt that has ended",
                        "",
                        "An outcome survives a save and a reload until it is dismissed, so a breach that",
                        "ended is still there to read. Nothing is lost by leaving it up."),
                inv -> {
                    GameSession.Outcome outcome = inv.session().dismissBreach();
                    if (outcome.succeeded() && outcome.message().isBlank()) {
                        // Status preserved, so `$?` still carries what the rules decided; only the
                        // wording is ours, because a command that prints nothing looks like one that
                        // did nothing.
                        return new Command.Output(List.of("outcome cleared"), outcome.status());
                    }
                    return Command.Output.of(outcome);
                }));
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The ledger, as text.
     *
     * <p>Column order matches the window's, so a player who reads one can read the other. The
     * {@code FLAG} column exists purely so the distinction survives a pipeline: the panel paints a
     * strike row in alarm, and a colour does not survive {@code | grep}, so the word does the work.
     */
    private static List<String> ledger(BreachSnapshot snapshot) {
        List<String> out = new ArrayList<>();
        out.add(pad("NO", 5) + pad("LAYER", 7) + pad("ACTION", 24)
                + right("COST", 6) + right("TOTAL", 8) + "  " + pad("FLAG", 8) + "RESULT");
        for (AttentionEntry e : snapshot.ledger()) {
            out.add(pad(String.format(Locale.ROOT, "%03d", e.sequence()), 5)
                    + pad("L" + e.layerIndex(), 7)
                    + pad(e.label(), 24)
                    + right("-" + e.cost(), 6)
                    + right(String.valueOf(e.spentAfter()), 8)
                    + "  " + pad(e.alarm() ? "STRIKE" : "", 8)
                    + e.result());
        }
        if (snapshot.ledger().isEmpty()) {
            out.add("(nothing spent yet — `probe` lists the moves and what each one costs)");
        }

        out.add("");
        snapshot.active().ifPresent(layer -> out.add(layer.title().toLowerCase(Locale.ROOT)
                + " · " + layer.attention().spent() + " of " + layer.attention().budget() + " spent"
                + " · " + layer.attention().remaining() + " left"
                + " · " + layer.strikes() + " of " + layer.strikeLimit() + " strikes"));
        var total = snapshot.totalAttention();
        out.add("attempt · " + total.spent() + " of " + total.budget() + " spent"
                + " · trace " + Math.round(total.traceProgress() * 100) + "%"
                + " · noise " + snapshot.noiseSoFar()
                + " · " + snapshot.reservedCycles() + " cycles held");

        BreachResolution resolution = snapshot.resolution();
        if (resolution != null) {
            out.add("");
            out.add("outcome " + resolution.record().outcome().name().toLowerCase(Locale.ROOT)
                    + " · noise " + resolution.noiseGenerated()
                    + " · heat +" + resolution.heatGained()
                    + (resolution.lootMinorUnits() > 0
                            ? " · extracted " + money(resolution.lootMinorUnits()) : "")
                    + (resolution.schematicMaterial() > 0
                            ? " · material +" + resolution.schematicMaterial() : ""));
            for (String line : resolution.consequences()) {
                out.add("consequence · " + line);
            }
        }
        return out;
    }

    /** The legal moves and what each one costs, before any of them is spent. */
    private static List<String> moves(BreachSnapshot snapshot) {
        List<String> out = new ArrayList<>();
        out.add(pad("ACTION", 14) + pad("KIND", 14) + right("COST", 6) + "  "
                + pad("ARGUMENT", 18) + "WHAT IT DOES");
        for (BreachAction a : snapshot.actions()) {
            out.add(pad(a.actionId(), 14)
                    + pad(a.kind().name().toLowerCase(Locale.ROOT), 14)
                    + right(String.valueOf(a.attentionCost()), 6) + "  "
                    + pad(a.argumentHint().isBlank() ? "-" : a.argumentHint(), 18)
                    + (a.enabled() ? a.detail() : "unavailable: " + a.refusal()));
        }
        if (snapshot.actions().isEmpty()) {
            out.add("(no moves — the attempt has resolved; `attention` shows how it went)");
        }
        return out;
    }

    private static List<String> price(BreachAction action) {
        List<String> out = new ArrayList<>();
        out.add("would take " + action.label().toLowerCase(Locale.ROOT)
                + " (" + action.kind().name().toLowerCase(Locale.ROOT) + ")");
        out.add("published cost: " + action.attentionCost() + " attention");
        if (!action.argumentHint().isBlank()) {
            out.add("takes an argument: " + action.argumentHint());
        }
        if (!action.enabled()) {
            out.add("not available: " + action.refusal());
        }
        return out;
    }

    /**
     * What {@code breach -n} prints.
     *
     * <p>Published figures only. There is no attention budget or layer count here because neither is
     * on the wire before the breach opens — both are set by the tier, and the engine reports them in
     * the first snapshot. Guessing them from the tier would be the client asserting a balance value
     * it does not own.
     */
    private static List<String> dryRun(GameSession session, BreachTarget target) {
        List<String> out = new ArrayList<>();
        out.add("would open a breach on " + target.targetId()
                + (target.address().isBlank() ? "" : " (" + target.address() + ")"));
        out.add("published cost: " + target.computeCost()
                + " cycles, held for the whole attempt and released into thermal recovery when it ends");
        out.add("tier T" + target.difficultyTier().tier()
                + " · " + target.liveOrDormant().name().toLowerCase(Locale.ROOT)
                + " · " + defences(target));
        if (target.minerCrack()) {
            out.add("a crack on your own rig: no heat, on any outcome, including a failure (I9)");
            if (target.estimatedBufferMinorUnits() > 0) {
                out.add("estimated buffer: ~" + money(target.estimatedBufferMinorUnits())
                        + ", swept on success — a transfer, not a payout");
            }
        } else if (target.liveOrDormant() == TargetState.DORMANT) {
            out.add("dormant: worth loot, never worth an unlock (docs/design/02 §2.4)");
        }
        out.add("layer count and per-layer attention budget are set by the tier; the engine reports");
        out.add("  them in the first snapshot, so they are not guessed here");
        out.add("available: " + session.computeBudget().available().cycles() + " cycles");
        if (!target.available()) {
            out.add("NOTE: " + target.refusal());
        }
        return out;
    }

    /** Compact defence marks. {@code fw?} is "no firewall tier established", not "no firewall". */
    private static String defences(BreachTarget target) {
        List<String> marks = new ArrayList<>();
        marks.add(target.firewallTier() > 0 ? "fw" + target.firewallTier() : "fw?");
        if (target.tarpit()) {
            marks.add("tarpit");
        }
        if (target.canaries()) {
            marks.add("canary");
        }
        if (target.honeypotSuspected()) {
            marks.add("honeypot");
        }
        return String.join(" ", marks);
    }

    private static String note(BreachTarget target) {
        if (!target.available()) {
            return target.refusal().isBlank() ? "unavailable" : target.refusal();
        }
        if (target.minerCrack()) {
            return "crack · no heat, win or lose"
                    + (target.estimatedBufferMinorUnits() > 0
                            ? " · buffer ~" + money(target.estimatedBufferMinorUnits()) : "");
        }
        return target.liveOrDormant() == TargetState.DORMANT
                ? "dormant · loot, never an unlock"
                : "live · counts for proof-of-skill";
    }

    // ------------------------------------------------------------------ helpers

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, Math.max(0, width - 1)) + " ";
        }
        return s + " ".repeat(width - s.length());
    }

    private static String right(String s, int width) {
        return s.length() >= width ? s + " " : " ".repeat(width - s.length()) + s;
    }

    /** See {@code BreachTargetList#money} — {@code Ethecoin} ships no formatting on purpose. */
    private static String money(long minorUnits) {
        return String.format(Locale.ROOT, "%d.%02d EC", minorUnits / 100, Math.abs(minorUnits % 100));
    }

    private interface Body {
        Command.Output apply(Command.Invocation invocation);
    }

    /**
     * One breach verb.
     *
     * <p>Its own type because {@code BuiltinCommands.SimpleCommand} is private. The one thing it adds
     * over that type is a real {@link #help()} body: these six verbs have arguments and consequences
     * that a one-line synopsis cannot carry, and {@code -h} is where a player looks for them.
     */
    private record Verb(
            String name,
            List<String> aliases,
            String synopsis,
            boolean sideEffect,
            List<String> helpLines,
            Body body)
            implements Command {

        Verb {
            aliases = List.copyOf(aliases);
            helpLines = List.copyOf(helpLines);
        }

        @Override
        public boolean hasSideEffect() {
            return sideEffect;
        }

        @Override
        public List<String> help() {
            List<String> out = new ArrayList<>();
            out.add(synopsis);
            if (!helpLines.isEmpty()) {
                out.add("");
                out.addAll(helpLines);
            }
            out.add("");
            out.add("Universal flags: -h  --explain  -n/--dry-run  -v/--verbose  --");
            return out;
        }

        @Override
        public Output run(Invocation invocation) {
            return body.apply(invocation);
        }
    }
}
