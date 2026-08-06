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

> **⚠ Amended twice, and the thesis above still holds.** CRT artefacts were permitted on 2026-07-26 (§9.1) and a drawn casing on 2026-07-27 (§9.2). Both are **off by default**, so the four things above still carry the mood for every player who never opens Settings — which is what this paragraph is actually asserting. What changed is that a player who *wants* the tube and the casing can have them; what did not change is that the interface may never depend on them. Anything that reads as a competent dark-mode developer tool with the artefacts switched off has still failed.

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

### 2.1a Two state hues — a bounded exception (amended 2026-07-27)

§2.1 says **"No semantic color system. No blue-info / green-success / red-danger. Introducing one kills the look in a single commit."** §9 makes it build-blocking. That stands, with one carve-out, taken on explicit direction and deliberately fenced:

| Token | Hex | Permitted in |
|---|---|---|
| `gain` | `#4FBF6A` | The transient balance delta; a breached network node |
| `warn` | `#E08A2E` | A patched network node — breached once, locked out now |

**Three fences, and they are what make this an exception rather than the start of a system:**

1. **Two places, named exhaustively.** The balance delta and the network node states. Nowhere else. A third site needs another amendment, not a precedent.
2. **The negative case reuses `alarm`.** A debit and a hostile state are the same red, because `alarm` already means loss — so exactly **one** new hue enters the palette, not three.
3. **Never a persistent readout.** The balance delta is on screen for about two seconds; the node states also carry a bracket marker (`[/]`, `[!]`, `[#]`) and a sentence in the tooltip, so §4.4 holds — neither state is colour alone, and both survive greyscale and a screen reader.

### 2.1b The accent as authorship — DIRECT's chat bubbles (amended 2026-08-06)

§2.1 reserves `amber` for **cycles doing work and money arriving**. COMS' **DIRECT** tab fills the
player's own messages with it and the other side with a neutral ground (`-es-bubble-them`). Taken on
explicit direction, and fenced the same way §2.1a is:

| Site | Fill | Meaning |
|---|---|---|
| `.es-dm-mine` | `-es-amber` | This message is yours |
| `.es-dm-them` | `-es-bubble-them` | Everyone else — a neutral, never a second hue |

**Why this is not the semantic colour system arriving through the back door:**

1. **It is deixis, not a category.** In a two-party transcript the accent does not classify anything —
   it means *"this one is yours"*. It says nothing about value, nothing about state, and it is
   meaningless outside a conversation, which is exactly why it cannot spread. One style class, used
   nowhere else.
2. **Only ONE side is marked.** Two coloured bubbles would be a colour system, and would additionally
   claim the two speakers are two *kinds* of thing. The other side is the unmarked default — the same
   "position is the primary cue, fill the secondary" split §4.4 already asks of `Switch`.
3. **Alignment carries it, and the colour is redundant.** Own messages sit right, everyone else's
   left, and the sender's name is on every bubble regardless. §4.4 holds: greyscale, a colour vision
   difference or a screen reader all still read the transcript correctly.
4. **The bubble is a NEW GROUND, so it is measured.** `ContrastTest.chatBubblesAreLegible` computes
   the text on both fills in all eight palettes and also asserts the neutral bubble is distinguishable
   from the panel behind it — a bubble that matched the window body would leave the fill doing nothing
   while the text stayed perfectly legible, which no text-contrast check can see.

⚠ **A third site needs another amendment, not a precedent** — the same fence §2.1a sets.

⚠ **The corners obey §9.3.** Square by default, rounded only under `.es-rounded`. A chat bubble is the
one shape in this client a reader expects to be round, which is precisely why it takes the player's
setting rather than an exemption.

The steady balance keeps its amber. §2.1's "amber means money" is untouched.

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

### 3.1 The login screen — the one centred layout (2026-07-28)

The diagram above is **the deck**. The main menu is not the deck, and it is the single screen in this client that is allowed to be centred on empty ground.

```
┌──────────────────────────────────────────────────────────────┐
│                    THE EYE AND SICKLE                        │
│                   An operator's console                      │
│                                                              │
│         ( ● )      ( ● )      ( + )      ( // )              │
│        halflight  kestrel     Slot 3   Home server           │
│                                                              │
│              halflight · 0 EC · 100 cycles                   │
│              1 minute played · last seen recently            │
│                  [ Continue ]  [ Delete ]                    │
│                                                              │
│ ~/Library/…/The Eye and Sickle          [Settings]  [Quit]   │
└──────────────────────────────────────────────────────────────┘
```

A row of round faces with a name under each — macOS's user picker — over GDM's furniture: the machine's identity in a top band, the system controls in a bottom bar. Both are the same idea, and it is the idea this screen needs: **the question is "who", and everything else is chrome.**

**Why §3's tiling rule does not reach here.** Tiling exists so that a player reading four live panels never has to hunt for one. This screen has no live state and one question; filling it edge to edge would mean inventing panels to fill it *with*. The screen it replaced tried — a stacked column of slot cards, each carrying a handle, a balance, a cycle count, an hours-played line and two buttons — and made the player read six numbers before they could start playing. The numbers moved under the selected face, where they answer *"is this the one I meant"* rather than *"which of these exists"*.

| | |
|---|---|
| **Scope** | `MainMenuView` only. The boot sequence and the deck are unchanged |
| **Selection** | Follows keyboard focus, radio-group style — Tab through the row and the summary keeps up |
| **Accent** | The selected ring. §2.1's single amber is spent there, because on this screen "which one is selected" is the only state there is |
| **Alarm** | A damaged save gets its own ring. Not an accent — the one state here the player must act on |
| **Geometry** | Circles are `Circle` **nodes**, not a corner radius. §9.3's radius gate is untouched: there is no radius here to permit |

Online play is the last face in the row — macOS's *"Other…"*, GDM's *"Not listed?"*. That placement is the claim: another way to be somebody, not another mode of the game.

### 3.2 The setup assistant — the second centred layout (2026-07-28)

§3.1 carved out **`MainMenuView` by name**. This is the second and, on current plans, the last: the pane a new character is created through.

```
┌──────────────────────────────────────────────────────────────┐
│  Back                    ▪ ▪ ▪ ▫ ▫ ▫ ▫                       │
│                                                              │
│                                                              │
│                    How should it look?                       │
│         Every palette is the same deck. One stylesheet…      │
│                                                              │
│              [swatch] [swatch] [swatch]                      │
│              [swatch] [swatch] [swatch]                      │
│                                                              │
│  Cancel                                    [ CONTINUE ]      │
└──────────────────────────────────────────────────────────────┘
```

macOS's Setup Assistant, in this deck's furniture: **one decision per pane**, a large title, a short paragraph of consequence, the control, and the only weighted button on the screen in the bottom right. Seven panes — welcome, identity, picture, palette, accessibility, teaching, ready.

**Why it exists when Settings already has all of it.** Settings answers a question the player already has; an assistant tells them the question *exists*. A new player handed a deck of twenty tool windows does not know the pointer is theirs to pick, that there are six palettes, or that the game will stop explaining Unix if asked. The teaching level (CL-4 / T-2) is the clearest case — it materially changes the game, its default is right for one audience and wrong for another, and it used to be asked in a bare `Alert`.

| | |
|---|---|
| **Scope** | `SetupWizardView` only, reached from the login screen's empty-slot branch |
| **Progress** | Discrete **squares**, not dots — §4's vocabulary. A wizard's progress is genuinely countable, unlike the splash's bar (§4.1) |
| **Accent** | Continue, and the selected option in any list. §2.1's one amber, spent on "what happens next" and "which one is chosen" |
| **Options** | Rows, never a `ChoiceBox`. §9 bans hidden UI, and a pane with one question and a whole window has no argument for a menu |
| **Motion** | None. Panes swap; nothing animates |

⚠ **Only two values belong to the character** — the handle and the picture. Palette, pointer, motion, text size, hostname and teaching level are **profile-global**. The assistant runs per character and asks all of them anyway, because a player creating their second character is also a player who might now want the high-visibility palette. What makes that safe: **every global pane is seeded from the value already set**, so pressing Continue through the whole wizard changes nothing. macOS dodges the same split by asking the long questions only on first boot; that was rejected here because it hides the one screen that tells a player these options exist.

⚠ **The globals are applied live** — a palette cannot be chosen from its name — so the caller snapshots them on entry and **Cancel puts them all back**. Trying three palettes and backing out must not re-theme the character the player was already playing.

⚠ **The palette swatches carry literal colours**, one block per theme, and have to: a swatch is rendered under whichever palette is *currently* live, so `-es-` tokens would paint all six tiles identically in whichever theme is on. §10 criterion 2 is satisfied the way §4.1's splash satisfies it — the colours are in `theme.css`, they are simply not resolved from the palette. `SetupSwatchTest` reads both sides and fails the build when they drift.

⚠ **Appearance belongs to the character (amended 2026-07-28).** The assistant's palette pane originally wrote a machine-wide setting, so creating a second character silently re-themed the first. It does not any more: `themeId`, pointer skin, wallpaper, casing, the three screen artefacts, curvature, rounded corners and subwindow control order all live in `VisualSettings`, one per solo slot. The assistant previews on a **detached** copy that belongs to no character until one is created — which is what makes Cancel free rather than something that has to be unwound.

| Scope | Fields | Why |
|---|---|---|
| **Per character** | `themeId`, `cursorSkin`, `wallpaper`, `bezel`, `crtScanlines`, `crtAberration`, `crtGlitch`, `crtCurvature`, `roundedWindows`, `subwindowControlOrder` | Three characters are three operators at three rigs, and the login screen already presents them that way |
| **Machine-wide** | `uiScalePercent`, `reducedMotionOverride` | Accessibility **floors** (`docs/client/07`). Per-character would hand a player who needs 150% text 100% on every new character |
| **Machine-wide** | `nativeWindowBorder` | `Stage.initStyle` is rejected on a realised Stage — per-character it could not take effect until a restart |
| **Machine-wide** | `windowSize`, `fullScreen` | The window's geometry, not the deck's look; per-character it would resize the player's window on every save switch |

⚠ **Settings says whose look it is.** The window is reached from two places that look identical — the login screen (the machine's) and the deck (the character's) — so each appearance page carries one line naming the owner. Without it, a player who re-themes from the menu and finds their character unchanged concludes the setting is broken rather than scoped.

⚠ **`ThemeManager` caches the current `ThemeId` and paints from the cache.** Pointing the profile at a different look changes nothing on screen by itself, and `applyAll()` after a swap faithfully re-applies the *previous* character's palette. `reloadAppearance()` re-reads then applies; `VisualSettingsTest` asserts every swap in the client is immediately followed by it.

⚠ **Escape is not bound.** Nothing owns Escape before the deck (the pause-menu filter is installed in `startDeck`), and on a screen whose job is a sequence of decisions a key that discards the sequence is one keystroke away from losing a picture the player just cropped. Cancel is a control they have to mean.

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

### 4.1 The firmware splash — one continuous bar, permitted (2026-07-28)

The catalog's **Meter** row reads "Never a continuous bar or gradient." That stands everywhere a player reads a quantity, and it is the reason the cycle grid is 100 countable cells rather than a percentage. The **power-on splash** is the one exception, and it is an exception because it breaks none of what the rule protects.

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│                                                              │
│                       u   ((O))   S                          │
│                       ▲     ▲     ▲                          │
│              fades in at 12–46%   │   fades in at 46–80%     │
│                       the ring is the O, lit the whole time  │
│                                                              │
│                   ▬▬▬▬▬▬▬▬▬▬▬▬▬▭▭▭▭▭▭                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**The bar measures nothing.** There is no work to wait for — solo loads in milliseconds — so it is not a progress indicator that happens to be smooth; it is time passing. §4's ban exists so that a countable quantity stays countable, and turning *this* into nine discrete cells would make it look like a reading of something. The rule holds precisely by not applying here.

| | |
|---|---|
| **Scope** | `PowerOn` only. One bar, 248 × 6, in the whole client |
| **Fill** | White on a 14%-white track. Not a palette colour at all — see below |
| **Ends** | Rounded by `Rectangle.arcWidth`, a **shape**. §9.3's radius gate is on `-fx-background-radius` and is untouched |
| **Motion** | Slides, per frame, off an `AnimationTimer` — see §5.1. No `Timeline`, no `Interpolator` |
| **Reduced motion** | Skipped whole. §5 makes atmosphere the first thing to go, and this screen is only that |

**Two boot screens, and the order is the fiction.** `PowerOn` is *firmware*: it plays once per process, before the login screen, and it knows nothing because no operator has been chosen yet. `BootSequence` is *uOS*: it plays after a character is opened and every line it prints is that save's real state. Power on → who are you? → uOS → the deck. Once per **process**, not per visit to the menu: returning from a game is a logout, and a machine that cold-boots on every logout has a fault.

**The mark is a glowing ring, and the ring is the O.** `u` and `S` fade in on either side of it as the bar fills, so what has been on screen the whole time turns out to have been the middle letter of **uOS**. The letters arrive on the *bar's* progress rather than on a timer of their own — the thing the player watches complete and the thing that completes are the same thing.

⚠ **White on black, and the palette cannot reach it.** Every other surface in this client is themed; this one is not. Firmware runs before anything knows who the player is, and a splash in their chosen accent would be claiming otherwise. Nothing in the block resolves an `-es-` palette token — `.es-poweron` declares its own two colours, so the five overlays have nothing to override. §10 criterion 2 still holds: the colours are in the stylesheet, they are simply not the palette's. The visible consequence is at the handover, where black gives way to the menu's own ground — invisible on the four dark palettes, a real change on `classic`. That reading is correct: the firmware is the machine's, the desktop is yours.

⚠ **The glow is concentric strokes, not an effect.** §9 still lists drop shadows, blur and glassmorphism as build-blocking — the 2026-07-28 amendment reversed the *rounded corner* ban and left that one standing — and `UiContractTest` fails the build on a `dropshadow(` anywhere in the stylesheet. So the halo is eight circles sharing a centre, six outside the bright ring and two inside it, and it took two passes to get right: **the first spaced four strokes evenly and read as four concentric circles.** A glow is a falloff, and a falloff drawn in strokes needs the strokes to overlap — the offsets are close and the widths are wide, so their alphas accumulate. The halo also overflows its own layout box on purpose; a box that contained it would push `u` and `S` seventeen points further out on each side and the three characters would stop reading as a word.

The halo **breathes**, on a sine over its opacity, on wall time rather than on progress — a glow that slowed as the bar filled would be reporting on a load, and there is nothing to report. That is continuous motion, permitted by §5.1 and nowhere else.

---

**On greeble:** it is not decoration to be cut in review. It is the single largest difference between this look and a dashboard. Budget roughly 10–15% of pixels to information that carries no meaning.

---

## 5. Motion

**Step and linear timing only.** Any spring, bounce, or ease-out reads as web UI immediately and will undo the whole aesthetic.

> **§5.2 — ONE SPRING IS PERMITTED, on explicit direction (2026-08-06), and it is fenced.**
>
> The Bluesky mark at the top of COMS' DIRECT tab winds up, releases through a full turn and settles
> (`ui/widgets/SyncSpin`). That is a spring, which the sentence above and §9's rejection list both
> name. It is allowed under four conditions, **all of which must stay true**:
>
> 1. **No new animation machinery.** No `Interpolator`, `Timeline`, `KeyValue` or `AnimationTimer` —
>    `UiContractTest` rations all four and none is touched. The motion is a **hand-authored table of
>    absolute angles** walked one entry per `Pulse` tick, the same stepped mechanism `Motion.reveal`
>    and the ring wallpaper already use. A **table, not a function**: a formula would be an easing
>    function sitting in the source for the next person to import, at which point §5 has been
>    abandoned rather than amended.
> 2. **One widget, one mark.** Not a shared easing utility, and it must not become one. The day a
>    second caller wants it is the day to ask whether §5 is being kept at all.
> 3. **It runs only while a real network sync is in flight**, and stops dead at rest. It is a
>    progress indicator, not decoration — which is what earns it a place.
> 4. **Reduce motion holds it still.** `Pulse.animate` never fires there, and nothing is lost: the
>    pane says "Syncing conversations…" in words.
>
> ⚠ The honest reading is that the table's *shape* is an easing curve however it is spelled — the
> ring wallpaper's note makes exactly that argument against a sine envelope. What is defensible is
> that it is confined to one 20px mark that turns only while real work is happening, and that
> removing it is deleting one file. `SyncSpinTest` is the fence.

| Event | Treatment |
|---|---|
| Panel reveal | Horizontal clip wipe, ~0.34s, **9 discrete steps**, staggered per pane |
| Value refresh | Values **twitch** — jump to the new figure with no interpolation |
| Text arrival | Types in character by character; never fades |
| In-progress work | Linear sweep bar, ~2.6s loop |
| Thermal recovery cells | Blink between two states on a 2-step loop |
| Caret | 1.06s step blink |

Numbers that count up are fine. Numbers that smoothly tween are not.

### 5.1 The firmware handover — a continuous fade, permitted (2026-07-28)

**Step and linear timing only** stands for the interface. It does not reach the power-on splash, which is a title card.

The distinction the section is really drawing is between motion the player is **working inside** and motion they are only **watching**. A panel that fades in makes them wait to read it; a value that tweens is a number lying about what it is. Neither applies to a splash handing over to a login screen: nothing is readable during it, nothing is interactive, nothing is being measured. `Motion`'s own header made the anti-fade argument, and that argument was about a *panel* — it is still correct there.

| | |
|---|---|
| **Where** | `PowerOn`'s progress bar, and the crossfade from the splash to the login screen. Nowhere else |
| **Mechanism** | `AnimationTimer`, ramping from elapsed nanoseconds. **No `Interpolator`, no `Timeline`** |
| **Duration** | 420ms each way, fading through the ground rather than between two screens |
| **Reduced motion** | Both jump to their final state, and the splash is skipped whole |

⚠ **`AnimationTimer` is rationed by filename, and that check is load-bearing.** A `Timeline` + `KeyValue` interpolates with `Interpolator.LINEAR` **by default**, so a fade could be added anywhere without the word appearing in the source — passing §10 criterion 7's existing check by never tripping it. `UiContractTest` therefore asserts `AnimationTimer` appears in exactly `Fade.java` and `PowerOn.java`. A third user is a decision someone makes on purpose.

⚠ **The content fades, never the ground.** The Stage is `TRANSPARENT` (§0) and the scene root paints the ground colour; ramping the root's opacity shows the window through itself for a fifth of a second. Both ends fade their *content* over a black that never moves.

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

### 0.1 The system window border — permitted as an opt-in (amended 2026-07-28)

**§0 says the entire aesthetic depends on the player never seeing their own operating system, and §10 criterion 1 makes no visible OS chrome an acceptance criterion. Amended on explicit direction, to allow more user controllability** — the same direction §9.1 took for screen artefacts and §9.3 for rounded corners.

| | |
|---|---|
| **Setting** | Settings → Desk → *Use the system window border* |
| **Default** | **Off.** §0 and §10 criterion 1 still describe what ships |
| **Effect** | The game window is `DECORATED`; the deck stops drawing its own `[−] [+] [×]` and the top strip stops being a drag handle |
| **Restart** | ⚠ Required, and unavoidably — `initStyle` is rejected on a realised Stage, and `DECORATED` and `TRANSPARENT` cannot both be true of one window |

⚠ **Two things must switch off with it, and both are correctness rather than polish.** The deck's own window controls, because two sets of minimise/maximise/close on one window is not redundancy — it is a question the player has to answer every time they want to quit. And the top strip's drag handler, because the OS title bar already drags the window and a second handle inside the content *fights* it: press the strip and the window jumps by the offset between the two.

⚠ **Rounded corners become the OS's business.** With a native frame the outer corners belong to the window manager, so the scene-root clip is not applied — clipping it would cut the game away *inside* a square frame and leave a visible gap. Desk windows still round; those are the deck's own furniture.

**The title changes with it, too.** Undecorated, the title is invisible in-game and its only reader is the OS window list, so it carries the *application* name (the only lever Windows offers). With a native frame the title bar is on screen and is the game's own furniture, so it says *The Eye and Sickle*.

---

## 9. Rejection list

Any of these individually undoes the look. Treat as build-blocking.

- ~~**Rounded corners**~~ — **amended 2026-07-28, see §9.3.** Permitted as an opt-in setting, off by default, and narrowly scoped. Drop shadows and blur are unchanged and still cut. ~~**Glassmorphism**~~ — **amended 2026-08-05, see §9.4:** permitted as a theme, off by default, under §9.1's four conditions. Shadows and blur are *not* included in that and remain build-blocking — and unreachable anyway, since JavaFX exposes no backdrop filter.
- A second accent hue, or a semantic color system
- Easing curves — spring, bounce, ease-in-out, ease-out — ⚠ **one exception, §5.2**: `SyncSpin`, fenced by four conditions
- ~~**Native window chrome of any kind**~~ — **amended 2026-07-28, see §0.1.** Permitted as an opt-in for the main window only, off by default. Tool windows are still drawn by the deck and always will be; §0's cancellation of the `Stage`-per-tool model is unchanged.
- Hidden UI: hamburgers, modals, collapsed drawers, accordions
- Proportional (non-mono) type anywhere, including body copy
- Gradient fills — hazard stripes and the sweep bar are the only gradients, both hard-edged or near-transparent
- Icon fonts and Material/Lucide icon sets — glyphs are drawn from ASCII and box-drawing characters
- Removing greeble because it "doesn't do anything"
- ~~**Bezel**~~ — **amended 2026-07-27, see §9.2.** A drawn casing is now permitted as an opt-in setting under §9.1's four conditions. *Screen curvature* is unchanged and still cut (§9.1 permits only the rim aberration, never a warp).
- **Vignette** — corner and edge darkening. Still cut: it dims real content by position rather than by meaning, and the corners are where tiled windows go.
- Any screen artefact that is **not** switchable off by the player (see §9.1)

### 9.3 Rounded corners — permitted as an opt-in (amended 2026-07-28)

**This list previously read "rounded corners… treat as build-blocking". Amended on explicit direction, to allow more user controllability.** The direction is the same one §9.1 already took for screen artefacts, and the same conditions apply.

| | |
|---|---|
| **Setting** | Settings → Desk → *Rounded window corners* |
| **Default** | **Off.** §9's rejection list still describes what this client looks like out of the box |
| **Scope** | The outer Stage and the desk's window frames. Nothing else |
| **Radius** | 6px, one value, in one CSS block gated on `.es-rounded` |

⚠ **It must never round anything a measurement is read off.** Not a meter cell, not the cycle grid, not a hazard band, not a character-cell texture. A cell with a soft corner reads as a *smaller cell*, and the entire point of a discrete meter (§4) is that a player can count it. Rounding a window is taste; rounding a measurement is a lie about a number.

That boundary is machine-checked — `UiContractTest.RoundedOptIn` fails the build if a non-zero radius appears outside an `.es-rounded` rule, or if such a rule names any of the measurement classes. The older assertion ("radius is 0 everywhere") was replaced rather than deleted, so the contract still has teeth; what changed is what it is a contract *about*.

**Why an opt-in rather than a straight reversal.** The failure §1 names is *a competent dark-mode developer tool*, and hard edges are most of what keeps this deck from being one. A player who prefers soft corners on their own screen is not a design problem; a shipped default that quietly drifts toward the generic is. Off-by-default keeps the identity and gives the choice away.

### 9.4 Glassmorphism — permitted as a theme, on the same conditions (amended 2026-08-05)

**§9.3 said in as many words that "drop shadows, blur and glassmorphism are unchanged and still cut".** Glassmorphism is permitted now, on explicit direction, by the same mechanism and under §9.1's identical four conditions. **Drop shadows and blur are NOT permitted and nothing below relaxes them.** Two themes ship, in Settings → Appearance:

| Theme | Id | What it is |
|---|---|---|
| **uOS Modern Liquid Abs — dark** | `liquid-dark` | Translucent graphite glass on a cold desk, under a lit rim |
| **uOS Modern Liquid Abs — light** | `liquid-light` | Bright glass on a cool grey desk |

The reference is macOS Tahoe's Liquid Glass. What was taken from it is the **material**; what was not taken is its colour vocabulary — see the accent note below.

**The four conditions, and why two of them are structural here rather than maintained by hand:**

1. **Off by default, switchable off permanently.** `Deck` is still the default and these are two entries in a picker. A theme is opt-in *by construction* — unlike a scanline setting there is no state in which a player gets this without having chosen it, and choosing something else removes it completely.
2. **No artefact may reduce the legibility of a figure the player is required to read.** Measured, not asserted — see the legibility section below, which is the whole of the engineering here.
3. **No blur *as a stylesheet effect* — and a real backdrop blur, which is a different thing.** ⚠ This condition read *"still no blur; JavaFX makes it impossible"* until **2026-08-05**, and that was true only of CSS: there is no `backdrop-filter`, and `-fx-effect: gaussianblur(...)` blurs a node's *own* content, i.e. the panel's text. It was never true of the toolkit. `ui/chrome/Frost` snapshots what is beneath each window, blurs the image, and paints it under the panel — a genuine backdrop blur, reached the long way round.

   What §9 actually objects to is **blur and shadow applied to the interface itself**, which softens edges the design language spends its whole geometry budget keeping hard. That ban is untouched, and `UiContractTest.rejectionListHolds` still scans **every** stylesheet for it — a palette overlay being exactly where "just a touch of blur to sell the glass" would land. Blurring *a picture of what is behind a window* leaves every edge, hairline and glyph in the interface exactly as sharp as before.
4. **Motion obeys §5.** Neither palette animates anything. §5 never comes up.

**Nothing about the component sheet changed.** These are palette overlays of about forty lines, like every other theme (`ThemeId`), so the argument §0 makes for one hand-written stylesheet is untouched: a widget still cannot look right in one variant and broken in another, because there is still exactly one set of component rules.

#### What "glass" is built from, given there is no blur

Three things, and the first is the one everybody reaches for and the least important:

1. **Transmission** — the panel is translucent, so the desk shows faintly through it.
2. **A specular rim** — a lit edge along every panel boundary. This does most of the work. It costs no new component rule: `-es-rule-hi` already paints `.es-panel-edge`, the 1px band the base sheet draws around every window, so brightening that token lights every panel at once.
3. **Elevation by brightness** — a raised surface is lighter. §2.1's *"depth comes from brightness, never from shadow or blur"* was already the rule, and it is exactly what glass wants; here the lift does the job a shadow does in the reference.

⚠ **The first build tuned the desk-to-panel step against the deck palette's and was wrong.** The deck's step is nearly invisible, which is correct for an interface built of hairlines with no fills and wrong for glass, **where the lift *is* the material.** A pane level with its ground is not a pane.

#### ⚠ Transmission is bounded above, and this is the part that will be got wrong again

Without blur, a translucent panel does not soften what is behind it — **it shows it, sharp.** Desk windows overlap. So the alpha is bounded by two separate failures, and **both were found by rendering, neither by review:**

- **Content behind.** A window under another window shows its text through it. Two columns of interleaved monospace is not a material, it is a rendering fault. Residual contrast is `(1 − alpha)` times the original.
- **Texture behind, and this is the binding constraint.** At 12% transmission the desk substrate's rows of hex came through every panel as horizontal **banding** — on the light palette, indistinguishable from a failing display. It is content-behind-glass again, but the content is *texture*, so it slips under a bound written for text while looking worse.

⚠ **Those two findings are why the palettes were tuned twice, and they are what a real blur then made obsolete.** With `ui/chrome/Frost` behind them the panels run at **80% (dark) and 68% (light)** transmission — roughly twice what was survivable without it — because what shows through is no longer a picture of the desk but a Gaussian blur of it. The two findings below stand as the record of what transparency costs *without* a blur, and as the constraint any future unfrosted palette is still held to:

- **The film must be LIGHT, and that is physics rather than taste.** A frosted pane scatters additively — it lifts what is behind it toward grey rather than tinting it darker, which is why a macOS glass panel over a black desktop is a mid grey. Measured at the same alpha and desk: a mid-toned film leaves the text behind it at **4.95:1** (plainly readable, a second screen), a light film at **2.03:1** (present, not readable). The authentic choice is also the only survivable one. The cost is that the dark palette's panel composites to a mid graphite and its greys are near-whites — again what the reference actually looks like.
- **⚠ The numeric bound is necessary and NOT sufficient.** A build measuring **2.78:1** — comfortably legal — rendered the notification stack over the LOG window as two columns of text occupying the same pixels: each individually below the legibility floor, the pair unreadable. A per-pair luminance ratio cannot see two texts competing for the same glyph cells. **Passing the test does not mean a palette is legible. Render it.**

`ContrastTest.whatShowsThroughIsNotReadable` holds the floor, and the shipped palettes sit well under it.

#### How the blur is actually done (`ui/chrome/Frost`)

There is no backdrop filter, so the blur is built from `Node.snapshot`: render what is beneath a window to an image, blur *the image*, and put it under the window's translucent panel. Three decisions make it affordable, and each is a trap avoided:

- **The capture is the whole desk, not the window's rectangle.** That sounds wasteful and is the opposite: a window's backdrop then changes only when the *content* behind it changes, never when the window moves. Dragging repositions an existing image — free, and pixel-accurate, since a translation over a static backdrop is exactly a translation of the backdrop.
- **It is captured at 0.4 scale.** A blur discards high frequencies by definition, so capturing them first is work whose only product is thrown away. The smoothed upscale contributes to the softening rather than fighting it. ⚠ The radius is therefore in *downscaled* pixels and is ~2.5× larger on screen — and JavaFX caps `GaussianBlur` at 63, which is a cap on the small number, not the visible one.
- **One capture per window, bottom-up.** A window must not see itself or anything above it. Hiding everything once and revealing one frame at a time gives frame *n* exactly frames *0..n-1*.

⚠ **It is deferred and coalesced, never synchronous.** `snapshot` forces a CSS and layout pass, so calling it from inside one — where most of these events originate — re-enters layout. And a single window opening fires several notifications, each of which would otherwise re-render the desk once per window.

**It refreshes at 24 fps, on its own clock** (`UiTokens.FROST_MS`) — deliberately *not* `Pulse`, which ticks at 100 ms and quantises every subscriber to a multiple of it, so a request for 24 fps rounds silently to 10; reaching 24 through Pulse would mean speeding up every decorative widget in the client to fix one of them.

⚠ **Every decision here came out of the numbers.** Four windows at 1600×1000, per full refresh:

| | cost | ceiling |
|---|---|---|
| One capture per window *(a real compositor's semantics)* | **~40 ms** | 24 fps — the entire thread |
| …each cut to its own window's rectangle | **~37 ms** | 27 fps |
| One shared capture for everything | **~9 ms** | ~110 fps |
| **What ships:** shared + one per *overlapping* window | **8–34 ms** | 127 fps tiled |

⚠ **Row two is the counter-intuitive finding: `snapshot` renders the whole node whatever the viewport says — the viewport only crops the result.** Cost is the *number* of snapshots and barely at all their size, which is why shrinking them bought 7% and taking fewer bought everything. It is also why the capture scale has diminishing returns: at 0.22 a cascaded cycle was still 32 ms.

#### Shared where that is exact; per-window where it is not

A single capture is taken with every frame hidden, so it is the desk. Handing it to a window sitting on top of another shows blurred *desk* where the window beneath should be — a hole punched through the stack rather than glass. **That shipped for one build and was wrong.**

⚠ The resolution is that a shared capture is not an approximation for most windows, it is **exact**: if a window's rectangle does not overlap any lower window, the desk genuinely is all that is under it. So only windows that really do overlap get their own capture — **none of them in the tiled layout**, where windows abut and share edges. Correctness everywhere, paid for in the worst case rather than in every case.

⚠ **The tiled layout is therefore the wrong one to check this in**, which is exactly how it went unnoticed: every render was tiled. `-Ddeck.cascade` on the render harness leaves windows overlapping.

#### 24 fps is a ceiling, not a rate

⚠ Paced against `UiTokens.FROST_BUDGET`: the desk measures each refresh and will not start the next until the gap is at least `cost / budget`. A fixed 24 fps would hand the thread to the blur exactly when the player has the most on screen — the deck would stutter under the interaction that caused it. So the frost stays **correct** at any window count and only its *frequency* degrades: the full 24 fps tiled, around 7 fps with four windows cascaded.

⚠ **Reduced motion stops the clock and falls back to the event-driven path**, so the frost is still correct after every interaction and simply never moves on its own — WCAG 2.2.2 satisfied without withdrawing the effect. A frost that merely froze would be *wrong* rather than still: it would show a desk that has since changed.

#### ⚠ A window may be glass; a well sunk into one must transmit; a thing that floats over content must not.

Three grounds, three rules, and the middle one was missed on the first frosted build:

- **`-es-panel`** — the window body. Glass.
- **`-es-well`** — anything sunk *into* a panel: a terminal's scrollback, a table body, a text field, the cycle grid's field, the map canvas, the calculator. It **aliases `-es-void`**, so on the six opaque palettes a well is the app ground exactly as before. ⚠ It exists because `-es-void` does two unrelated jobs — it is the desk *and* every recess — and glass needs those to part company: the desk stays opaque (it is the bottom of the stack), while a well painted with it punches a **black box through the glass**, which is exactly what the terminal, file manager, map, manual and calculator looked like. In the glass palettes it is a *tint over the frost*, darker than the panel (a recess reads as a recess by being darker — §2.1's depth-from-brightness, in the one direction that is not "raised") and more opaque than it (a terminal's whole content sits directly on it, and a blurred bright patch behind small monospace is where transmission costs real legibility).
- **`-es-float`** — anything floating over content: toasts, dialogs, context menus, tooltips, the download dock, the sync banner. Opaque.

#### ⚠ A window may be glass. A thing that floats over content may not.

`-es-float` is the ground for anything that floats: toasts, dialogs, context menus, tooltips, the download dock, the sync banner. It **aliases `-es-panel-hi`**, so on the six opaque palettes it changes nothing and a floating surface is the raised surface exactly as before; the two glass palettes override it with an opaque literal.

The rule it encodes is the one the render taught: a window body may transmit because what is behind it is *usually the desk*, but an alert is over content by definition, and **an alert you cannot read is not an alert.**

#### ⚠ Measuring a translucent palette needs compositing, and the naive reading is worse than no check

`ContrastTest`'s token pattern was `#[0-9A-Fa-f]{6}`. Against an eight-digit `#RRGGBBAA` **that does not fail — it matches the first six digits and silently drops the alpha**, so every contrast assertion in the client would have gone on measuring text against a panel colour that is never on screen, and reported a pass. A check that quietly measures the wrong thing is worse than no check, because it is believed.

The class composites now: the panel is measured over the desk, and the raised surface over the **panel** (a header strip sits inside a window, so two glass layers stack). Both liquid palettes clear the 3:1 floor with better margins than the deck palette itself.

#### The accent stays warm amber (explicit direction)

The reference is unmistakably blue-accented and this is **not**. §2.1 calls the warm-accent-on-cool-ground temperature split load-bearing, and a blue accent standing beside the existing `gain` green, `warn` orange and `alarm` red is the semantic colour system §2.1 bans, arriving one token at a time. So `-es-amber` keeps its single meaning — cycles doing work, or income — in each palette's own register: a lighter sodium on the dark glass, and a burnt amber on the light one for the reason uOS Classic already records (bright sodium measures ~1.7:1 on a near-white panel, which would turn the one meaningful colour into decoration).

**The material is the reference's. The vocabulary is the game's.**

#### ⚠ These themes round windows, and §9.3's boundary is what makes that safe

Glass with hard corners is not the material, so `ThemeId.roundsCorners()` is true for both. It does **not** write the player's §9.3 setting — a costume has to come off cleanly, and a player who tried the theme for thirty seconds must not find their deck permanently round with nothing to say why. The setting is OR-ed with the theme at the two places that shape a window, via one helper so they cannot disagree.

⚠ **It reuses the existing `.es-rounded` class rather than introducing a theme-scoped radius**, deliberately. §9.3's ⚠ *"never round a measurement"* is machine-checked against that one selector; a parallel one would have been a second place for that boundary to be forgotten. Under Settings → Windows the switch shows the **effective** state and is disabled with a line saying who decided it — a control that appears to do nothing reads as broken, and players blame the control.

**§9.3's rejection-list entry is otherwise unamended.** The shipped client is still square-cornered, because the shipped theme is still `Deck`.

---

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

### 9.2 Bezel — permitted as a casing, on the same conditions (amended 2026-07-27)

**§9 cut bezel and §9.1 pointedly kept it cut when four other artefacts were permitted.** It is permitted now on explicit direction, by the same mechanism and under §9.1's identical four conditions. Ten styles ship, in Settings → Screen → **Casing**:

| Style | Margin | What it is |
|---|---|---|
| `Off` *(default)* | 0 | The shipped look, unchanged |
| `Hairline` | 10 | Two thin rules — the quietest |
| `Corner brackets` | 14 | Brackets and ticks, open in the middle |
| `Ruled edge` | 12 | A tick scale, heavier every fifth |
| `Casing` | 26 | Vents, fixings, a port block, a designator plate |
| `Cable loom` | 30 | Three dressed cable runs, junctions, terminators |
| **`Gothic plate`** | 46 | Riveted plate, corner buttresses, hazard chevrons |
| **`Terminal panel`** | 40 | Blinking status lamps, toggle switches, a grille |
| **`Chrome 3.1`** | 30 | Raised bevel, title bar, drawn control boxes |
| **`Motif`** | 34 | Double bevel, corner grips, square buttons |

**Two of these deliberately imitate window chrome, and §9 bans "native window chrome of any kind".** That ban protects §0's premise — *the player never sees their own operating system*. A thirty-year-old window manager is nobody's operating system: `Chrome 3.1` and `Motif` read as a retro **machine**, not as the host showing through, which is the thing the rule exists to prevent. They are also opt-in and off by default, like everything else here.

**The bevel on those two is legal on its own terms.** §2.1 says depth comes from **brightness, never from shadow or blur**, and a bevel is exactly a light top-left edge against a dark bottom-right one. No `DropShadow`, no blur — §9's ban on both is untouched and still machine-checked.

⚠ **`Gothic plate` is genre, not iconography.** Rivets, plate, buttresses and hazard chevrons — deliberately none of the protected emblems the obvious reference is known for. Construction is free to borrow; insignia are not.

**`Casing` and `Cable loom` are the machine ones.** Casing carries vent slots, corner fixings, a port block down one flank and a stamped designator plate; the loom is three cable runs dressed at different inset lanes, turning at right angles, with junction clamps at the bends and terminator blocks where each run ends. Everything on both is a flat hard-edged shape in a palette token — vents and ports are the void showing *through* the band rather than marks painted on it, which is what makes them read as holes in a panel.

> **⚠ The detailing is asymmetric on purpose.** Vents along the top, ports down the left, the plate bottom-right. Real equipment has a front, and a border with identical trim on all four sides reads as a *picture frame* — which is precisely what §9 objected to about bezels. Asymmetry is what makes it read as a fabricated object instead of a decoration around a picture.

⚠ The junction clamps are the **one** place the casing takes the accent, and it keeps its meaning: a live connector is the only part of a machine's shell that is actually powered, and §2.1 reserves amber for live. Nothing else on the casing may borrow it.

### The resolution is the viewport's, and the casing is outside it (2026-07-27)

Settings → Window sets the **screen inside the machine**, not the OS window. Choosing 1920 × 1080 gives the deck 1920 × 1080 and the casing is added beyond it, so the window the desktop has to find room for is `(resolution + 2 × casing) × scale`. Measured: a 1280 × 800 viewport with the 26px `Casing` produces a 1332 × 852 window.

Before this the casing was subtracted *from* the resolution — a 20px casing turned a 1920-wide choice into an 1880-wide deck, and the number in Settings described something the player never actually got.

⚠ **This decoupled the UI scale from the viewport, and that is a real simplification.** The window used to be sized *to* the resolution with the deck laid out at `physical / scale`, so 1280 × 800 at 150% gave the deck 853 logical pixels and fell under §3's 860 floor — which is why Settings had to hide size/scale combinations. Now the window is sized *from* the viewport: the deck always gets the full resolution in layout units and the scale only changes how large those pixels are drawn. Every preset clears the floor at every scale, and the single remaining constraint is whether the resulting window fits the display.

What makes this safe rather than a hole in the list is that condition 2 is **structural here, not maintained by hand**:

> **⚠ The casing is drawn in a MARGIN, and the deck is inset by exactly that margin.** It never overlays content. A casing painted over the top strip would hide the compute readout, which is client pillar **C2** — and C2 is the one thing §3's layout was rearranged to make impossible to lose. `BezelStyle.margin()` is both the inset applied to the deck and the entire width the frame has to draw in, so a style that wanted to draw wider than its own margin would have to change the inset too. `BezelStyleTest.everyStyleOwnsAMargin` fails the build on a style with no margin.

The other three conditions carry over unchanged:

1. **Off by default, switchable off permanently.** `Off` is the shipped look. Asserted against a fresh profile, not just against the enum.
2. **No blur, no glow.** Flat fills and hairlines with hard edges — the same vocabulary the panels already use. Unchanged and still machine-checked.
3. **What moves obeys §5.** ⚠ This read *"nothing here moves"* until `Terminal panel` landed with blinking status lamps. The rule was never "a casing must be still" — §9.1's actual condition is that motion steps rather than tweens and that `prefers-reduced-motion` stops it. The lamps run on `Pulse.animate`, the decorative channel, which is exactly that. Every other style is inert.
   > **⚠ Reduced motion freezes the lamps LIT, not dark.** A panel whose indicators all went out would read as *powered off* — a wrong statement about the machine, where a still lamp is merely a less lively one.

**⚠ Vignette is still cut, and this does not reopen it.** The argument against a vignette was never about frames — it is that it *"dims real content by position rather than by meaning, and the corners are where tiled windows go."* A casing in a margin dims nothing, because no content is ever underneath it. The two are not the same request and the reasoning does not transfer.

**⚠ Screen curvature is still cut too.** §9.1 permits the radial rim *aberration* and explicitly not a warp, because a real barrel distortion needs a per-pixel remap and would put every click somewhere other than where the player sees the control. A casing does not bring that back — it is drawn beside the interface, not over it, and it is mouse-transparent besides.

**Corner brackets are open in the middle deliberately.** A frame that closes on all four sides is the thing §9 objected to in the first place: it puts the interface inside a *picture of a device*. The bracket style leaves the middle of each run open so it reads as an overlay on a machine rather than as a photograph of one.

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
- **The 100-cell grid is one cell per cycle, not per percent.** A starting rig is exactly 100 cycles (`engine/Balance.STARTING_CYCLES`), so the reference's hundred cells are literal — and the grid grows with the rig rather than rescaling, which keeps "compute is countable" true at every rig size.
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
