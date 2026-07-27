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
- **Bezel** — a drawn monitor casing, screen curvature, or any frame implying the interface sits inside a pictured device. Still cut, without exception.
- **Vignette** — corner and edge darkening. Still cut: it dims real content by position rather than by meaning, and the corners are where tiled windows go.
- Any screen artefact that is **not** switchable off by the player (see §9.1)

### 9.1 Screen artefacts — permitted, on conditions (amended 2026-07-26)

**This list previously read "CRT scanlines, vignette, bezel, chromatic aberration — explicitly cut, do not reintroduce." That was amended on explicit direction.** Three of those four are now permitted:

| Artefact | Status | Ships |
|---|---|---|
| **CRT scanlines** | Permitted | Off |
| **Chromatic aberration** | Permitted | Off |
| **Light VHS-style glitch** — brief displacement torn off *edges* | Permitted | Off |
| **Simulated tube curvature** — radial rim aberration on a slider | Permitted | 0 |
| **Bezel** | **Still cut** | — |
| **Vignette** | **Still cut** | — |

Four conditions, and they are what make the amendment safe rather than a hole in the list:

1. **Every artefact is off by default and switchable off permanently.** This is the whole distinction the rejection list was protecting. An effect the player switches on is a costume; an effect welded to the interface is a claim about fidelity that the interface then has to keep making while the player is trying to read a number. Settings → Screen, and the `crt` command.
2. **No artefact may reduce the legibility of a figure the player is required to read.** Scanlines cost contrast on body text — that is exactly why they are opt-in rather than a default, and why the high-visibility theme does not turn them on for anyone.
3. **Still no blur and no glow.** §9's ban on those is unchanged and machine-checked. A scanline is a hard-edged band and a glitch sliver is a flat lift with hard edges — real artefacts on real hardware are hard-edged too, so nothing is given up. The **one** exception is the refresh bar, which §9's own wording already allows: gradients are permitted where they are "hard-edged or **near-transparent**", and every stop in it is below 0.05 alpha. A test enforces that ceiling.
4. **Motion artefacts obey §5.** Scanline drift, the refresh bar and the glitch all step in whole pixels and never tween, and `prefers-reduced-motion` stops all three — leaving the lines drawn and perfectly still. Aberration never moved.

**Glitch displaces the picture; it does not paint on it.** Two wrong versions preceded this one and both are worth recording. It began as full-width tracking bands — but a real signal does not degrade uniformly, it breaks up where the signal changes fastest, so it was re-anchored to **window frames, panel borders, table rules and the edges of readouts**. That was still not right, because it *drew coloured slivers over* an interface that never moved, and painted marks read as decoration sitting on the screen. A tape or timebase fault **moves the image**. So the glitch now sets `translateX` on real nodes — a render transform, no layout pass — and a row of text that jumps four pixels sideways and back reads instantly as the signal failing. The drawn fringes remain, but their job is now the colour bleed on the edges of the elements that *moved*.

It is also **bursty rather than periodic**: quiet for 3.5–12 seconds, then 3–8 frames at 90ms that re-randomise every frame, then quiet again for a different interval. The first build held one displaced pose for 1.4 seconds, which reads as a rendering fault rather than tape damage — a VHS tear is a snap. Intermittency is what makes an artefact read as damage at all; something on a regular beat reads as a feature of the interface, and something constant stops being noticed within a minute. ⚠ Because it mutates nodes it does not own, every displacement is recorded with its **previous** translation and restored on burst end, on switch-off and on dispose — restoring a hard zero would destroy any translation another part of the client had set, and a decorative effect that quietly breaks a real animation is worse than one that does not run.

Anchoring to elements also makes the effect self-scaling in the right direction: **a bare desk barely glitches and a crowded one glitches most**, so the artefact tracks how much interface is actually on screen.

**Curvature is a slider, and it does not warp the picture.** ⚠ Real barrel distortion is a per-pixel remap needing either a pixel shader (JavaFX exposes none) or a per-frame render-to-texture mapped onto a 3D mesh. The second is not just expensive — it **breaks input**, because hit-testing would still use the undistorted geometry and every click would land somewhere other than where the player sees the control. A curvature setting that silently made the UI unclickable is a far worse outcome than one that does less than its name suggests, so the interface stays flat and the Settings copy says so outright.

What the slider does scale is the artefact curved glass actually produces: **radial chromatic aberration at the rim** — zero at the centre, stronger at the edges, strongest in the corners. Built from four edge bands, since a corner sits inside two of them at once; measured at full strength as R−B of −2 at centre, −11 at the edge midpoints and −19 at all four corners. ⚠ Every band runs **warm outboard, cool inboard, the same way round on all four edges**, because lateral CA magnifies one channel more than the other. Making left/top warm and right/bottom cool looks reasonable and is wrong — the two bands then carry opposite channels at the top-right and bottom-left corners and cancel, which measured as +4 and −8 against +17 and −22 at the other two. Two strong corners and two washed-out ones is that mistake's signature. It stays a fringe rather than an outline: it fades out well before the centre and never closes into a frame, because a frame is a bezel.

**Scanlines move, and that is what makes them a tube.** A still line pattern is a Moiré texture; the slow vertical drift plus a refresh bar rolling down it is what a camera pointed at a CRT actually records. Both live under the single scanline switch, because nobody enables scanlines wanting a static one. ⚠ The drift is deliberately slow — fast drift over body text is a shimmer that is tiring to read through, which would undo condition 2.

⚠ **Chromatic aberration is scoped, and the scope is honest.** Full-scene aberration would mean snapshotting and recompositing the whole scene every frame; there are no shaders available. It is applied to the **desk wallpaper**, which is text and can afford three layers, and to the **edges of glitch bands**, which is where a tape artefact bleeds colour anyway. It is not applied to the terminal, the tables or the meters, and the setting's own help text says so.

**The greeble budget now has a second consumer.** §4 budgets "roughly 10–15% of pixels" to meaningless texture. The desk wallpaper is greeble at desk scale and spends from that same budget, which is why it is held near 10% occupancy of cells, drawn in `dim-3` at ~0.34 opacity, and **never in amber** — §2.1's accent reservation matters most on the largest surface in the client.

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

- **§9's screen-artefact ban was amended, and §9.1 is the replacement.** Scanlines, chromatic aberration and light VHS glitch are permitted as **optional, off-by-default, player-switchable** effects; bezel and vignette stay cut. The line the list now draws is *switchability* rather than the effects themselves — the original entry's real argument was never "artefacts look bad", it was that an interface which permanently degrades its own legibility is lying about what it can show, and a toggle answers that completely. Implemented as `ui/CrtOverlay` (scanlines, tracking bands, band fringe) and `ui/widgets/Substrate#setAberration`. ⚠ Aberration is **scoped to the wallpaper and the glitch bands** and cannot be full-scene — see §9.1.
- **The desk has a wallpaper, and it spends §4's greeble budget.** `ui/widgets/Substrate` is greeble at desk scale: the same alphabet §4 fixes, sparse enough to sit near 10% cell occupancy, in `dim-3` at ~0.34 opacity, **never amber**. It has three states — off, still, drifting — rather than a checkbox, because **WCAG 2.2.2 (Pause, Stop, Hide)** requires that automatically-starting motion lasting over five seconds be pausable, and because "I want the texture but not the movement" is a real preference that a boolean forces a player to lose. Rows drift at three different rates: a field sliding as one sheet would read as a scrolling raster, which is the thing §9.1 still does not want.

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
