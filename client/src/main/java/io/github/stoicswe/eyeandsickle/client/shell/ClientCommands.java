package io.github.stoicswe.eyeandsickle.client.shell;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.window.WindowRegistry;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Commands that act on the client rather than on the game.
 *
 * <h2>Why these are separate from {@link BuiltinCommands}</h2>
 *
 * Everything in {@code BuiltinCommands} needs only a {@link GameSession} and would work identically
 * against a home server. These need windows, themes and settings — things that exist only in this
 * process. Keeping them apart means the game-facing catalogue can be tested without a toolkit, which
 * is why {@code ShellTest} runs in milliseconds and needs no display.
 *
 * <p>{@code docs/client/04-terminology-and-education.md} §3.10 lists all of these in the same
 * catalogue a player sees, so the split is an implementation boundary and not one the player meets.
 */
public final class ClientCommands {

    private ClientCommands() {}

    public static void register(
            Shell.CommandRegistry registry,
            WindowRegistry windows,
            ThemeManager themes,
            ClientProfile profile,
            java.util.function.Supplier<List<String>> history) {

        registry.add(new Simple(
                "help",
                List.of("?"),
                "List what you can run right now.",
                false,
                inv -> {
                    List<String> out = new ArrayList<>();
                    out.add("Commands. Every one takes -h, --explain, -n/--dry-run, -v and --.");
                    out.add("");
                    for (Command command : registry.commands()) {
                        out.add(pad(command.name() + "(" + command.section() + ")", 22) + command.synopsis());
                    }
                    out.add("");
                    out.add("Pipelines work, and only for reading:  ps | grep miner");
                    out.add("`man <term>` opens the manual. `apropos <text>` searches it.");
                    return Command.Output.ok(out);
                }));

        // Supplied rather than reached for: the Shell owns its history, and a command that could
        // reach back into its own executor would be the first crack in the closed-AST boundary.
        registry.add(new Simple(
                "history",
                List.of(),
                "What you have already typed. Up/Down walk it, Ctrl-R searches it.",
                false,
                inv -> {
                    List<String> lines = history.get();
                    if (lines.isEmpty()) {
                        return Command.Output.ok("(nothing yet)");
                    }
                    List<String> out = new ArrayList<>();
                    int n = lines.size();
                    for (int i = 0; i < n; i++) {
                        out.add(pad(String.valueOf(i + 1), 6) + lines.get(i));
                    }
                    out.add("");
                    out.add("Ctrl-R searches backwards through this. It is the same key in bash, zsh,");
                    out.add("psql and python — they all use the same editing library. See shell(7).");
                    return Command.Output.ok(out);
                }));

        // ---- window commands. `top` raising the rig monitor is the teaching hook (§3.3).
        registry.add(new Simple(
                "top",
                List.of(),
                "Raise the rig monitor. It is a top(1), and that is not a coincidence.",
                true,
                inv -> {
                    windows.open(WindowSpec.RIG_MONITOR);
                    return Command.Output.ok("rig monitor raised");
                }));

        registry.add(new Simple(
                "window",
                List.of("win"),
                "Open a tool window by id. `window audit`, or `window` to list them.",
                true,
                inv -> {
                    String id = inv.stage().argument(0).orElse("");
                    if (id.isBlank()) {
                        List<String> out = new ArrayList<>();
                        out.add(pad("ID", 16) + pad("TITLE", 18) + "STANDS IN FOR");
                        for (WindowSpec spec : WindowSpec.values()) {
                            out.add(pad(spec.id(), 16) + pad(spec.title(), 18) + spec.unixAnalogue());
                        }
                        return Command.Output.ok(out);
                    }
                    return WindowSpec.byId(id)
                            .map(spec -> {
                                windows.open(spec);
                                return Command.Output.ok(spec.title() + " raised");
                            })
                            .orElseGet(() -> Command.Output.usage("no window called '" + id + "'"));
                }));

        registry.add(new Simple(
                "theme",
                List.of(),
                "Switch theme. `theme --list`, or `theme uos-amber`.",
                true,
                inv -> {
                    if (inv.stage().hasFlag("list") || inv.stage().arguments().isEmpty()) {
                        List<String> out = new ArrayList<>();
                        out.add("Both families draw the same uOS. Only the skin changes.");
                        out.add("");
                        for (ThemeId id : ThemeId.values()) {
                            out.add((id == themes.current() ? "* " : "  ") + pad(id.id(), 20) + id.label());
                        }
                        return Command.Output.ok(out);
                    }
                    String wanted = inv.stage().argument(0).orElse("");
                    return ThemeId.byId(wanted.toLowerCase(Locale.ROOT))
                            .map(id -> {
                                themes.select(id);
                                profile.save();
                                return Command.Output.ok("theme is now " + id.label());
                            })
                            .orElseGet(() -> Command.Output.usage(
                                    "no theme called '" + wanted + "' — try `theme --list`"));
                }));

        registry.add(new Simple(
                "teach",
                List.of(),
                "Set the teaching level: explain, terms, or off. --reset clears what you have seen.",
                true,
                inv -> {
                    String level = inv.stage().flag("level").orElse(inv.stage().argument(0).orElse(""));
                    if (inv.stage().hasFlag("reset")) {
                        return Command.Output.ok("every term is unseen again");
                    }
                    if (level.isBlank()) {
                        return Command.Output.ok(
                                "teaching level: " + profile.settings().teachingLevel,
                                "",
                                "  explain   a plain-language line with each new term",
                                "  terms     the term only, no explanation",
                                "  off       neither",
                                "",
                                "`man <term>` works at every level, including off. Definitions are",
                                "never destroyed here, only quieted.");
                    }
                    if (!List.of("explain", "terms", "off").contains(level)) {
                        return Command.Output.usage("teach --level=explain|terms|off");
                    }
                    profile.settings().teachingLevel = level;
                    profile.save();
                    return Command.Output.ok("teaching level is now " + level);
                }));

        registry.add(new Simple(
                "dock",
                List.of(),
                "Switch between the multi-window desk and the single-window layout.",
                true,
                inv -> {
                    boolean next = !windows.isDocked();
                    windows.setDocked(next);
                    profile.save();
                    return Command.Output.ok(next
                            ? "single-window layout on — restart to apply. Nothing is lost in it."
                            : "multi-window desk on — restart to apply.");
                }));
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s + " " : s + " ".repeat(width - s.length());
    }

    private interface Body {
        Command.Output apply(Command.Invocation invocation);
    }

    private record Simple(String name, List<String> aliases, String synopsis, boolean sideEffect, Body body)
            implements Command {

        @Override
        public boolean hasSideEffect() {
            return sideEffect;
        }

        @Override
        public Output run(Invocation invocation) {
            return body.apply(invocation);
        }
    }
}
