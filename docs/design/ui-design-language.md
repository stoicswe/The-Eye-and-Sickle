# UI Design Language — The Eye and Sickle

*Target path: `docs/design/ui-design-language.md`. Companion to `docs/design/` (systems/economy) and `docs/architecture/` (stack). Reference implementation: `rig-console-mockup.html`.*

**Status:** decided. The reference mockup is the source of truth for look; this document is the source of truth for rules. Where they disagree, this document wins.

---

## 0. The decision that has to be reversed first

`docs/architecture/01-tech-stack` specifies **AtlantaFX for native OS theming** and **a separate `Stage` per tool**. Both are cancelled.

Native theming puts real macOS traffic lights and Windows title bars around the game. The entire aesthetic depends on the player never seeing their own operating system. Every reference image is a sealed world.

**Replacement:**

- **One `Stage`**, `StageStyle.UNDECORATED`, maximized or fullscreen. It contains an in-game window manager: draggable panes with chrome we draw ourselves.
- **Drop AtlantaFX.** Ship one hand-written stylesheet. Nothing inherits from a platform theme.
- Multi-window survives only as an **opt-in multi-monitor feature**. Each additional `Stage` is also `UNDECORATED` and gets its own drawn chrome. It is never the default.

Everything below assumes this reversal.

---

## 1. Thesis

Blade Runner / cyberpunk operator's console, **without CRT effects and without a physical bezel**. Those were doing most of the "this is not your computer" work in the reference images. Stripped of them, the mood has to be carried by four things and nothing else:

1. **Geometry** — hairline rules, corner notches, no fills, no radius.
2. **Density** — persistent visible state, no hidden UI, greeble as texture.
3. **Voice** — diegetic uppercase `KEY: VALUE` readouts with units.
4. **Motion** — step timing only. No easing curve anywhere in the product.

The named failure mode: **a competent dark-mode developer tool.** If a screen would not look out of place in a JetBrains IDE, it has failed, regardless of how correct the colors are.

---

## 2. Tokens

### 2.1 Color

Ground is cold blue-black. Grayscale is cold. The single accent is warm sodium amber. That temperature split is load-bearing — it is what replaces phosphor glow.

| Token | Hex | Use |
|---|---|---|
| `void` | `#07090A` | App ground, inset wells. **Never `#000`.** |
| `panel` | `#0C1012` | Panel body |
| `panel-hi` | `#11171A` | Header strips, hover rows |
| `rule` | `#1B2326` | Hairlines, table row dividers |
| `rule-hi` | `#2C383B` | Panel edges, section boundaries |
| `dim-3` | `#33403F` | Greeble, deepest gray fills |
| `dim-2` | `#4E5D5E` | Labels, keys |
| `dim-1` | `#7B8D8E` | Secondary values |
| `text` | `#A9BCBD` | Body |
| `text-hi` | `#DCE9E9` | Primary values, panel titles |
| `amber` | `#FFAE38` | **Live/earning data only** |
| `amber-mid` | `#B87A28` | Secondary live, filled meters |
| `amber-low` | `#6A4715` | Hazard stripes, note rules, dim outlines |
| `alarm` | `#C4423A` | Loss and hostile state only |

**Rules of use**

- **Amber is not "primary" — it means cycles doing work, or income.** In the cycle grid, self-mining and control channels are amber; frames, firewall, and detection array are gray steps. The palette encodes income vs. overhead, so a panel is readable before it is read. Do not spend amber on ordinary emphasis.
- **`alarm` appears at most twice per screen.** It marks a hijacked miner, a full buffer discarding yield, a failed crack. Never a normal validation error.
- **No semantic color system.** No blue-info / green-success / red-danger. Introducing one kills the look in a single commit.
- **Depth comes from brightness, never from shadow or blur.** No `DropShadow` on panels.

### 2.2 Type

Two faces, both monospace, both OFL — bundle the TTFs in `resources/fonts/`, do not rely on system installs.

| Role | Face | Treatment |
|---|---|---|
| **Labels, keys, headers, buttons** | **Martian Mono** 500 | Uppercase, 8–9px, wide tracking |
| **Body, data, tables, numbers** | **IBM Plex Mono** 300/400/500 | 11–12px |
| **Display numerals** | **Martian Mono** 700 | 24–30px, the one large thing on a panel |

Martian Mono is chosen because it is unusually wide by default — see §7.3, JavaFX cannot do letter-spacing, and the face has to supply the tracking itself.

Everything snaps to a character cell. Numbers are tabular-figure everywhere (`font-feature-settings: "tnum"`, or in JavaFX bundle the tabular variant).

### 2.3 Geometry & spacing

- **Border radius: 0.** Everywhere. No exceptions.
- **Borders are 1px hairlines, not fills.** Panels are drawn, not filled.
- **Notched corners** replace rounded ones: 18px 45° cut, top-right of major panels.
- **One diagonal per screen**, no more — a hazard-stripe band at 45°. It is what stops the layout reading as "terminal."
- Spacing scale: `1, 5, 7, 9, 12, 14` px. Tight. Density is the point.
- Cell grid: 11px base cell for meters and the cycle grid.

---

## 3. Layout

**Tiling, not floating.** Panels abut and share edges, filling the screen. Nothing sits on neutral background with margin around it.

```
┌──────────────────────────────────────────────────────────────┐
│ TOP STATUS STRIP  ─ operator, heat, noise, thermal, session   │
├────┬─────────────────────────────┬───────────────────────────┤
│    │                             │                           │
│ R  │  PANE 1                     │  PANE 2                   │
│ A  │  (notched, own chrome)      │  (notched, own chrome)    │
│ I  │                             │                           │
│ L  │                             │                           │
├────┴─────────────────────────────┴───────────────────────────┤
│ COMMAND STRIP  ─ prompt, caret, keybind hints                │
└──────────────────────────────────────────────────────────────┘
```

- **Top strip** — global diegetic state. Cells separated by 1px rules, one flex spacer, one hazard band, clock right-aligned.
- **Left rail**, 34px — vertical rotated label, tick marks, hazard strip. Almost pure texture. Hides below 900px.
- **Main** — 2 columns at `1.32fr / 1fr`, collapsing to one column below 900px.
- **Command strip** — prompt with blinking block caret, keybind hints.

**Nothing is hidden.** No hamburgers, no modals, no collapsed drawers, no tooltips carrying information not shown elsewhere. Persistent visible state, at the cost of white space.

**Every region has a header strip.** `LABEL` left, `[−] [□] [×]` glyph controls, then a dim right-aligned identifier (`PROC/ALLOC · 0x2F`). Unlabeled regions are a bug.

---

## 4. Component catalog

| Component | Rule |
|---|---|
| **Key:value readout** | `KEY` in `dim-2` Martian 8.5px uppercase; value in `text-hi` Plex 12px. Units always present. `CPU TEMP: 67.2C`, never `Temperature: 67°`. |
| **Cycle grid** *(signature)* | 100 discrete cells, 25 per row, 1px gaps, on a `void` well. Each cell colored by owner. Compute is countable, not a percentage. |
| **Legend** | Key:value rows on a 1px grid, not chips. Hovering a row isolates its cells in the grid instantly (opacity 0.22 on the rest, no transition). |
| **Meter** | 3px × 9px cells with 1px gaps. Never a continuous bar or gradient. |
| **Buffer indicator** | 8 cells = 4 hours, one per half hour. Fills `amber-mid`; goes `alarm` at full. |
| **Table** | Martian 8px uppercase headers, 1px `rule` row dividers, `panel-hi` on hover. Host cell carries a designator + an uppercase dim subtitle (`KX-4417` / `transit fare relay`). |
| **Note** | 2px left border in `amber-low` (or `alarm`), `panel-hi` ground. One sentence of consequence, not description. |
| **Working panel** | An inset well with a sweep bar crossing it on a linear loop. Used only where something is genuinely in progress. |
| **Greeble** | Hex quads, block glyphs, dots, 4-digit serials, `//` marks. `dim-3`, 8.5px, clipped at the edge. Regenerates every ~4s. **Unreadable by design.** |
| **Hazard band** | 45° repeating stripe in `amber-low` or `rule-hi`, ~55% opacity. |

**On greeble:** it is not decoration to be cut in review. It is the single largest difference between this look and a dashboard. Budget roughly 10–15% of pixels to information that carries no meaning.

---

## 5. Motion

**Step and linear timing only.** Any spring, bounce, or ease-out reads as web UI immediately and will undo the whole aesthetic.

| Event | Treatment |
|---|---|
| Panel reveal | Horizontal clip wipe, ~0.34s, **9 discrete steps**, staggered per pane |
| Value refresh | Values **twitch** — jump to the new figure with no interpolation |
| Text arrival | Types in character by character; never fades |
| In-progress work | Linear sweep bar, ~2.6s loop |
| Thermal recovery cells | Blink between two states on a 2-step loop |
| Caret | 1.06s step blink |

Numbers that count up are fine. Numbers that smoothly tween are not.

**`prefers-reduced-motion` kills all of it** — static final state, caret solid. Not optional.

---

## 6. Voice

Diegetic and operational. Uppercase for labels, sentence case for consequence text.

- **Errors do not apologize and are never vague.** `BUFFER FULL — YIELD DISCARDED`, not "Warning: your buffer may be full."
- **State the consequence, not the condition.** "KX-0155 has paid out nothing for 31 hours. The channel still bills 3 cycles." beats "Miner status: anomalous."
- **Name the tradeoff the player is actually facing.** "A thorough scan needs 35. Pull cycles off self-mining to run one, and the block in progress is forfeit."
- Empty states are an instruction, not a mood piece.
- One name per action, used everywhere: the key that says `COLLECT` produces the line `COLLECTED`.

---

## 7. JavaFX implementation notes

JavaFX CSS is a `-fx-`-prefixed subset of CSS2. Several things the mockup relies on do not exist. These are the real gaps.

### 7.1 What maps cleanly

| Web | JavaFX |
|---|---|
| CSS custom properties (colors) | **Looked-up colors** — `.root { -amber: #FFAE38; }` then `-fx-fill: -amber;`. Colors only. |
| `steps(n)` timing | `Timeline` + `Interpolator.DISCRETE` — an exact equivalent |
| `:hover`, `:focus-visible` | `:hover`, `:focused` |
| Flex/grid | `HBox` / `VBox` / `GridPane` / `TilePane` + `setSpacing` / `hgap` / `vgap` |
| Inset ring (`box-shadow: inset`) | Layered `-fx-background-color` with `-fx-background-insets` — the idiomatic way to draw hairlines |
| Per-side borders | `-fx-border-color: a b c d;` with `-fx-border-width` |

### 7.2 What does not exist

- **`clip-path`.** Notched corners must be a `Polygon` set via `Node.setClip()`, or a `Path` drawn as the panel frame. `-fx-shape` accepts an SVG path string but **scales the shape to the region**, which distorts a fixed 18px notch on resize — do not use it here. Nine-slice images are the fallback if the `Path` route gets fiddly.
- **`letter-spacing`.** No tracking control at all. This is why Martian Mono was chosen — the face supplies the width. Do not attempt per-character `Text` nodes to fake it; the layout cost is not worth it.
- **`text-transform`.** Uppercase in the model layer or in a formatter, not in CSS.
- **`aspect-ratio`.** Cycle-grid cells need explicit `prefWidth`/`prefHeight`, or a `TilePane` with fixed tile size.
- **Numeric CSS variables.** Looked-up colors are colors only. Spacing and size tokens live as Java constants — one `UiTokens` class, referenced everywhere, never inlined.
- **Custom shaders.** Not needed now that CRT is cut. Relevant only if that decision is ever revisited.

### 7.3 Performance

- 100 `Region` nodes in a `TilePane` for the cycle grid is fine.
- Greeble regenerating every 4s across several strips: use a single `Canvas` per strip rather than many `Text` nodes if profiling shows scene-graph churn. Start with `Text`; only move if measured.
- All timers on one shared `Timeline` driver, not one per widget.

### 7.4 Suggested structure

```
ui/
  UiTokens.java          — spacing, sizes, durations (colors live in CSS)
  theme.css              — the single stylesheet, no AtlantaFX
  chrome/
    WindowFrame.java     — notched panel + header strip + [−][□][×]
    DeskManager.java     — in-game WM: drag, focus, z-order, snap
  widgets/
    CycleGrid.java
    KeyValue.java
    CellMeter.java
    BufferBar.java
    Greeble.java
    SweepPanel.java
  panes/
    AllocationPane.java
    DeploymentPane.java
```

---

## 8. Make the desktop a mechanic

The window manager should not be pure atmosphere. Three systems already want it, and wiring them in is what stops the aesthetic from being decoration that has to be defended in review:

- **Bandwidth** (§11) caps simultaneous open tool windows.
- **Memory Buffer** (§11) caps equipped-tool windows specifically.
- **Split attention** (§10.1b) stops being an abstract modifier and becomes *too many windows competing for the same screen*. The shrinking backlog timer is visible as stacked alert panes crowding the deck.

If screen real estate is attention, the UI is a system rather than a skin.

---

## 9. Rejection list

Any of these individually undoes the look. Treat as build-blocking.

- Rounded corners, drop shadows, blur, glassmorphism
- A second accent hue, or a semantic color system
- Easing curves — spring, bounce, ease-in-out, ease-out
- Native window chrome of any kind
- Hidden UI: hamburgers, modals, collapsed drawers, accordions
- Proportional (non-mono) type anywhere, including body copy
- Gradient fills — hazard stripes and the sweep bar are the only gradients, both hard-edged or near-transparent
- Icon fonts and Material/Lucide icon sets — glyphs are drawn from ASCII and box-drawing characters
- Removing greeble because it "doesn't do anything"
- CRT scanlines, vignette, bezel, chromatic aberration — explicitly cut, do not reintroduce

---

## 10. Acceptance criteria

The first JavaFX pass is done when:

1. One undecorated `Stage`, no OS chrome visible on macOS, Windows, or Linux.
2. `theme.css` contains every color as a looked-up color; no hex literals in Java.
3. Both bundled fonts load from resources and render on all three platforms.
4. `AllocationPane` renders 100 discrete cells with correct owner coloring and instant legend isolation on hover.
5. `DeploymentPane` renders the five-row miner table with cell-based buffer bars, including the full-buffer and reassigned states.
6. Notched corners render correctly at three window widths without distortion.
7. Panel reveal uses `Interpolator.DISCRETE`; no `Interpolator.EASE_*` appears anywhere in the codebase.
8. Reduced-motion respected via a settings toggle. ⚠ **Correction:** this criterion originally read "JavaFX cannot read the OS preference; expose it explicitly and default it off." The first clause is wrong. `Platform.getPreferences().isReducedMotion()` exists, is an observable property, and the client has read it since before this document was written (`theme/ThemeManager`). The practical advice stands and is implemented: there **is** an explicit Settings toggle, and an explicit choice overrides the system one in both directions. Defaulting it off while ignoring the OS would mean a player who has asked their whole system to stop animating still gets a greeble field regenerating every four seconds until they find a checkbox.
9. Layout holds from 1280px to 2560px wide.

---

## 11. Open questions

1. **Window snapping.** ✅ **Resolved 2026-07-26 — both, as a setting.** Free-drag, or snap to a coarse grid? Snapping reinforces the character-cell language and makes Bandwidth limits legible; free-drag feels more like an operator's desk. "Prototype both" was the instruction, and both shipped: `ui/chrome/DeskManager.Placement`, switchable at runtime from Settings → Desk and from the `desk` command. **Snap is the default**, because it is also what makes edge-tiling possible — dragging a window against a side of the desk fills that half, into a corner that quarter — which is how §3's tiling ideal stays reachable by hand. Logged as **UI-1** in `15-open-questions.md`.
2. **Alert panes.** Do bot alerts (§10.1b) open as new windows, or dock into a fixed strip? New windows sell the crowding pressure but risk becoming unmanageable at high bot count. **Still open** — bots are `[PROPOSAL]` in `10-botnets.md`, so there is nothing to alert about yet. Logged as **UI-3**.
3. **Rail contents.** ✅ **Resolved 2026-07-26 — the rail is the launcher.** It became the window switcher this document suggested it might, for a reason outside this document: client pillar **C1** requires every tool to be reachable without the terminal. Each rail entry is the tool's own accelerator key, so the launcher teaches the shortcut while being the thing that removes the need for it. The tick marks and hazard strip stayed, so the texture argument survives. Logged as **UI-1**.
4. **Localization.** Uppercase-everything and fixed-width character cells assume Latin script and short strings. If non-English is ever in scope, decide before the component library sets. **Still open.** One thing already done in anticipation: every `toUpperCase` and every `String.format` in the client passes `Locale.ROOT`, so a Turkish locale cannot turn `IDENTITY` into `İDENTİTY` and a German one cannot print `1,25 EC` beside an `EC/HR` projection that used a period. Logged as **UI-4**.

---

## 12. What implementation changed, and what it cost

Recorded here rather than only in the resolution log, because these are the places where following this document had a consequence someone will want the reasoning for.

- **The `native` theme family is gone.** §0 cancels AtlantaFX and OS-native theming, so there is nothing left for a system light mode to match. What replaced seven stylesheets is **one component sheet plus palette overlays of ~40 lines each** — `theme.css` owns every component rule, geometry, hairline and motion, and a variant owns colours only. A test enforces that an overlay never sets a size, a font or a border width, because the moment one does, the guarantee that a widget cannot look right in one theme and broken in another is gone.
- **uOS Classic is no longer System 7 chrome.** Bevels, drop shadows and rounded corners are all on §9's build-blocking list. What survived is its *palette* — a light field with black hairlines — which was always the part doing the work, since it made Classic the most legible skin in the client. The period bevelling did not survive this document, and keeping it would have left one theme exempt from the contract every other theme is held to.
- **A high-visibility variant was added** (not in this document; requested alongside it). It is the same deck with a palette that clears WCAG AAA for body text and 3:1 for hairlines, plus a handful of structural modifiers in `theme.css` under `.es-theme-deck-hc` — a heavier focus ring, harder legend isolation, quieter greeble. It is the one place in the client that spends §2.1's "never `#000`", deliberately and only there.
- **§8's Bandwidth cap is built but defaulted off.** A starting rig has `bandwidth = 1` (`11-rig-infrastructure.md` §2), so capping windows at Bandwidth directly would allow one open panel. The arithmetic that turns Bandwidth into a usable window budget is invented rather than derived, so it ships as an opt-in marked `[PROPOSAL]`. Logged as **UI-2**.
- **The 100-cell grid is one cell per cycle, not per percent.** A starting rig is exactly 100 cycles (`solo/Balance.STARTING_CYCLES`), so the reference's hundred cells are literal — and the grid grows with the rig rather than rescaling, which keeps "compute is countable" true at every rig size.
- **The rig monitor is also an activity monitor** (added 2026-07-26, not in this document). It lists
  what the rig is doing, with time remaining. Building it exposed that `04-mining.md` §3.2's Duration
  column had never been implemented — a scan completed instantly and its published "~6 min" was a
  number in a log line. Progress is a `CellMeter` and a countdown in words, never a `ProgressBar`,
  because §4 says "Never a continuous bar or gradient"; unknown progress gets §5's linear sweep
  rather than an empty meter, since a bar reading 0% on a nearly-finished recovery is worse than one
  that admits it does not know. Logged as **UI-6**.
- **The game draws its own pointer** (added 2026-07-26, not in this document, but implied by §0). After
  the window chrome went, the pointer was the last piece of the host OS left on screen. Three skins,
  drawn from colours read back out of the live stylesheet so they follow every palette. **The system
  pointer is the default**, deliberately — see **UI-7**. Two more JavaFX traps were measured here:
  `-fx-cursor: url(...)` does not work at all, and a CSS `-fx-cursor` on a node beats an inherited
  Scene cursor, so every `-fx-cursor` declaration had to leave `theme.css`.
- **`ComputeBudget.unaccountedFor()` got its own cell colour.** Not in the component catalog, but it is the strongest thing this palette can do: cycles the rig is spending on something it cannot name, drawn as blinking alarm cells. `04-mining.md` §3.1 makes noticing exactly that the way a player finds a miner they did not deploy. It is never synthesised — zero means the slice does not exist — so its appearance always means something.
