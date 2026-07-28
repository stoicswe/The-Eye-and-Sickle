# 05 — Tool Windows & The Layout System

**Status:** ⚠️ **[PROPOSAL]** — the *model* is Established and cited inline (one `javafx.stage.Stage` per tool, `../architecture/01-tech-stack.md` §1 and `../design/00-vision-and-pillars.md` §6; the always-on-top rig monitor, `../design/01-core-resources.md` §1.4; the mandatory single-window docked fallback, flagged independently in `../architecture/01-tech-stack.md` §1 and `../design/05-hacking-minigame.md` §5; the real-data audit views, `../design/04-mining.md` §3.1). Everything else here — per-window sizes, the catalogue detail, the accelerator map, geometry persistence, workspaces, the dock shell, and the attention ladder — is first-pass design. Toolkit claims were checked against the live JavaFX API docs; anything unconfirmed is marked **⚠ unverified** inline.
**Depends on:** `00-client-overview.md` (pillars C1–C6, the window catalogue in §6.1, the shortcut map in §6.3), `01-visual-language.md` (the token and primitive contract), `02-platform-native-themes.md` (per-platform window chrome), `../design/01-core-resources.md`, `../design/04-mining.md`, `../design/05-hacking-minigame.md`, `../design/09-defense-and-hardening.md`, `../design/10-botnets.md`, `../design/12-identity-and-social.md`, `../design/glossary.md`
**Depended on by:** `06` (breach & live-engagement surfaces), `07` (onboarding & first run), and the `client/` module's window layer — `EyeAndSickleClient`, `Launcher`, `window/ToolWindow`

---

> ⚠️ **SUPERSEDED 2026-07-26 — there is one window, and the client draws its own chrome.**
> `../design/ui-design-language.md` §0 cancels the `Stage`-per-tool model this document is written
> against, and the docked *tabbed* shell §5 designs. Both are replaced by **the deck**: one
> `StageStyle.UNDECORATED` Stage containing an in-game window manager
> (`client/.../ui/chrome/DeskManager`) with drag, focus, z-order, minimise, maximise, close, resize,
> snap-to-grid and edge tiling.
>
> **What survives from this document unchanged:** the window catalogue and its ids, the minimum and
> default sizes, the accelerator map, the `paneFactory` contract, geometry persistence and off-screen
> recovery, and the rule that no window's minimum may exceed 720×480. `WindowCatalogueTest` still
> checks all of it.
>
> **What changed:** a tool is a panel on the desk rather than a `Stage` or a `Tab`. §1.4's always-on-top
> problem is gone — the compute readout is a cell in the top status strip, which is chrome. §1.3's
> occlusion and displacement failure modes are now the window manager's to answer rather than the OS's.
> §5's docked mode is retired as a separate layout; its **contract** — no functionality or information
> lost — carries over and is stronger, because there is no longer a second layout that could lose
> anything. Reasoning logged in `../design/15-open-questions.md` §3.

## 1. The multi-window model

### 1.1 What is Established, and what that actually commits us to

> **One tool, one OS window.** `../architecture/01-tech-stack.md` §1: *"JavaFX's `Stage` is a top-level OS window; one `Stage` per tool gives the literal 'operator's desk' layout the design wants […]. This is a *native* capability, not a fight against the framework."* `../design/00-vision-and-pillars.md` §6 names the target image directly: *"map here, terminal there, miner dashboard on the second monitor."*

Three consequences follow, and they are the reason this document is long:

1. **Layout belongs to the player, not to us.** `00-client-overview.md` §1.1 puts window layout, sizes and positions in the client-owned column. We ship defaults, presets and repair; we never silently rearrange a desk the player arranged. Every automatic geometry change in this document is either (a) a repair of a window that would otherwise be unreachable (§3.8), or (b) explicitly requested by the player (§4).
2. **We design windows, not screens.** There is no "main screen" to compose. Every tool has to be legible alone, at its minimum size, with no other window visible — because that is a state the player can and will produce.
3. **The window manager is a third party.** JavaFX will not fight it and neither will we. Z-order, snapping, workspaces, tiling and stage management are the OS's. We influence z-order exactly twice (`01-visual-language.md` §5.1): the rig monitor's `alwaysOnTop`, and explicit user-initiated raises.

### 1.2 Why the multi-window model is load-bearing rather than decorative

It would be cheaper to build one window with a tab bar. Three things break if we do.

**The breach loses its shape.** `../design/05-hacking-minigame.md` §5 describes a breach spanning windows *the way a real operator's desk does* — map for Traversal, terminal for the active layer, rig monitor for compute and trace, recon for the flavour logs the human-read steps depend on (§3.2). The puzzle's anti-bot property (Invariant I10) is precisely that a human cross-references material a fixed heuristic cannot: the Traversal class *"hides the true objective node among decoys distinguishable only by cross-referencing recovered logs."* Cross-referencing two documents is a **simultaneity** problem. A tabbed shell makes it a memory problem instead, which is a different and worse game.

**Compute stops governing.** Pillar C2 (`00-client-overview.md` §2) requires the compute ledger to be permanently visible, and `../design/01-core-resources.md` §1.4 justifies a dedicated always-on-top window for exactly that reason. In a tabbed shell "always visible" has to be re-invented as persistent chrome — which is what §5 does for the docked mode, and it is strictly a fallback, not the plan.

**The fiction stops being the interface.** Pillar C1: the interface *is* the toolset. A tool you can put where you want, resize, and leave open on a second monitor reads as a thing you own. A tab reads as a section of an app.

### 1.3 The four failure modes of multi-window, and where each is answered

Naming them up front is the honest form of adopting the model.

| Failure | What it looks like | Answered in |
|---|---|---|
| **Loss** — a window exists but the player cannot find it | Twelve windows, three monitors, one of them behind the browser | §3.4 the switcher; §3.5 the accelerator map |
| **Occlusion** — the window is there but covered at the moment it matters | The rig monitor is under the map when the trace spikes | §1.4 always-on-top and its platform fallback; §5 docked mode |
| **Displacement** — saved geometry no longer describes a place | The laptop was undocked; two windows are at x=2600 | §3.8 off-screen recovery |
| **Management cost under time pressure** | Arranging windows while a trace timer runs | §4 workspaces; §5 the docked fallback; §6 the no-focus-theft rule |

### 1.4 Always-on-top, and the platform caveat that must be handled

Exactly one window sets `alwaysOnTop` by default: `rig-monitor` (`01-visual-language.md` §5.4; `../design/01-core-resources.md` §1.4; already encoded in the scaffold's `ToolWindow.alwaysOnTop()`).

The JavaFX `Stage.alwaysOnTop` javadoc carries a caveat that is load-bearing here (**verified**): *"setting this property might be ignored on some platforms."* Since pillar C2 rests on it, the client cannot set-and-hope.

**Required handling:**

1. Call `stage.setAlwaysOnTop(true)`, then **read back** `stage.isAlwaysOnTop()`.
2. If it read back `false`, the platform declined. Do not retry in a loop and do not warn on every launch.
3. Degrade in this order: (a) offer the rig monitor's **strip form** (§2.1) pinned to a screen edge, where occlusion costs less because the strip is short; (b) surface a one-time rung-1 note in the switcher (§6.2) offering the **docked layout**, whose rig strip is chrome and structurally cannot be occluded (§5.2).

Platform-specific window-chrome behaviour — macOS Spaces and full-screen, Windows Snap Layouts, Wayland's positioning restrictions — belongs to `02-platform-native-themes.md` §3.9, §4.9 and §5.8. This document assumes the profile that doc resolves and does not re-decide it.

No other window may set `alwaysOnTop` without the player asking. **No alert may ever set it** (`01-visual-language.md` §5.4) — an alert that forces itself above a live breach is focus theft with extra steps (§6.3).

### 1.5 The window registry — the implementation shape everything else assumes

The scaffold's `ToolWindow` already carries `id`, `title`, default size, `alwaysOnTop` and a content `Supplier<Parent>`, and its javadoc already states the right principle: *"a tool declares what it is called and how to build its content, and window plumbing stays in one place."* This document needs five more fields on that record:

| Field | Why it must exist |
|---|---|
| `minWidth` / `minHeight` | Below its minimum a tool is not degraded, it is broken. Applied with `Stage.setMinWidth/setMinHeight` (**verified** on `Stage`) so the OS refuses the resize rather than the layout breaking silently. |
| `canonicalOrder` | Fixes the switcher order, which fixes `Shortcut+1…9` (§3.5). |
| `closable` | `false` only for `rig-monitor` (`00-client-overview.md` §2, C2). |
| `initialFocus` | Which node takes focus when the window is raised (§3.3). Per-window, in the catalogue. |
| `paneFactory` | **The single most important field.** It returns the tool's content `Parent`. In multi-window mode that `Parent` becomes a `Scene` root; in docked mode the *same instance* becomes a `Tab` content node. A JavaFX `Node` has one parent, so one instance can only be in one place — which makes "the docked mode shows the same thing" structural rather than a promise (§5.4). |

`ToolWindow`'s current `Supplier<Parent>` builds a fresh `Parent` per call, which is right for `openNew()` and wrong for mode switching, where the live instance must be re-parented so scroll position, selection, and typed-but-unsubmitted text survive (`00-client-overview.md` §1.1 lists draft state as client-owned — losing it on a mode switch would be the client destroying its own data). The registry therefore memoises: one pane instance per window id per session.

### 1.6 Titles, and one thing a title must never contain

Format: `<tool name> — The Eye and Sickle`, sentence case per `01-visual-language.md` §9.2.

Tool name **first**. OS window lists, taskbar tooltips, `Shortcut+Tab` switchers and Mission Control all truncate the tail, so a title that leads with the product name renders thirteen identical entries. (The scaffold's current `"The Eye and Sickle — Rig Monitor"` has this backwards and should be flipped when the second window lands.)

> **A window title never contains the player's handle, DID, server, or any balance.** Titles leak into screen shares, screenshots, OS window lists and streaming overlays. A game whose social layer is built on hidden roles and evidence (`../design/12-identity-and-social.md` §2) and which ships a *Burner Handle* explicitly for compartmentalisation (§1) should not put the handle in the one string every screen-capture tool renders. Identity lives inside `identity`, where the player chose to look at it.

---

## 2. The window catalogue

The thirteen ids below marked **(00 §6.1)** come from `00-client-overview.md` §6.1. Two are additions this document makes — see §2.2. Sizes are **logical px** (JavaFX's scale-independent units), so they mean the same thing at 1× and 2×.

### 2.1 Summary

| # | id | Title | Unix analogue | Default | Minimum | Accel | Closable | Open on first run |
|---|---|---|---|---|---|---|---|---|
| — | `rig-monitor` | Rig monitor | `top` | 420×560 *(panel)* · 560×96 *(strip)* | 320×420 · 420×88 | `Shortcut+0` | no — collapses to strip | **yes** |
| 1 | `terminal` | Terminal | a shell session | 880×620 | 560×360 | `Shortcut+1` | yes | no |
| 2 | `netmap` | Network | `nmap` / a topology view | 1100×780 | 720×480 | `Shortcut+2` | yes | no |
| 3 | `recon` | Recon | `less` | 760×640 | 480×400 | `Shortcut+3` | yes | no |
| 4 | `audit` | Audit | `ps` / `netstat` / `df` | 900×600 | 640×400 | `Shortcut+4` | yes | no |
| 5 | `mining` | Mining | a miner dashboard | 820×600 | 560×400 | `Shortcut+5` | yes | no |
| 6 | `storage` | Storage | `ls` across three mounts | 840×620 | 560×420 | `Shortcut+6` | yes | no |
| 7 | `ledger` | Ledger | a transaction log | 880×560 | 600×360 | `Shortcut+7` | yes | no |
| 8 | `botnet` | Botnet | `jobs` / `systemctl` | 780×560 | 520×400 | `Shortcut+8` | yes | no |
| 9 | `defense` | Defense | a firewall / IDS console | 780×560 | 520×380 | `Shortcut+9` | yes | no |
| — | `market` | Market | a package manager | 900×640 | 600×440 | `Shortcut+Shift+M` | yes | no |
| — | `identity` | Identity | `whoami` / `id` | 560×640 | 420×440 | `Shortcut+Shift+I` | yes | no |
| — | `comms` ✚ | Comms | `mail` / `who` | 720×620 | 480×400 | `Shortcut+Shift+P` | yes | no |
| — | `settings` ✚ | Settings | `~/.config` | 760×620 | 560×440 | `Shortcut+,` | yes | no |
| — | `calc` ✚ | Calculator | `bc` / `printf %x` | 820×700 | 560×460 | `Shortcut+Shift+C` | yes | no |
| — | `switcher` | Windows | `jobs` | 280×520 | 240×320 | `Shortcut+Shift+J` | yes | **yes** |

`dock` is reserved as the id of the docked shell's single `Stage` (§5.2). It is not a tool and never appears in the switcher.

**Minimum sizes obey one rule:** no window's minimum may exceed **720×480**, so that any two tools fit side by side on a 1366×768 laptop with room for the rig strip. `netmap` and `breach` sit exactly on it (720×480); a graph below that is a list pretending to be a graph.

### 2.2 Three windows this document adds — and one it does not

`00-client-overview.md` §6.1 lists thirteen. This document adds **`comms`**, **`settings`** and **`calc`**, and says so rather than quietly extending a table another doc owns.

- **`comms`** — `identity` is `whoami`: *your* handle, DID, heat bands, faction standing, burners. The social layer is a different subject entirely — other operators, recovered messages, compiled dossiers, the evidence threshold and the mass-vote override (`../design/12-identity-and-social.md` §2–§3). Folding "who might be informing on me" into "who am I" would bury a whole system inside a status panel. The Unix pairing makes the split legible: `id` versus `who`/`mail`.
- **`settings`** — `00-client-overview.md` §4.2 already routes theme selection through "Settings → Appearance" and §5.2 makes the teaching level a persistent choice, so the surface is presupposed; it simply had no id. It also has to hold the layout escape hatch (§3.8).
- **`calc`** *(added 2026-07-28)* — one value in hex, decimal, octal and binary at once, with its bits as a clickable grid, a register width, a two's-complement reading, the bitwise and shift operations, and a byte-order swap. It earns its slot on pillar **C6**, not on a game system: `../education/01-foundations.md` is a whole domain about bases, bit width, two's complement, byte order and overflow, and every other window in this catalogue hands the player numbers in the machine's notation without any surface that makes them legible. It is also **the only window that takes no `GameSession`** — it spends nothing, is gated by nothing and cannot be lost, so it is the one addition that required checking no invariant. Terminal half: `calc(1)`, aliased `bc`, driving the same engine (**C1**).

Tracked as **WL-1**. If the catalogue owner would rather have thirteen, `comms` folds into `identity` as a second section and `settings` becomes a `ModalPane` over whichever window invoked it — both are cheaper than the reverse.

**Not added: a chat window.** `../design/00-vision-and-pillars.md` §3 pillar 5 is explicit that story arrives as recovered logs and records and that *"there are no companion characters."* A general chat window would be the easiest place in the client for that pillar to leak.

---

### 2.3 `rig-monitor` — Rig monitor

> The most important window in the client. `../design/01-core-resources.md` §1.4 calls the compute ledger *"the game's most important HUD element"* and specifically justifies a dedicated, always-on-top window. This is Established; the layout below is not.

**Owns** — the four numbers §1.4 requires visible at a glance, plus the two meters that only matter under pressure:

| Row | Content | Primitive / format |
|---|---|---|
| Headline | Available cycles | `es-stat`, `TYPE_MONO_READOUT_LG`, `72 / 100 cycles` |
| Gauge | Allocation **segmented by consumer**, plus available and recovering | `es-gauge es-gauge-compute`; legend mandatory (`01-visual-language.md` §8.1) |
| Legend | `Breacher 22 · Miner 10 · Recon 8 · Defense 14 · Channels 9` | mono, per `01-visual-language.md` §9.3 |
| Recovery | `18 recovering · 4m 20s` | time-to-recover mandatory |
| Thermal | `Thermal T1 · load 84% · recovery ×4.6` | rendered as received — the multiplier is a server-derived value, not a client calculation (C4) |
| Heat | `PERSONAL · moderate` and `SERVER · high` | banded chips, never meters (`01-visual-language.md` §2.2.4) |
| Noise | pooled value + decay tail + threshold ticks | `es-gauge es-gauge-noise` |
| Trace | contribution segments, one per action | `es-gauge es-gauge-trace` |

**Two structural rules:**

- **The gauge is segmented by consumer or it is a defect.** `../design/09-defense-and-hardening.md` §3 and `../design/10-botnets.md` §3 both make the same point from different ends: a paranoid defensive loadout is 67 permanent cycles, a three-bot loadout is 40 before socketed tools, and the player's actual decision is *which one to stop*. One grey bar cannot be acted on. (Segment legibility past ~8 consumers is **V-7** in `01-visual-language.md`; this doc inherits that problem rather than re-solving it.)
- **The trace row is always present, even when idle**, showing `no active engagement` in `-es-fg-secondary`. Growing the window when a breach starts would move every number below it at the exact moment the player is reading them, which `01-visual-language.md` §7.3 forbids. Constant geometry also means the player learns where the trace meter *will* be long before they need it.

**Two forms.**

- **Panel** — 420×560, min 320×420. The default. Vertical, all rows.
- **Strip** — 560×96, min 420×88. Three lines: readout + heat chips + trace; the gauge; the legend as `TYPE_MONO_CAPTION`. Wide rather than tall specifically so the mandatory legend fits on one line.

`Shortcut+W` on the panel collapses it to the strip; it never closes (`00-client-overview.md` §2). The strip toggles back from its own control or `rig --panel` in the palette. `Shortcut+0` raises whichever form is active — and per `00-client-overview.md` §6.3 that binding is **never remappable**.

**Always-on-top:** yes, by default, with the read-back fallback in §1.4.
**Initial focus:** the gauge (focusable, so `Alt` reveals attribution without a pointer — C3).
**Fed by:** `../design/01-core-resources.md` §1 (compute), §3 (noise), §4 (heat); `../design/11-rig-infrastructure.md` §2 (Thermal Budget); `../design/05-hacking-minigame.md` §4 (trace).

---

### 2.4 `terminal` — Terminal

**Purpose:** the active breach layer and the command surface. Where the puzzle is played.

**Owns:** the command line and its history; the live event stream for the focused engagement; the current layer's probe surface; the layer stack readout (`layer 2 of 4 · Credential · tier 3`); the abort control.

**Key interactions:**

| Input | Action |
|---|---|
| `Up` / `Down` | command history |
| `Tab` | completion over the enumerated command set |
| `Shortcut+F` | find within the buffer |
| `Shortcut+C` | copy selection — every line is selectable text (`01-visual-language.md` §8.7) |
| `Shortcut+.` | abort the breach; always confirms, because `aborted` is a persisted outcome (`../design/05-hacking-minigame.md` §2) |

**Two rules that generalise beyond this window:**

- **Read-only duplication across windows is permitted; a control has exactly one home.** During a live breach the terminal header carries a compact trace readout, because a player whose rig monitor happens to be occluded must not lose the trace. It does **not** carry a second abort button. The keyboard path (`Shortcut+.`) is global, so the action is universally reachable while the button stays in one place.
- **Not a shell.** `00-client-overview.md` §7 makes this a security boundary, not a scope decision: the command surface dispatches to a fixed, enumerated set of game commands. No subprocess, no filesystem, no scripting hook. The command grammar itself belongs to `04`.

**Initial focus:** the command line.
**Fed by:** `../design/05-hacking-minigame.md` §2 (the contract), §3 (classes and layers), §4 (trace); `../design/06-intrusion-tools.md` (equipped loadout).

---

### 2.5 `netmap` — Network

> **⚠ This section described `map` until 2026-07-27, and the code had shipped TWO windows against it.** `netmap` is the tool this section specifies — the graph, the recon overlays, the node states. A second window also called "Network map" held a read-only table of known nodes on the same `Shortcut+2`; it had **no sweep control**, so it was permanently empty for anyone who had not swept elsewhere, and it carried a stale note reading *"Breach targeting is not built"*. It was **removed**, and `netmap` — which has had a LIST view on a chip beside GRAPH and FOLDERS the whole time — inherited the id's slot and its accelerator. `WindowCatalogueTest.onlyOneNetworkTool` fails the build if a second network window ever appears.

**Purpose:** the target graph. The Traversal puzzle class lives here (`../design/05-hacking-minigame.md` §3.1) and so does every recon overlay.

**Owns:** nodes and edges; hop distance from the player's entry point (proximity *is* graph hop distance, `../design/01-core-resources.md` §3.1); the recon knowledge ladder; the defense-profile ring; the engaged node during a breach.

Rendering is entirely `es-node` (`01-visual-language.md` §8.8) — shape for node kind, ring weight for defense tier, fill for live/dormant, badges for miner/canary/honeypot/yours, states `es-state-unknown → sniffed → mapped → analyzed → breached` with `es-state-suspected-trap` and `es-state-engaged` overlaid.

**Key interactions — fully keyboard-complete, because a graph is the easiest surface to accidentally make pointer-only:**

| Input | Action |
|---|---|
| `Tab` / `Shift+Tab` | next/previous node in a **deterministic** order: hop distance ascending, then address lexicographic |
| Arrows | pan |
| `+` / `−` | zoom (plain keys; suppressed while a text field has focus) |
| `Home` | fit to content |
| `Enter` | open the focused node's detail popover |
| `Space` | toggle selection |
| `Shortcut+F` | filter by address, kind, or state — the filter drives the traversal order too |

Deterministic traversal order is what lets a screen-reader user walk the graph at all, and it is what makes "the third node at two hops" a sentence two players can exchange.

**One rule from the source:** `../design/07-recon-tools.md` §3 makes recon optional and expensive, so **the map must render usefully with most nodes unknown** (`01-visual-language.md` §8.8). A map that only looks right fully scanned punishes the intended play pattern.

**The glyph key is a column beside the graph, not a strip beneath it** *(2026-07-28)*. Ten glyph/word pairs do not fit across the panel, and the horizontal scroll belongs to the *data area* rather than to the panel — so the tail of the strip ran off the right edge with no way to reach it, and the entries that vanished were `░░` (contact) and `··` (beyond), the two dimmest states and the two most in need of naming. A column has a bounded width and an unbounded run of entries, which is the shape this data has.

**The sweep ladder shows the rules' verdict** *(2026-07-28)*. All three rungs are always *offered* and always pressable; the two the player has not bought read as `LOCKED`, dimmed, with a tooltip naming the tool, its price, and what the tier buys over the base — §5's rule that a gate is never a generic "locked", applied to the one panel that had been showing a gate as nothing at all. ⚠ The verdict arrives through `GameSession.sweepOptions()` and is **never computed in the view** (C4); an *absent* verdict — a session that cannot reach the rules — is rendered as offered, not as locked, because asserting a gate nobody asserted is the same violation arriving by the back door. It is deliberately not `setDisable(true)`: a disabled JavaFX node is skipped by picking and shows no tooltip, which would remove the explanation at the moment it is wanted. The tooltip's first sentence is Invariant **I2** — same reach, no tier buys reach at any price.

**Initial focus:** the last focused node, else the entry node.
**Fed by:** `../design/07-recon-tools.md` (all six overlays); `../design/09-defense-and-hardening.md` §1 (ring weight); `../design/05-hacking-minigame.md` §3.1 (Traversal); `../design/01-core-resources.md` §3.1 (hop distance).

---

### 2.6 `recon` — Recon

**Purpose:** the reading surface for recovered material — the human-read input the puzzle depends on, and the entire delivery channel for the story (`../design/00-vision-and-pillars.md` §3, pillar 5).

**Owns:** recovered logs, emails and database records; their source attribution; search and cross-reference across everything the player has recovered.

Built from `es-log-line` with `es-state-recovered` (`01-visual-language.md` §8.7) — visually distinguished from system output because provenance of *narrative* matters. This is also the window where the **[PROPOSAL]** diegetic Eye skin applies (`00-client-overview.md` §3.3, **CL-3**): recovered Eye documents render their *content pane* in The Eye's institutional chrome. Scope limit unchanged — content panes only, never the player's own controls, same contrast floor.

**Key interactions:** `Shortcut+F` searches the open document; `Shortcut+Shift+F` searches **all** recovered material and raises this window; selecting an identifier offers *find in `audit` / `netmap` / `ledger`* (§3.3 cross-window links). Pin and tag are local, client-owned annotations.

**Why cross-reference is a feature and not a convenience:** `../design/05-hacking-minigame.md` §3.2 hangs Invariant I10 on it — the Traversal class hides the objective among decoys *distinguishable only by cross-referencing recovered logs*. If moving from a log line to the node it names is slow, the anti-bot property of the puzzle is a chore rather than a skill.

**Initial focus:** the document list.
**Fed by:** `../design/05-hacking-minigame.md` §3.2; `../design/14-world-and-narrative.md` §3; `../design/07-recon-tools.md` (what recon yields).

---

### 2.7 `audit` — Audit

> **Established content requirement.** `../design/04-mining.md` §3.1: manual investigation costs **zero compute**, finds *anything* including rootkit-wrapped miners, *"because the discrepancy is always present in the data"* — and *"this is a hard implementation requirement: the process/connection/storage views must be real, consistent data — not decorative."* It is also named as the game's second-strongest tutorial vector, which makes it the spine of pillar C6.

**Owns** three tables and the scan controls:

| Table | Unix analogue | Columns |
|---|---|---|
| Processes | `ps` | pid · owner · cycles · state · started · command |
| Connections | `netstat` | local · remote · state · **owning process** (may be blank — that blank is the tell) |
| Storage | `df` / `ls` | mount (the three tiers) · used / capacity · delta since last audit |

Scan controls, priced at the point of commitment per pillar C1, with the figures from `../design/04-mining.md` §3.2: **Quick** 5 cycles / ~30s, **Full** 15 / ~2m, **Thorough** 35 / ~6m. Each shows its cycle cost *and* the projected recovery time at the rig's current load, because `../design/04-mining.md` §3.2's whole design is that *"scanning aggressively while overextended is punishing; scanning while lean is cheap"* — and that asymmetry is invisible unless the window states it before the player commits.

> **The rule that keeps this window honest: `audit` never flags a discrepancy the player is meant to find.**
>
> It provides arithmetic *tools* — a selection sum in the status bar, sortable columns, cross-linking a connection to its process — and renders no verdict. A "find anomalies" button is a bot that solves the puzzle for the player (Invariant I10) and a client asserting a conclusion the server did not send (C4), in one control. The tables must be sortable, filterable, copyable and cross-linkable; they must not be summarised, deduplicated, or helpfully annotated.

**Initial focus:** the process table.
**Fed by:** `../design/04-mining.md` §3 (both discovery paths), §5 (the four responses); `../design/09-defense-and-hardening.md` §2 (Rootkit Wrapper — hidden from scans, never from audit); `../design/01-core-resources.md` §1.1 (the consumer list).

---

### 2.8 `mining` — Mining

**Purpose:** the two mining systems that share a name (`../design/04-mining.md`), kept visibly separate because almost every rule differs.

**Owns:**

- **Self-mining** — the allocation control, block progress (`es-gauge-block`), effective rate (`40 EC/hr` at a full 100-cycle rig), and the three structural guarantees stated plainly in the window: silent, zero heat, unseizable (Invariant I4). Saying so is not flavour; `../design/04-mining.md` §1.1 makes self-mining the income floor and the productive off-ramp from a hot state, and a player who does not know it is safe will not use it as one.
- **The deployed network** — per miner: tier (T1–T3), host, yield, **buffer fullness** (`es-gauge-buffer`), control-channel cost (3 cycles each, permanent), rootkit state, last audit.
- **Sweep exposure** — the current per-hour figure driven by the heat band, from `../design/04-mining.md` §4's table (2% / ~8% / ~25% / ~45% / ~60%), rendered as received.

**The one interaction that needs care:** reallocation. Pulling cycles off mining mid-block forfeits that block's progress entirely (**[PROPOSAL]**, `../design/04-mining.md` §1.3), so the allocation control sits directly beside the block gauge and the confirmation states the forfeit in cycles and in EC. Inline confirmation, not a modal (`01-visual-language.md` §5.3).

**Buffer fullness is the whole crack-timing bet** (`../design/04-mining.md` §5.1) seen from the deployer's side: a buffer near its 4-hour cap is income the player is failing to collect *and* the prize a host would seize. It reads as a gauge, not a number, for that reason.

**Initial focus:** the allocation control.
**Fed by:** `../design/04-mining.md` §1, §2, §4, §5; `../design/07-recon-tools.md` (Provenance Tracer audits); `../design/11-rig-infrastructure.md` (Cuckoo Patch, Firmware Implant, Worm Module).

---

### 2.9 `storage` — Storage

**Purpose:** the vault, the inventory, and the risk gradient they sit on.

**Organised by exposure, never by item category** (`00-client-overview.md` §6.1). `../design/01-core-resources.md` §6 defines the three tiers as a strict capacity/exposure trade, and sorting by item type would bury the only property that decides anything. Three sections in exposure order, each with its capacity gauge and its mandatory padlock glyph (`01-visual-language.md` §2.2.6):

| Section | Tier | Exposure | Glyph |
|---|---|---|---|
| Encrypted Vault | `vault` | never exposed | closed padlock |
| Standard Storage | `standardStorage` | exposed while online | open padlock |
| High-Hackable Zone | `highHackableZone` | always exposed, raidable offline | broken padlock |

**Owns:** item cards (`es-item-card`) with gate badge, cost row, tier chip, provenance chip and socketed state; per-tier capacity; the move action.

Two states carry disproportionate weight and must be loud:

- **`es-state-socketed`** — assigned to a bot, therefore *out of the vault and mid-risk* (`../design/01-core-resources.md` §6, `../design/10-botnets.md` §1). Safety and productivity are mutually exclusive by design; the UI's job is to make the moment that trade happens unmissable.
- **`-es-provenance-*`** — this is the one window where the **client computes and can therefore be believed** (`00-client-overview.md` §1.1, `../architecture/04-item-provenance.md` §6). A per-item and per-tier `verify` action re-walks the chain offline. `unverified` and `broken` never share a colour: today `unverified` is the *common* case, because external DID→key resolution is stubbed (`../design/15-open-questions.md` **W-1**).

**Moving an item is never drag-only.** Select and `Enter` opens the move menu. Pillar C5 bans drag as the sole path for anything, and keyboard completeness is a floor (`00-client-overview.md` §3.5).

**Initial focus:** the vault section.
**Fed by:** `../design/01-core-resources.md` §6; `../design/10-botnets.md` §1 (socketing); `../architecture/04-item-provenance.md`; `../design/09-defense-and-hardening.md` (Honeypot Stash, Cold Storage Expansion).

---

### 2.10 `ledger` — Ledger

**Purpose:** the public ethecoin ledger. **This is an investigator surface, not a transaction list.**

`../design/01-core-resources.md` §2.2 is unusually direct that the public ledger is *"a gameplay feature, not blockchain flavor: it gives investigators — player and NPC — something to work with,"* and `../design/12-identity-and-social.md` §3 makes EC-flow analysis one of the four raw materials of an informant evidence case. So the window is built to be *used as evidence*.

**Owns:** the EC balance; the ledger as `es-ledger-row` (`01-visual-language.md` §8.5) — timestamp, direction, amount, counterparty, reason, traceability chip, authority badge; transfers; Dead Drop.

**Interactions that make it an investigator tool rather than a receipt:**

| Affordance | Why |
|---|---|
| Filter by counterparty, amount range, time range, traceability | The four axes an actual flow analysis uses |
| **Follow** — pivot to a counterparty's visible flows | Building a chain is the verb; making the player re-filter each hop would tax the mechanic out of use |
| Every field selectable and copyable | A player who cannot copy a handle cannot cross-reference it in `comms` or `recon` |
| Sort and re-sort without losing the selection | — |

**`es-state-untraceable`** is the interesting row: a Dead Drop transfer has *no public-ledger entry*, and the absence is the information (`../design/08-stealth-and-noise.md` §1). It renders with `-es-ec-untraceable` and the mandatory `no ledger entry` label, never as a gap the player has to notice.

**Initial focus:** the row list, not the filter field — digits typed immediately after a raise should jump rows, not become a filter query.
**Fed by:** `../design/01-core-resources.md` §2.2; `../design/08-stealth-and-noise.md` (Dead Drop); `../design/12-identity-and-social.md` §3 (evidence).

---

### 2.11 `market` — Market

**Purpose:** gated offerings, with the *blocking gate* surfaced per item.

`../design/02-unlock-gates.md` §4 already flags five gate classes as possible cognitive overload (OQ-2). `00-client-overview.md` §6.1's mitigation is the design of this window: **never a generic "locked."** Every offering carries a `es-gate-badge` naming which of the five gates blocks it and stating the requirement in words — `400 EC`, `Cold Storage Expansion schematic`, `Sickle standing: trusted`, `Logic class, tier ≥ 3, live target`, `heat ≥ moderate`. Split gates show both components, ceiling component first (`01-visual-language.md` §8.9).

**Two vendor rails, both always listed:** cold-reachable and hot-reachable. `../design/02-unlock-gates.md` §2.5 runs the heat gate in both directions — respectable fixers do not meet wanted people, and black-market brokers are only reachable while hot, which is the only sanctioned route to zero-days. Listing the unreachable rail with its heat-state badge makes "what would going hot open" a visible decision instead of folklore. Reachability is a **server verdict rendered as received** (C4); the client never evaluates a gate.

**Owns:** offerings, prices, and the three economy-facing stats every tool carries — EC, **compute**, **noise** — shown with equal weight (`01-visual-language.md` §8.6), because `../design/06-intrusion-tools.md` §5 makes compute and noise the real balance levers and a card that whispers them teaches the wrong economy.

The offering catalogue is content, not code, and is currently empty (`../design/15-open-questions.md` **W-3**). The window must render an honest empty state (`01-visual-language.md` §9.5) rather than an apologetic one.

**Initial focus:** the offering list.
**Fed by:** `../design/02-unlock-gates.md`; `../design/06-intrusion-tools.md`, `07-recon-tools.md`, `09-defense-and-hardening.md`, `11-rig-infrastructure.md` (the tool tables); `../design/03-economy.md` (prices).

---

### 2.12 `botnet` — Botnet

**Purpose:** frames, instances, and the two timers that make a botnet feel like overextension you chose.

**Owns:**

- **Frames vs. instances**, visually separated, because the distinction is the whole loss model. A **frame is a blueprint** (`../design/10-botnets.md` §2); an instance is built at EC cost. Loss destroys the instance and its socketed tools, **never the blueprint** (Invariant I11).
- Per instance: frame type, compute reservation (Recon 8 · Miner 10 · Sentinel 14 · Breacher 22 · Mimic 12 · Scavenger 9), socketed tools, current task, noise contribution.
- **The backlog timer** — a per-item response window that shrinks as bot count rises (`../design/10-botnets.md` §1). Outside the trace meter this is the most urgency-laden thing in the client, and it gets rung-3 treatment on the attention ladder (§6.2).
- **Split-attention state** — the parallel, non-queuing penalty applied to *every* simultaneous engagement (`../design/10-botnets.md` §1b). It must be shown as an active modifier on the engagements it is degrading, not as a status line, or the player will attribute a lost breach to the breach.

**The loss surface is a C3 obligation.** When a bot is lost, the window states — in one place, itemised — what was destroyed (instance + each socketed tool, with EC value), what was **not** (the frame, by name), and whether tier-gated schematic contribution material dropped (`../design/10-botnets.md` §1a, Invariant I13). A player who believes they lost a late-game schematic will stop running bots, and the design depends on them running bots.

**Initial focus:** the instance list.
**Fed by:** `../design/10-botnets.md`; `../design/01-core-resources.md` §1.1, §3.1; `../design/11-rig-infrastructure.md` (Isolated Partition).

---

### 2.13 `defense` — Defense

**Purpose:** the armed loadout and its permanent compute draw. After the rig monitor this is the second-most important compute surface in the client.

**Owns:** every armed defense with its while-armed cycle cost — Firewall T1/T2/T3 (5 / 9 / 15), Canary Token (1), Tarpit (8), Honeypot Stash (12), Auto-Counter Daemon (18), Detection Array T1–T3 (6 / 14 / 25), Rootkit Wrapper (2 per wrapped miner) — plus a **running total against rig capacity**, mirrored from the rig monitor.

That total is the window's reason for existing. `../design/09-defense-and-hardening.md` §3 works the arithmetic deliberately: a paranoid loadout is **67 permanent cycles of 100**, leaving 33 for offence, mining and stealth combined, and *"you cannot be fully defended and fully offensive at once. This is intended and load-bearing; do not 'fix' it by discounting defensive compute."* A UI that shows each defense's cost but never the sum quietly undoes that.

**Also owns:** canary trips. A trip both alerts the owner and **tags the toucher's handle** (`../design/09-defense-and-hardening.md` §2), which is the raw material of an evidence case — so a trip row links straight through to `comms` (§3.3 cross-window links), and to `recon` for the captured evidence itself.

**Initial focus:** the armed list.
**Fed by:** `../design/09-defense-and-hardening.md`; `../design/12-identity-and-social.md` §3 (canary → evidence); `../design/04-mining.md` §3 (Detection Array).

---

### 2.14 `identity` — Identity

**Purpose:** `whoami`. Who the server currently believes you are, and at what cost.

**Owns:** handle; DID (mono, middle-truncated, fully copyable — `01-visual-language.md` §9.3); **personal heat** and **server heat** as separate scope-labelled band chips; **`factionReputation`**; burner handles and their separate heat; Ghost Protocol.

> **`validatorReputation` never appears in this window, or in any player-facing window.** `../design/glossary.md` marks the collision explicitly and `CLAUDE.md` repeats it: faction standing and federation validator trust are unrelated quantities that must never share a field, a column, a label, a colour, or a primitive (`01-visual-language.md` §2.2.10). Validator reputation is server infrastructure and belongs to a federation-operator surface this document does not define.

**The one genuinely destructive control in the client:** Ghost Protocol wipes personal heat entirely and forfeits the handle, leaderboard position and all reputation tied to that name (`../design/01-core-resources.md` §4.3). It gets a window-scoped `ModalPane` (`01-visual-language.md` §5.3) that requires typing the handle being destroyed. `../design/08-stealth-and-noise.md` §3 wants this to be *"deliberately painful enough that laying low is usually the better choice"* — the friction is the design, not a safety net bolted onto it.

Faction abandonment (reputation reset, heat spike, forfeiture of faction-specific tools, `../design/01-core-resources.md` §5) gets the same treatment. Note that forfeiture is currently a no-op stub (`../design/15-open-questions.md` **W-5**), so the confirmation must state what *will* be forfeited from server-sent data rather than from a client-side list.

**Initial focus:** the heat chips.
**Fed by:** `../design/01-core-resources.md` §4, §5; `../design/12-identity-and-social.md` §1; `../design/08-stealth-and-noise.md` §3; `../architecture/02-identity-and-auth.md`.

---

### 2.15 `comms` — Comms ✚

**Purpose:** the social and informant layer — where paranoia lives (`../design/12-identity-and-social.md`).

**Owns:** contacts (NPC and player) with their heat-state reachability; recovered and received messages; **Informant Dossiers** and the compiled evidence they hold; open cases with their evidence threshold; the accusation and mass-vote surfaces.

**The evidence view is the hard part**, and it is a pure C4 problem. `../design/12-identity-and-social.md` §2.1 defines two removal paths with deliberately different rigour, and *whether the threshold is met is a server verdict*. The client shows the compiled material — canary handle tags from `defense`, EC flow analysis from `ledger`, dossier output, behavioural data from recon (`../design/12-identity-and-social.md` §3) — and renders the threshold state as received. It never computes "you have enough."

**The mass-vote override must not feel routine.** It is *"deliberately dangerous"*: partial evidence as an eligibility gate plus a server-wide Sickle supermajority, real costs regardless of outcome, no reversal, deliberately unlikely to succeed. So: it is **not reachable from a context menu**, only from an opened case view; the confirmation states the irreversibility and the costs-regardless-of-outcome in words; and the UI never displays a predicted success probability, because a number would turn a political act into an expected-value calculation. `../design/12-identity-and-social.md` §2.1 is explicit — *"do not 'balance' the override into reliability; its unreliability is the point."*

**Initial focus:** the case/message list.
**Fed by:** `../design/12-identity-and-social.md`; `../design/09-defense-and-hardening.md` §2 (canary tags); `../design/01-core-resources.md` §2.2 (ledger trails).

---

### 2.16 `settings` — Settings ✚

**Purpose:** every client-owned preference, in one place, plus the layout escape hatch.

| Section | Contents | Owned by |
|---|---|---|
| Appearance | theme family and variant, default density, accent behaviour | `00-client-overview.md` §3–§4, `02-platform-native-themes.md` |
| Accessibility | reduced-motion override, high contrast, focus-ring width, **alert-ladder ceiling** (§6.2) | this doc + `01-visual-language.md` §7.4 |
| Teaching | level: `explain` / `terms` / `off`; the searchable term index | `00-client-overview.md` §5.2 |
| Layout | mode (multi ↔ docked), workspaces, per-window density, **reset window layout** | §3–§5 |
| Input | shortcut remapping — with `Shortcut+0` fixed and unremappable | §3.5 |
| Profile | server, session, sign-out | `../architecture/02-identity-and-auth.md` |
| Operator | handle, and **the rig's hostname** — the two halves of the shell prompt | this doc |

**The hostname is a client setting and lives beside the handle** *(2026-07-28)*, because the two are the two halves of one string: the command strip reads `handle@hostname.local:~$` — who you are, then where you are, which is the order every terminal and every SSH session uses. Nothing in the rules reads it, no gate depends on it and no ledger entry records it, which is why it belongs in the profile next to the theme rather than anywhere near a save file. Validation is **RFC 1123's** and not this game's — letters, digits and hyphens, no leading or trailing hyphen, 63 characters — so an underscore is refused even though nothing here would break on one, and the refusal says whose rule that is. `.local` is mDNS and is appended rather than stored; a typed `.local` is stripped. Terminal half: `hostname(1)`, behaving like the real one — no argument prints the short name, an argument sets it (**C1**).

**Reset window layout** is a required escape hatch, not a convenience: it discards saved geometry for the current display signature and re-places every open window at its default. It is also reachable as `layout reset` in the command palette from any window, and as a `--reset-layout` launch flag. The flag exists because the palette needs a focused window to be invoked from, and the startup repair pass (§3.8) is what guarantees one always exists — the flag is the belt to that pair of braces.

**Initial focus:** the section list.

---

### 2.17 `switcher` — Windows

**Purpose:** the reason thirteen windows are navigable rather than lost. It is chrome, not a tool: it holds no game state and has no source system in `00-client-overview.md` §6.1's table.

**Owns:** one row per window — open / closed / minimised, alert rung, density, and **the window's headline value**. The headline is what turns a window list into a dashboard:

| Window | Headline |
|---|---|
| `rig-monitor` | `72 / 100 cycles` |
| `terminal` | `layer 2 of 4 · tier 3` or `idle` |
| `netmap` | `14 nodes known · 3 unmapped adjacent` |
| `recon` | `3 unread` |
| `audit` | `last audit 6m ago` |
| `mining` | `40 EC/hr · 3 buffers ≥ 75%` |
| `storage` | `vault 4 / 6` |
| `ledger` | `1,240.00 EC` |
| `market` | `2 offerings newly reachable` |
| `botnet` | `3 running · 2 pending` |
| `defense` | `armed 4 · 41 cycles` |
| `identity` | `PERSONAL · moderate` |
| `comms` | `1 open case` |
| `settings` | — |

Headlines carry authority badges like any other server-owned value (`01-visual-language.md` §8.4): `—` when unknown, never `0`.

**Interactions:** `Enter` raises (opens if closed) · `Space` toggles open/closed · type-to-filter · digits jump to the canonical position · `Shortcut+Shift+J` raises the switcher itself from anywhere.

**Always-on-top:** off by default (`01-visual-language.md` §5.4 permits exactly one default), but this is the one window where an always-on-top toggle in its own header earns its pixels, because a player using it as a rail wants it pinned. Whether it should use `StageStyle.UTILITY` is **PN-10** in `02-platform-native-themes.md` and is not re-decided here.

**Initial focus:** the row list.

---

### 2.18 What opens on first run, and why that set

**`rig-monitor` (panel form) and `switcher`. Nothing else.**

- `rig-monitor` because `00-client-overview.md` §2 (C2) makes it the window that opens by default and `../design/01-core-resources.md` §1.4 makes it mandatory.
- `switcher` because it is chrome rather than a tool — the reading under which it is not an exception to C2 — and because the alternative failure is severe in both directions. Opening all fifteen windows is the single worst first impression the multi-window model can make; opening only the rig monitor leaves a new player with no evidence the other fourteen exist.

Placement on the primary screen's `getVisualBounds()` (**verified** on `javafx.stage.Screen`): rig monitor top-right inset `SPACE_6` (24px), switcher top-left inset `SPACE_6`. Both corners, nothing in the middle, because the middle is where the onboarding flow will put the terminal.

The onboarding flow (`07`) opens `audit` and `terminal` when the tutorial crack begins — `../design/04-mining.md` §5.1 names cracking as *"the strongest early-game teaching vector"* and says the tutorial should plant a weak scripted miner early, which means the player's first two tools are the one that finds it and the one that cracks it. Those opens are user-initiated in the sense that matters: they are consequences of the player advancing the flow, not of a server event (§6.3).

**On every subsequent run**, the saved layout is restored (§3.7) and the first-run set is not re-applied.

---

## 3. Window management

### 3.1 Opening

Four routes, all equivalent, all user-initiated:

1. The switcher — `Enter` or click.
2. The accelerator — `Shortcut+<n>` opens if closed, raises if open (§3.5).
3. The command palette — `open map`, or `win map`; the palette's grammar belongs to `04`.
4. A cross-window link (§3.3).

A window opens at its saved geometry for the current display signature, else its default, run through the placement validator (§3.8). `openNew()` on the scaffold's `ToolWindow` — a *second* window for the same tool — is deliberately out of scope for v1: two `netmap` windows would need two pane instances and therefore two divergent view states, and every cross-window link would become ambiguous about which one to raise. Tracked as **WL-8**.

**No window is ever opened by a server event.** A new `Stage.show()` takes focus on every platform. §6.3 makes this a hard rule.

### 3.2 Closing

Closing a tool window is free and lossless: layout is client-owned (`00-client-overview.md` §1.1) and every value in it is server-owned, so there is nothing to save and nothing to confirm.

Two exceptions:

- **`rig-monitor` is not closable.** `Shortcut+W` and the close button collapse it to the strip form (§2.3).
- **A window hosting a live engagement warns before closing** — inline, in the window, not a modal (`01-visual-language.md` §5.3). Closing it does not end the engagement; the engagement is server-owned (I14) and the window is a view. The warning says exactly that, because a player who believes closing the terminal aborts the breach has been taught something false about where authority lives.

Unsubmitted draft state — an unsent loadout, an unsent allocation change, a half-typed probe (`00-client-overview.md` §1.1) — blocks a *silent* close: the window offers `discard` / `keep open` inline. It never blocks a close the player insists on.

### 3.3 Focusing, raising, and cross-window links

**Raising is never stealing** (`00-client-overview.md` §6.2). Exactly three things may raise a window:

1. An explicit player action (accelerator, switcher, palette).
2. A **cross-window link** the player clicked or activated.
3. A mode switch (§5.3) or workspace restore (§4).

A background *event* never raises anything. It marks the switcher entry and the window's own indicator and waits (§6).

**Cross-window links are first-class**, and the catalogue implies a specific set:

| From | Link | To |
|---|---|---|
| `ledger` row → counterparty | | `identity`, or `comms` if a case exists |
| `defense` canary trip → tagged handle | | `comms`, on the evidence case |
| `defense` canary trip → captured evidence | | `recon` |
| `audit` connection → owning process | | `audit`, same window, other table |
| `audit` discovered miner → host | | `mining` |
| `recon` log line → node address | | `netmap`, focused on that node |
| `recon` log line → handle | | `comms` |
| `netmap` node → its recovered material | | `recon`, filtered |
| `market` offering → the gate blocking it | | `identity` (reputation/heat) or `storage` (schematic) |
| `botnet` instance → its socketed tools | | `storage`, filtered to `es-state-socketed` |
| `mining` miner → its host node | | `netmap` |

Under pillar C5 a player should never have to *find* the window that explains what they are looking at. In docked mode these links activate a tab instead of raising a `Stage`; the link table is identical (§5.4).

**On raise, focus goes to the window's `initialFocus`** — declared per window in §2. Getting this wrong is how a raise becomes a keystroke sink: raising `ledger` into its filter field means the digits the player types to jump rows become a search query instead.

### 3.4 The switcher as the answer to loss

Specified in §2.17. Two properties make it load-bearing rather than a nicety:

- **It lists closed windows too.** A taskbar shows what is open; this shows what *exists*. Discovery and navigation are the same surface.
- **Its order is canonical and fixed** — the §2.1 column, not most-recently-used. This is a deliberate resolution of an ambiguity in `00-client-overview.md` §6.3, which binds `Shortcut+1…9` to *"tool window n from the switcher order"* without saying whether that order is stable. If it were MRU, `Shortcut+2` would mean a different window every few minutes, which destroys the only property a numeric accelerator has. Tracked as **WL-3**.

### 3.5 Keyboard

All bindings use JavaFX's `KeyCombination.SHORTCUT_DOWN`, which resolves to ⌘ on macOS and Ctrl on Windows/Linux — written `Shortcut`. This is the one mechanism that gives correct platform behaviour from a single codebase (`00-client-overview.md` §6.3).

**Global — installed on every window's `Scene`:**

| Binding | Action | Source |
|---|---|---|
| `Shortcut+0` | Raise `rig-monitor` — **never remappable** | `00-client-overview.md` §6.3 |
| `Shortcut+1` | `terminal` | this doc |
| `Shortcut+2` | `netmap` | " |
| `Shortcut+3` | `recon` | " |
| `Shortcut+4` | `audit` | " |
| `Shortcut+5` | `mining` | " |
| `Shortcut+6` | `storage` | " |
| `Shortcut+7` | `ledger` | " |
| `Shortcut+8` | `botnet` | " |
| `Shortcut+9` | `defense` | " |
| `Shortcut+Shift+M` | `market` | " |
| `Shortcut+Shift+I` | `identity` | " |
| `Shortcut+Shift+P` | `comms` (mnemonic: people) | " |
| `Shortcut+Shift+C` | `calc` | " |
| `Shortcut+Shift+J` | `switcher` (mnemonic: `jobs`) | " |
| `Shortcut+,` | `settings` — the macOS Preferences convention, honoured on all three | " |
| `Shortcut+\`` | Cycle open tool windows | `00-client-overview.md` §6.3 |
| `Shortcut+K` | Command palette | " |
| `Shortcut+F` | Find within the focused window | " |
| `Shortcut+Shift+F` | Find across all recovered material (raises `recon`) | this doc |
| `Shortcut+Shift+D` | Toggle multi-window ↔ docked | `00-client-overview.md` §6.3 |
| `Shortcut+Shift+T` | Theme switcher | " |
| `Shortcut+Shift+E` | Cycle teaching level | " |
| `Shortcut+Shift+C` | Toggle density for the focused window | " |
| `Shortcut+Shift+1…9` | Apply workspace 1–9 | this doc, §4 |
| `Shortcut+.` | Abort the current breach — **always confirms** | `00-client-overview.md` §6.3 |
| `Shortcut+/` | Open the `es-term` definition for the focused term | `01-visual-language.md` §8.10 |
| `Shortcut+W` | Close the focused tool window (`rig-monitor` → collapse to strip) | this doc |
| `Shortcut+M` | Minimise the focused window (platform standard) | " |
| `Shortcut+Q` | Quit — confirms if an engagement is live (§3.10) | " |
| `Alt` held | Reveal attribution overlays on every visible meter | `00-client-overview.md` §6.3 |
| `Escape` | Dismiss popover / palette / inline confirmation | this doc |

**Shared within any window:**

| Binding | Action |
|---|---|
| `Tab` / `Shift+Tab` | Focus traversal, with a 2px focus ring outside the control's bounds (`01-visual-language.md` §1.5) |
| `Enter` | Activate the focused item |
| `Space` | Toggle selection |
| `Shortcut+A` | Select all in the focused table or buffer |
| `Shortcut+C` | Copy selection — every log line, ledger row and identifier is real selectable text |
| `Home` / `End` | Extremes; on `netmap`, `Home` fits to content |

**Three design rules behind the table:**

- **Only time-critical actions get a chord.** Saving a workspace, exporting, remapping — palette and menu only. A chord is a scarce resource and spending it on something the player does once a month makes the ones they need under a timer harder to remember.
- **`Alt`-held reveal and `Alt`-in-a-chord must not collide.** No binding in this document uses `Alt`; the attribution reveal fires on `Alt` held alone for ≥200ms with no other key down, and any chord suppresses it. That is why workspaces took `Shortcut+Shift+1…9` rather than the more obvious `Shortcut+Alt+1…9`.
- **Everything the player can do with a pointer, they can do with the keyboard**, in both layout modes (`00-client-overview.md` §3.5, floor 4).

**Hit targets:** WCAG 2.2 SC 2.5.8 (Level AA) requires pointer targets of at least **24×24 CSS px** (**verified**). This client's floors are well above it: 32px standard, **40px comfortable / 36px compact for any time-critical control, never below 32** (`01-visual-language.md` §4.3) — abort, defend, reallocate, disarm.

### 3.6 The accelerator-installation trap

`Scene.getAccelerators()` returns `ObservableMap<KeyCombination, Runnable>` (**verified**) — and accelerators are **per-`Scene`**. In a fifteen-window client that means the global table has to be installed on every scene, or `Shortcut+0` silently stops working from the ledger window and nobody notices until a playtest.

**Required:** one `GlobalAccelerators.installOn(Scene)`, called by the window factory, plus a test that enumerates the registry and asserts every registered window's scene carries the complete global map. This is exactly the class of defect that is invisible in the window you are developing and fatal in the one you are not.

**And the honest limitation:** JavaFX exposes no global (system-wide) hotkey API. Every binding here requires *some* client window to hold OS focus. `Shortcut+0` cannot raise the rig monitor from inside a browser. There is no fix that does not leave the toolkit; **WL-9** tracks whether an OS-level badge (`02-platform-native-themes.md` **PN-4**) is the only honest answer.

### 3.7 Remembering geometry

Persisted locally per profile, never round-tripped to the server (`00-client-overview.md` §4.5), in the client's own profile directory — `%APPDATA%\The Eye and Sickle\` · `~/Library/Application Support/The Eye and Sickle/` · `$XDG_CONFIG_HOME/eyeandsickle/`. `00-client-overview.md` §7 makes "no filesystem access outside the client's own profile directory" a security boundary.

**`layout.json`, sketched:**

```
{ "schemaVersion": 1,
  "mode": "multi",                        // or "docked"
  "activeWorkspace": null,
  "switcherOrder": ["terminal","map","recon","audit", ...],
  "displays": {
    "d41d8cd98f00b204": {                 // display signature, §3.9
      "windows": {
        "rig-monitor": { "x":2456,"y":24,"w":420,"h":560,
                         "state":"normal","alwaysOnTop":true,
                         "density":"comfortable","form":"panel" },
        "terminal":    { "x":120,"y":80,"w":880,"h":620, ... }
      } } },
  "workspaces": [ /* §4.1 */ ] }
```

**Rules:**

- **Geometry is keyed by (display signature, window id).** A desk arranged for a docked three-monitor setup is a different desk from the one arranged on a train, and overwriting one with the other is the most common complaint about every multi-window app that gets this wrong.
- **Saved geometry is never deleted by a repair.** §3.8 writes a *repaired* placement under the current signature and leaves the original intact under its own, so replugging a monitor restores the desk exactly.
- **A corrupt or unreadable file falls back to §2.18 defaults, silently** (`00-client-overview.md` §4.5). A preferences file must never block sign-in.
- **Writes are debounced** — 1000ms after the last geometry change, plus on clean shutdown. A player dragging a window generates a change event per frame.

### 3.8 When a saved position is no longer a place

The monitor was unplugged. The resolution changed. The laptop was undocked. The dock was rearranged. All four produce the same failure: a rect that describes nowhere.

**The validator.** A saved frame is valid iff **both** hold:

1. `Screen.getScreensForRectangle(x, y, w, h)` is non-empty (**verified** static method on `javafx.stage.Screen`), **and**
2. the intersection with the union of all screens' `getVisualBounds()` contains a **grab region** — at least **96×24 logical px of the window's top edge** — *and* at least **20% of the window's area**.

The grab region is the operative half. A window can be 95% on-screen and still unreachable if the strip the window manager lets you drag is the part hanging off. 96×24 is a conservative approximation of a title-bar handle across the three platforms.

**The repair, in order:**

1. Choose the target screen: the one with the largest intersection with the saved rect; if there is none, the screen containing the pointer; if that cannot be determined, `Screen.getPrimary()`.
2. Clamp size to `min(saved, visualBounds − 2 × SPACE_6)`, then up to the window's declared minimum. If the minimum does not fit, the minimum wins and the window overhangs — a window that is too big to fit is still usable; a window shrunk below its minimum is not.
3. Clamp position into the visual bounds, inset `SPACE_6`.
4. **Cascade**: offset by `SPACE_6` (24px) per already-repaired window this pass, so three repaired windows do not land in one perfect stack.
5. Write the repaired rect under the *current* signature. Leave the original untouched (§3.7).

**Then say so.** A single rung-1 mark (§6.2) in the switcher: `3 windows repositioned — display removed`. Never a dialog, never per window, and never silence — silently moving windows is the client editing something the player owns (`00-client-overview.md` §1.1).

**Also:** windows are validated at startup *and* whenever the screen set changes (§3.9), and `settings` carries the reset (§2.16) for the case where the repair produces something the player simply dislikes.

### 3.9 Multi-monitor and DPI

`../design/00-vision-and-pillars.md` §6 names the second-monitor case explicitly, and `00-client-overview.md` **CL-8** flags mixed-scale multi-monitor behaviour as needing verification on all three platforms before geometry persistence is trusted. This section specifies the mechanism; **CL-8** remains the outstanding *verification*.

**Screens.** `Screen.getScreens()` returns an `ObservableList<Screen>` (**verified**) — attach a list-change listener, and on every change re-run the §3.8 validator against every visible `Stage`. **Repair only what fails.** A window still fully on a screen that did not change is not touched: the player put it there.

**Display signature.** JavaFX exposes no stable per-monitor identity, so one is synthesised: over `Screen.getScreens()` sorted by `(bounds.minX, bounds.minY)`, join `"{minX},{minY},{width},{height}@{outputScaleX}x{outputScaleY}"` and take the first 16 hex characters of the SHA-256. Two identical monitors swapped left-to-right hash the same and get each other's windows — accepted, because the §3.8 validator catches anything that lands unreachable and nothing else is harmful. **WL-2** tracks whether a stronger identity is worth native code.

**DPI.** `Window.outputScaleXProperty()` / `outputScaleYProperty()` exist since JavaFX 9 (**verified**), and the javadoc carries a caveat worth designing around: the property *"is updated asynchronously by the system at various times including: at some point during moving a window to a new Screen which may be before or after the Screen property is updated."* So:

- Drive scale-dependent work off the **`outputScale` properties**, not off a screen-change event. The ordering is explicitly unspecified.
- There is **no `Window.getScreen()`** (**verified** — the property does not exist on `javafx.stage.Window`). Screen membership must be derived: `Screen.getScreensForRectangle(window bounds)`, choosing the largest intersection. Write that once, in one helper.
- **No asset swapping is needed.** Icons are `SVGPath` constants (`01-visual-language.md` §6.2) and every size token is logical px, so a scale change re-rasterises vector geometry and nothing else. This is a design decision paying off: a raster icon set would need a DPI set maintained across three platforms and mixed-scale desks.
- Logical sizes are scale-independent by construction, so a window dragged from a 1× to a 2× display keeps its logical size and doubles its physical size — which is correct.

### 3.10 Quitting

**`Platform.setImplicitExit(false)`** (**verified**: with implicit exit true — the default — *"the JavaFX runtime will implicitly shutdown when the last window is closed"*).

Two reasons. First, `rig-monitor` collapses rather than closes, so "the last window" is a state that should be unreachable — relying on a default that fires in a state we believe impossible is exactly how a client exits during a breach. Second, quitting must be explicit and must be able to warn: `Shortcut+Q` with a live engagement running gets a window-scoped confirmation naming the engagement, because the engagement is server-owned and quitting does not abort it — leaving is not the same as aborting, and the outcomes differ (`../design/05-hacking-minigame.md` §2).

---

## 4. Workspaces

### 4.1 What a workspace is

A named, restorable arrangement. Crucially, **a workspace is mode-independent**: it carries both a multi-window arrangement and a docked arrangement, so `Shortcut+Shift+1` means the same *task* in both modes.

```
{ "name": "breach",
  "multi":  { "open": ["terminal","map","recon","rig-monitor","switcher"],
              "geometry": { "terminal": [0.00,0.00,0.46,1.00], ... },   // fractions of visual bounds
              "rigForm": "panel",
              "density": { "recon": "comfortable", "terminal": "compact" } },
  "docked": { "rail": "collapsed", "split": [0.62,0.38],
              "panes": [ ["terminal"], ["map","recon"] ],
              "active": { "1": "map" }, "tray": "expanded" } }
```

**Geometry is stored as fractions of the primary screen's `getVisualBounds()`**, not as pixels. A preset authored on a 3440×1440 ultrawide has to be usable on a 1440×900 laptop, and fractions are the only form that survives that. Each resulting rect is then clamped up to the window's declared minimum (§3.8 step 2), which is where the resolution tiers in §4.4 come from.

**What a workspace does *not* capture:** filters, scroll positions, map viewport, selection. Restoring an arrangement should not also rewind what the player was reading. Whether that is right is **WL-6** — a case can be made that an `investigation` workspace is far more useful if it remembers the ledger filter.

### 4.2 Restore semantics

Applying a workspace: open what is missing, move and resize what is open, close what is not in the set — with three exceptions that exist because a preset is a convenience and the player's work is not.

1. **`rig-monitor` is never closed** (it may change form).
2. **A window hosting a live engagement is never closed or resized below its minimum.** A breach workspace that closes the terminal running the breach is a bug with a friendly name.
3. **A window holding unsubmitted draft state is never closed.** It stays open, is placed where the preset would have placed it if it appears in the set, and is marked rung-1 in the switcher with `unsent changes`.

Applying a workspace is a player action, so it may raise and focus (§3.3). It is also the only bulk geometry change the client performs, which is why it is always explicit — there is no "auto-apply the breach workspace when a breach starts." That would move the player's windows at the worst possible moment (§6.4).

Saving: `workspace save <name>` in the palette, or Settings → Layout. No chord — saving is not time-critical (§3.5).

### 4.3 The three shipped presets

Fractions are `[x, y, w, h]` of the primary screen's visual bounds. Docked arrangements are in the same schema as §4.1.

**`breach` — `Shortcut+Shift+1`**
Serves the simultaneity requirement in `../design/05-hacking-minigame.md` §5: the active layer, the target graph, the flavour logs the human-read steps need, and compute + trace, all at once.

| Window | Fraction | Note |
|---|---|---|
| `terminal` | `[0.00, 0.00, 0.46, 1.00]` | the active layer; widest, leftmost, where the eye starts |
| `netmap` | `[0.46, 0.00, 0.34, 0.62]` | Traversal, and the engaged node |
| `recon` | `[0.46, 0.62, 0.34, 0.38]` | the cross-reference material |
| `rig-monitor` | `[0.80, 0.00, 0.20, 0.62]` | **panel** form, always-on-top; the trace gauge needs the height |
| `switcher` | `[0.80, 0.62, 0.20, 0.38]` | the triage list, beside the trace |

**`mining` — `Shortcut+Shift+2`**
Compute allocation is the subject, so the rig monitor is a first-class column rather than a corner.

| Window | Fraction |
|---|---|
| `rig-monitor` (panel) | `[0.00, 0.00, 0.22, 1.00]` |
| `mining` | `[0.22, 0.00, 0.48, 1.00]` |
| `audit` | `[0.70, 0.00, 0.30, 0.55]` |
| `ledger` | `[0.70, 0.55, 0.30, 0.45]` |

**`investigation` — `Shortcut+Shift+3`**
Following EC flows and building a case (`../design/12-identity-and-social.md` §3). Compute is not the subject here, so the rig monitor takes its **strip** form across the top — still permanently visible, per C2, but not occupying a column it does not earn.

| Window | Fraction |
|---|---|
| `rig-monitor` (strip) | `[0.00, 0.00, 1.00, 0.12]` |
| `ledger` | `[0.00, 0.12, 0.40, 0.56]` |
| `comms` | `[0.00, 0.68, 0.40, 0.32]` |
| `recon` | `[0.40, 0.12, 0.36, 0.88]` |
| `netmap` | `[0.76, 0.12, 0.24, 0.50]` |
| `identity` | `[0.76, 0.62, 0.24, 0.38]` |

Docked variants: `breach` — rail collapsed, split 62/38, panes `[terminal] [map, recon]`, tray expanded. `mining` — rail 220, split 70/30, panes `[mining, audit] [ledger]`. `investigation` — rail 220, split 45/55, panes `[ledger, comms] [recon, map, identity]`.

### 4.4 Resolution tiers

A preset that opens five windows on a 1366×768 laptop produces a pile, not a desk. Every preset therefore declares three plans, selected on the primary screen's visual-bounds width:

| Tier | Width | `breach` plan |
|---|---|---|
| Wide | ≥ 2400 | the five-window layout above |
| Standard | 1500 – 2399 | `terminal [0,0,.55,1]` · `map [.55,0,.28,1]` · `rig-monitor` panel `[.83,0,.17,.60]` · `switcher [.83,.60,.17,.40]`. `recon` stays closed — one keystroke away at `Shortcut+3`. |
| Tight | < 1500 | `rig-monitor` **strip** `[0,0,1,.12]` · `terminal [0,.12,.66,.88]` · `map [.66,.12,.34,.88]` |

The tight tier is why the strip form exists. At 1366×768 that strip is 1366×92, which comfortably clears its 420×88 minimum and puts compute and trace across the top of the screen where nothing can occlude them — the multi-window echo of the dock's rig strip (§5.2).

Below **1280** logical px wide, the client offers the **docked** breach workspace instead, once, dismissibly, in the switcher at rung 1. It offers; it never switches. (Related: `02-platform-native-themes.md` **PN-11** proposes the same one-time offer when a tiling window manager is detected.)

---

## 5. The single-window docked fallback

### 5.1 Status

> ⚠️ **CHANGED 2026-07-25 — this layout is now the DEFAULT, not the alternative.**
>
> Everything below still describes the layout correctly. What changed is which one a new player
> meets: `dockedLayout` defaults to `true`, so the client opens as one window with the tools as tabs.
> The multi-window desk described in §1–§3 is fully built and one setting away (Settings → Layout, or
> `dock` in the terminal), but it is no longer what a fresh profile gets.
>
> This inverts §1.1's reading of `../architecture/01` §1, which is an **Established** decision. It was
> changed on explicit direction, and the reasoning is logged in `../design/15-open-questions.md` §3.
> The short version: §2.3 of `07-accessibility.md` already required this layout to lose no
> functionality, so defaulting to it costs a new player nothing and spares them fifteen OS windows in
> their first thirty seconds. `../architecture/01` §1 should be amended to match.

> **Mandatory, flagged independently by two source documents.** `../architecture/01-tech-stack.md` §1: *"Window management needs an accessibility fallback: a single-window / docked layout for players who can't manage many OS windows under time pressure […]. Multi-window is the default and the fantasy; it must not be the *only* option."* `../design/05-hacking-minigame.md` §5 raises the same flag from the gameplay side, about the live breach specifically.

The *requirement* is Established; the design below is `[PROPOSAL]`.

It is worth being precise about what the fallback is for, because "accessibility fallback" undersells it. Window management under time pressure is a barrier for: players using screen magnification (where a second window is off the magnified viewport); players with motor impairments for whom dragging and precise clicking are expensive; players on small or single displays; players on tiling window managers, where fifteen floating windows are actively hostile; and players who simply find fifteen windows stressful. That is not a fringe. The docked mode has to be *good*, not merely present.

`00-client-overview.md` §6.4 sets the terms this section fills in: it is a **layout mode, not a second UI**; it is **switchable live**, including mid-breach; and *"whoever builds the second tool window builds the docked fallback in the same change, while it is still cheap."*

### 5.2 The shell

One `Stage`, id `dock`. Default **1440×900**, minimum **1024×640**, absolute floor **800×600** enforced with `Stage.setMinWidth/setMinHeight`.

```
┌──────────────────────────────────────────────────────────────────────┐
│  RIG STRIP                                              96px, fixed  │
│  72/100 cycles ▓▓▓▓▓▒▒░░  │ trace ▓▓▓░░░░░ │ noise ▓▓░ │ PERSONAL·mod │
│  Breacher 22 · Miner 10 · Recon 8 · avail 60 · 18 recovering 4m20s    │
├────────┬─────────────────────────────────────────────────────────────┤
│ RAIL   │  ┌ terminal ─┐                    ┌ map ─┬ recon ┐          │
│ 220px  │  │                                │                          │
│ (56    │  │      CONTENT — SplitPane of TabPanes                      │
│  icon) │  │                                │                          │
│        │  └───────────────────────────────┴──────────────────────────┘
├────────┴─────────────────────────────────────────────────────────────┤
│  ALERT TRAY                            28px collapsed / 200 expanded │
└──────────────────────────────────────────────────────────────────────┘
```

| Region | Size | Behaviour |
|---|---|---|
| **Rig strip** | 96px comfortable / 80px compact, full width | **Chrome, not a pane.** Never scrolls, never collapses, never hosts a tab. This is how pillar C2 survives single-window mode: the compute ledger structurally cannot be occluded, because there is no z-order for it to lose. Contents are exactly the rig monitor's strip form (§2.3), including the always-present trace row. |
| **Rail** | 220px, collapsible to a 56px icon rail | The switcher (§2.17), vertical: every window, its state, its alert rung, its headline value. Set `SplitPane.setResizableWithParent(rail, false)` (**verified**) so it does not grow with the window. |
| **Content** | remainder | A horizontal `SplitPane` of **1–3** `TabPane`s. Three is the ceiling: a fourth column on a 1440px window gives each pane 340px, below every tool's minimum. |
| **Alert tray** | 28px / 200px | The triage list (§6.7). Expands on a rung-3 event; the player may pin it open or collapsed. |

**Tabs.** `TabPane.TabDragPolicy` exists since JavaFX 10 (**verified**); set to the reordering policy so tabs can be reordered by drag (⚠ **unverified**: the exact enum constant names — read them off `TabPane.TabDragPolicy` before implementing). `TabClosingPolicy` is `ALL_TABS`, except that the tab hosting a live engagement is not closable (§3.2). Moving a pane between columns: drag the tab, or `dock move terminal left` in the palette, or the tab context menu — no new chord (§3.5).

Every tab carries an accessible name equal to its window title, and the rig strip's gauges carry theirs (`01-visual-language.md` §6.4).

### 5.3 The mode switch

`Shortcut+Shift+D`, from any window, at any time, including mid-breach (`00-client-overview.md` §6.4). A setting that becomes unreachable when you need it is not a setting.

**Multi → docked.** Each open window's pane instance is re-parented from its `Scene` into a `Tab`. **The `Stage` geometry is retained in `layout.json`, not discarded**, so switching back restores the desk exactly. Column assignment: the active workspace's docked plan if one is applied, else two columns split 60/40 with windows distributed in canonical order.

**Docked → multi.** Stored geometry is reapplied through the §3.8 validator; windows with no stored geometry get their default placement.

**What must survive the switch**, and the mechanism that makes it survive: **the same `Node` instance is re-parented, never rebuilt** (§1.5). Scroll position, table selection, map viewport, half-typed probe text, expanded rows, filter state — all of it is state held in the live scene graph, and all of it is lost the moment the pane is reconstructed from a factory. A player who switches modes mid-breach to get a manageable layout and loses their typed probe has been punished for using the accessibility feature.

The engagement itself is unaffected in either direction: it is server-owned (I14) and the pane is a view.

**The switch never animates** — same reasoning as the theme switch (`00-client-overview.md` §4.3): cross-fading an application while a trace timer runs is a legibility failure with a stopwatch attached.

**The mode persists** (`layout.json.mode`, per `00-client-overview.md` §4.5).

### 5.4 "No functionality or information is lost" — the mechanical version

A promise in prose erodes. Four checkable rules instead:

1. **One pane factory per window id, consumed by both modes.** Test: `assertEquals(registry.ids(), dockHostableIds())`. A tool that exists in one mode and not the other fails the build.
2. **A pane may not ask which mode it is in to decide *what data to show*.** It may ask to decide *layout*. The mechanism: panes receive a `DockContext` exposing available width, available height and density — and nothing else. There is no `isDocked()`. Without that API shape, rule 2 is unenforceable; with it, the wrong thing is unwritable.
3. **Every accelerator resolves in both modes.** In docked mode the raise action activates a tab, adding the pane to a column if it is not present. Test: for each id, the accelerator makes that pane visible in both modes.
4. **Every cross-window link (§3.3) resolves in both modes.** Same table, different verb.

**What actually differs, honestly:** simultaneity. Three panes visible at once instead of six. That is the real cost, it is unavoidable in one window, and §5.5 spends the entire budget on the case where it matters.

**What does not differ:** density is still per-pane (`Shortcut+Shift+C`). Theme is still global (`00-client-overview.md` §4.4). `alwaysOnTop` is meaningless and simply absent — the rig strip has replaced it with something stronger.

### 5.5 The live breach in docked mode — the case that motivated the flag

> **Requirement:** during a breach the player must be able to see **the target, the trace meter and their compute at once**, in single-window mode.

**How each is satisfied:**

| Needed | Where it lives | Why it cannot be lost |
|---|---|---|
| **Compute** | Rig strip, left third — segmented gauge, per-consumer legend, available readout, recovery clock | The strip is chrome. It cannot be covered, tabbed away, or scrolled off. |
| **Trace** | Rig strip, centre — `es-gauge-trace` with labelled contribution segments | Always present, showing `no active engagement` when idle, so its arrival never reflows the strip (§2.3). |
| **Target** | Content, right column — `netmap` focused on the engaged node (`es-state-engaged`) | The `breach` docked workspace puts it there; §5.6 covers what happens when the column will not fit. |
| **The active layer** | Content, left column — `terminal` | The widest pane, because it is where input goes. |
| **Cross-reference material** | Content, right column — `recon` as a second tab beside `netmap`, `Shortcut+3` | Simultaneity with the map is the one thing genuinely traded away here. |

**Concrete geometry, 1280×800 dock:** rig strip 1280×96 · rail collapsed to 56 · content 1224×648 split 62/38 → terminal 759, map/recon 465 · alert tray 28. Every pane clears its minimum.

**The strip does not grow when a breach starts.** Constant geometry, always — the alternative pushes every pane down 24px at the precise moment the player is reading a number, which `01-visual-language.md` §7.3 forbids and pillar C5 exists to prevent.

**The layout is applied by the player, not by the event.** Starting a breach in docked mode posts a rung-1 mark offering the `breach` workspace (`Shortcut+Shift+1`). It does not rearrange the panes on its own. The client does not move the player's furniture while they are working, and "there is a timer running" makes that more true, not less.

### 5.6 Small screens

Below **1024** logical px of content width the two-column breach layout stops clearing minimums (`terminal` 560 + `netmap` 720 = 1280 plus a divider). The degradation is specified rather than emergent:

- The `netmap` column collapses. In its place, a **target strip** appears as a single 32px row inside the terminal pane, directly under its header: node-kind glyph · address · defense-ring summary · `layer 2 of 4 · Credential · tier 3` · hop distance from entry.
- `netmap` remains a tab in the same column as `terminal`, one keystroke away at `Shortcut+2`.

**No information is lost** — every value on the target strip is on the map, and the map itself is one key away. What is lost is *simultaneity*, and the target strip restores the decision-relevant part of it: which node, how defended, which layer, how far. That is the honest boundary of what one window can do, stated rather than glossed.

At the **800×600** floor the shell refuses to shrink further (`Stage.setMinWidth/setMinHeight`) rather than letting the layout break. A single content column, the rig strip, and the collapsed rail still fit: 800 − 56 rail = 744 content width, which clears `terminal`'s 560 and `ledger`'s 600. Below that we would be shipping a layout that cannot render its own tools, which is worse than a floor.

---

## 6. Focus and urgency

### 6.1 The toolkit fact that shapes this section

**JavaFX exposes no attention or urgency API on `Stage`** — no `requestAttention()`, no urgency hint, no taskbar-flash or Dock-bounce (**verified** against the `javafx.stage.Stage` API docs). Reaching OS-level attention means pulling `java.awt.Taskbar` into a JavaFX process, which `02-platform-native-themes.md` **PN-4** owns and does not decide.

That constraint points the same direction the design already wanted. `../design/00-vision-and-pillars.md` §7: *"time pressure exists (trace timers, backlog timers) but the skill expression is planning, reading, and triage."* Pillar C5 turns that into a rule — *"alerts accrete into a triage list, they do not steal focus. Focus theft during a breach is the single most damaging thing this client could do."* So the entire ladder below is **in-application**, and that is not a workaround.

### 6.2 The attention ladder

Five rungs. Every event is assigned exactly one at the point it is raised, and **no rung takes focus**.

| Rung | Name | For | Presentation | Focus | Motion |
|---|---|---|---|---|---|
| **0** | record | routine state change | appended to the window's own stream; the switcher headline updates | never | none |
| **1** | mark | something the player should know eventually | rung 0 + `es-state-alerting` dot on the switcher entry and on the window's own indicator (its tab, in docked mode) | never | none |
| **2** | pulse | time-bounded, should be noticed soon | rung 1 + a two-step opacity pulse at `DUR_SLOW`, **capped at three cycles, then steady** (`01-visual-language.md` §7.3 permits exactly this and nothing more) | never | 3 cycles max |
| **3** | tray entry | carries a deadline | rung 2 + a row in the alert tray with a live countdown; the tray auto-expands if collapsed | never | none |
| **4** | banner | changes what a window *means* | rung 3 + a banner inside the affected window only | never | none |

**Rung assignment, by event class:**

| Event | Rung | Why |
|---|---|---|
| Block mined; miner buffer tick; ledger row; recon document recovered | 0 | routine |
| Market offering became reachable; heat band changed; provenance re-verification finished | 1 | matters, not now |
| Noise threshold crossed; buffer reached its 4h cap; a deployed miner stopped reporting | 2 | a decision is now available |
| **Bot defense backlog item** (`../design/10-botnets.md` §1) | 3 | a shrinking response window is the definition of a deadline |
| Canary trip; Ping Sweep against you (`../design/07-recon-tools.md` §1 — *"target is notified something pinged them"*) | 3 | someone is casing you, and the response window is real |
| Raid in progress; sweep resolved; bot lost; trace entered `es-state-imminent` | 4 | the meaning of the surface has changed |

**Banner rendering, and the rule that makes it safe:** the banner is an overlay anchored to the top of the window's content plane at elevation level 3 (`01-visual-language.md` §5.5), 32px comfortable / 28px compact. It **does not reflow the content beneath it**. That is affordable because of a companion rule the catalogue already obeys:

> **Every tool window's top 32px is its own heading row and may hold no time-critical control.** So a banner can never occlude one.

### 6.3 What is allowed to interrupt: nothing

**Not one rung takes focus. There is no rung 5.**

The exhaustive list of things that may raise or focus a window is in §3.3: an explicit player action, a cross-window link the player activated, a mode switch or workspace restore. A server event is on none of them.

Three corollaries that close the obvious loopholes:

- **No window is opened by an event.** `Stage.show()` takes focus on every platform, so "just pop open the defense window when a canary trips" is focus theft wearing a helpful expression.
- **No alert sets `alwaysOnTop`** (`01-visual-language.md` §5.4).
- **`Modality.APPLICATION_MODAL` is banned outright** (`01-visual-language.md` §5.3) — it blocks input to every window including the rig monitor. The only modals in this client are `ModalPane`s scoped to one window, and every one of them is **player-initiated**: abort confirmation, Ghost Protocol, faction abandonment, quit-with-live-engagement. **No server event ever produces a modal.**

**In-game consequences are not errors** (`01-visual-language.md` §9.4). A failed breach, a swept network, a lost bot are outcomes with narrative weight; they get outcome surfaces on the ladder, never an error dialog.

### 6.4 The typing guard

Not stealing focus is necessary and not sufficient. A player typing a probe under a trace timer can also be broken by something that merely *moves* while they type.

**The rules:**

1. **No alert reflows the focused window's content.** Structurally guaranteed by the overlay banner in §6.2.
2. **No alert may appear inside a window whose text-entry caret is active.** If a rung-4 banner is due for a window with an active caret, it is **queued** and inserted at the first of: caret blur · submit · 3000ms of keyboard idle. The tray entry (rung 3) appears immediately regardless, because the tray is outside the pane and never moves anything inside it.
3. **Nothing animates while a breach is live**, beyond the meters themselves (`01-visual-language.md` §7.3) — including the rung-2 pulse, which goes straight to its steady end state.
4. **Nothing reorders under the cursor.** New tray rows and log lines append; the view never re-sorts (`01-visual-language.md` §7.3).
5. **Rate limit:** at most one promotion to rung ≥ 2 per window per 2000ms. Further events inside that window coalesce into the existing entry with a count.

### 6.5 Coalescing — the correlated-sweep rule

Pillar C3 names the exact failure to avoid (`00-client-overview.md` §2): a network-wide wipe must present as *one event with one cause* — heat band, the roll, what was lost, what survived and why — **not as eleven separate "miner lost" notifications.** *"Eleven notifications turn a designed dramatic beat into a bug report."*

`../design/04-mining.md` §4 makes this structural rather than incidental: heat is a single global value, every NPC-hosted miner rolls against it, and *"losses are correlated, not attritional."* The client will therefore receive bursts of correlated events as a matter of course.

**The mechanism:** every event carries a `causeId`. Events sharing one produce **exactly one** ladder entry, at the highest rung any member warrants, whose body itemises the members. The entry's headline names the cause (`Eye sweep · heat: high · 45%/hr`); the body lists what was lost, what survived, and why (Firmware Implant, `../design/11-rig-infrastructure.md` §3). One entry, one cause, full attribution — which is C3 satisfied at the notification layer rather than only at the meter.

If the server does not send a `causeId`, the client falls back to a 2000ms window over `(eventType, source)` and labels the group `n events`. It must never *invent* a cause, because a cause is exactly the kind of assertion C4 forbids the client from making.

### 6.6 Clearing, escalation, expiry

**Clearing:**
- Rungs 1–2 clear when the player raises or focuses that window. Looking at it is acknowledgement enough.
- Rungs 3–4 clear **only** on explicit acknowledgement or on resolution. A deadline you glanced at is not a deadline you handled, and `../design/10-botnets.md` §1 makes the backlog timer shrink with bot count precisely so that triage is the skill.

**Escalation over time:** a rung-3 item whose deadline enters its final 25% promotes one rung, and its countdown adopts `-es-status-bad-fg` with `es-state-imminent`. It still does not take focus. Escalation is capped at rung 4.

**Expiry:** when a deadline passes, the outcome is a server fact. It is rendered per C3 with its cause and its consequence itemised — not as "you missed it." The rule from `01-visual-language.md` §9.4 holds: state the rule that applied, never blame the player.

**Minimised and closed windows:** a minimised window that alerts is **never de-iconified** and a closed window is never re-opened. The switcher distinguishes `open · alerting`, `minimised · alerting`, and `closed · alerting`, because "where is the thing shouting at me" is the actual question and the switcher is the only surface that can answer it for all three.

### 6.7 The two triage surfaces

Under `../design/10-botnets.md` §1b's split-attention model — parallel, non-queuing, penalising every simultaneous engagement — the player's job during a bad minute is to read several surfaces and decide which one to save. Two surfaces exist for exactly that:

- **The switcher** (`Shortcut+Shift+J`) — every window, its rung, its headline value. Breadth.
- **The alert tray** — every rung-3 and rung-4 item with its deadline, sorted by time remaining, ascending. Depth, and it is where the countdowns live. In multi-window mode the tray is a section of the switcher; in docked mode it is the bottom band (§5.2). Same content, same order, same actions.

Each tray row offers the one or two actions that resolve it inline — `defend`, `abandon`, `open <window>` — so that triage does not require a window tour. `defend` and `abandon` are time-critical controls and take the 40px comfortable / 36px compact hit target (`01-visual-language.md` §4.3).

`Tarpit` deserves a note here, because the design already anticipated this surface: `../design/09-defense-and-hardening.md` §2 describes it as pairing *specifically* with the bot-backlog timer — *"it buys the seconds you need to triage which bot to save when multiple engagements fire at once."* An armed Tarpit is therefore visible in the tray as extra seconds on the affected countdowns, not merely as a line item in `defense`. A defensive item whose entire value is response time has to show up in the surface where response time is spent.

---

## 7. Open questions

Deliberately undecided here. Log in `../design/15-open-questions.md` §2 if this doc set is adopted. Prefix **WL-** (window layout), chosen to avoid the existing `OQ`/`P`/`D`/`S`/`N`/`E`/`A`/`G`/`W`/`Q` prefixes in `design/15` and the `CL-`/`V-`/`PN-` prefixes in `docs/client/00`–`02`.

- **WL-1: Does the catalogue stay at thirteen windows, or go to fifteen?** §2.2 adds `comms` and `settings` to `00-client-overview.md` §6.1's thirteen. Folding `comms` into `identity` and making `settings` a `ModalPane` are both cheap; splitting them later is not, because every cross-window link, accelerator and workspace would move. **Decide before the first non-scaffold window ships.**
- **WL-2: How stable does a display signature need to be?** §3.9 synthesises one from screen bounds and output scale, because JavaFX exposes no per-monitor identity. Two identical monitors hash the same and can swap desks. Native EDID access would fix it and costs three platform-specific code paths. Watch for: players with matched dual monitors reporting their layout "flips."
- **WL-3: Fixed canonical switcher order, or most-recently-used?** §3.4 fixes it so `Shortcut+1…9` is muscle memory, resolving an ambiguity in `00-client-overview.md` §6.3. MRU would put the windows you are actually using first. These are incompatible and the right answer is probably "fixed, with a separate MRU cycle on `Shortcut+\``" — which is what this doc assumes without proving.
- **WL-4: Does the rig monitor's strip form survive a late-game consumer list?** `01-visual-language.md` **V-7** already flags per-consumer segmentation past ~8 consumers. The strip has one line for the legend, so it hits that wall first and hardest. If V-7's answer is grouping by consumer class with drill-down, the strip needs to be the first surface designed against it.
- **WL-5: Should workspaces be exportable and shareable?** A community "operator's desk" layout is genuinely appealing and would be well received. It is also a file the client parses, and `00-client-overview.md` §7 rules out player-supplied CSS for a related reason. A geometry-only schema is a much smaller surface than a stylesheet — but "much smaller" is not "none."
- **WL-6: Should a workspace capture view state, or only geometry?** §4.1 captures only geometry, so restoring an arrangement never rewinds what the player was reading. The counter-argument is strong for `investigation` specifically, where the ledger filter *is* the workspace. Possible split: presets carry view state, player-saved workspaces do not — or the player chooses per workspace.
- **WL-7: Should the alert-ladder ceiling be a player setting?** §2.16 lists it under Accessibility on the assumption that it should. Players with attention-related disabilities may want everything at rung 1; players who miss deadlines may want everything at rung 4. The risk is a player capping the ladder at rung 1 and then losing a botnet to a backlog timer they never saw — which is a legitimate choice, but only if the setting says so plainly.
- **WL-8: Two windows for the same tool.** §3.1 defers `openNew()` because a second `netmap` needs a second pane instance and makes every cross-window link ambiguous. Two `recon` windows side by side is exactly what cross-referencing wants, though, and it is the most plausible thing a player will ask for. Revisit once the pane instance model is real.
- **WL-9: What raises a window when the client has no OS focus at all?** §3.6: JavaFX has no global hotkey API and no urgency hint, so a client in the background can neither be summoned nor signal. `02-platform-native-themes.md` **PN-4** (an AWT `Taskbar` badge) may be the only honest answer, and it costs AWT in a JavaFX process. Decide alongside **CL-7** (audio), which is the other non-focus-stealing channel.
- **WL-10: Is a "peek" gesture worth it?** Holding a key to raise the rig monitor above everything temporarily would solve occlusion without a permanent always-on-top — but the only ergonomic key for a hold gesture is `Alt`, which `00-client-overview.md` §6.3 already spent on attribution overlays. Probably not worth a second hold gesture; noted so it is not rediscovered.
- **WL-11: Minimum sizes versus localisation.** Every minimum in §2.1 was chosen against English strings. German compounds and Japanese vertical metrics will both break some of them, and `01-visual-language.md` **V-10** already defers localisation wholesale. The minimums must be re-derived from the longest string per locale, not scaled by a factor.
- **WL-12: Does the docked mode need more than three content columns, or a free-form dock?** §5.2 caps at three because a fourth column on a 1440px window is below every tool's minimum. A drag-anywhere dock (a docking framework) would be more flexible and is a large dependency plus its own accessibility surface. Revisit only if playtests show three columns is the binding constraint.
