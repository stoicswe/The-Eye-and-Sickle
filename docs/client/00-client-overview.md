# 00 — Client Overview & Design Vision

**Status:** ⚠️ **[PROPOSAL]** — the *stack* and the *shape* are Established (JavaFX + AtlantaFX, one `Stage` per tool, the client as a non-authoritative view layer: `../architecture/01-tech-stack.md` §1, `../design/00-vision-and-pillars.md` §6, Invariant I14). Everything this doc adds on top of that — the client pillars, the two theme families, the educational layer, the window catalogue, the shortcut map — is first-pass design filling a gap the source material never covered. Tagged inline where a statement is Established.
**Depends on:** `../design/00-vision-and-pillars.md`, `../design/01-core-resources.md`, `../design/05-hacking-minigame.md` §5, `../architecture/01-tech-stack.md` §1, `../design/glossary.md`
**Depended on by:** `01-visual-language.md` and every subsequent doc in `docs/client/`; the `client/` module implementation

---

## 1. What the client is

> **The experience goal, in one sentence:** you are an operator at a desk, and every window on your screen is a tool you actually own, showing you what is true right now and what it cost you to know it.

The client is a **cross-platform JavaFX desktop application** that renders server-owned game state and sends player intent. It is not a game engine scene, not a launcher, and not a wrapper around a web view. Its subject matter — terminals, node graphs, dashboards, ledgers, log files — *is* UI, which is precisely why a windowing toolkit beats a game engine here (`../architecture/01-tech-stack.md` §1).

Three facts are Established and constrain everything else in `docs/client/`:

| Fact | Source | Consequence for design |
|---|---|---|
| One OS window (`javafx.stage.Stage`) per tool | `../design/00-vision-and-pillars.md` §6, `../architecture/01-tech-stack.md` §1 | Layout is the *player's*, not ours. We design windows, not screens. |
| JavaFX 26 + AtlantaFX 2.1 for theming | `../architecture/01-tech-stack.md` §1 | The design system is CSS-first and must express itself as looked-up colours and style classes (`01-visual-language.md` §1). |
| The client is never authoritative (I14) | `../design/00-vision-and-pillars.md` §4, `../architecture/01-tech-stack.md` §1 | The UI must never *imply* it decided an outcome, and must be visibly honest about pending/stale server state. |
| A single-window docked fallback **must** exist | `../architecture/01-tech-stack.md` §1, `../design/05-hacking-minigame.md` §5 | Multi-window is the default and the fantasy; it is never the only option. Flagged for `design:accessibility-review` in both source docs. |

### 1.1 What the client owns, and what it only renders

This split is the working form of I14. It is worth stating once, precisely, because every ambiguous case downstream resolves against it.

| The client genuinely owns | The client only renders |
|---|---|
| Window layout, sizes, positions, which tools are open | Compute totals, allocation, availability, recovery (`../design/01-core-resources.md` §1) |
| Theme, density, motion, tooltip-layer settings | Ethecoin balances and every ledger row (`../design/01-core-resources.md` §2.2) |
| Input: keystrokes, probes, tool selection, aborts | Noise, personal heat, server heat, faction reputation |
| Local presentation of received events (ordering, grouping, formatting) | Item ownership, storage tier placement, gate satisfaction |
| **Offline re-verification of item provenance chains** against DID public keys (`../architecture/04-item-provenance.md` §6) | Breach outcomes, trace progress, loot, consequences (`../design/05-hacking-minigame.md` §2) |
| Draft state not yet submitted (an unsent loadout, an unsent allocation change) | Deployed-miner state, yield buffers, sweep results |

Provenance verification is the single interesting exception and it exists for a specific reason: the client re-walks the chain *so that it does not have to trust the server's UI to have checked it*. That is a case of the client trusting **less**, not deciding more. The UI consequence is in §2, pillar **C4**, and the tokens are in `01-visual-language.md` §2.2.9.

---

## 2. Client design pillars

Six pillars, each derived from a game pillar (`../design/00-vision-and-pillars.md` §3) or an invariant. These are the tiebreakers: when two client-side designs are both defensible, the one that serves a pillar more directly wins.

| # | Client pillar | Serves | Fails if violated |
|---|---|---|---|
| **C1** | The interface **is** the toolset | Pillar 1, Pillar 5 | The game becomes a menu *about* hacking |
| **C2** | Compute is never off-screen | Pillar 2, `../design/01-core-resources.md` §1.4 | The master scarcity stops governing decisions |
| **C3** | Every outcome shows its cause | Pillar 4, `../design/00-vision-and-pillars.md` §5 | Losses read as dice, not as "I got greedy" |
| **C4** | The client never claims authority it doesn't have | Invariant I14 | Players learn to distrust the UI, or to blame it |
| **C5** | Legibility under pressure, never reflex | `../design/00-vision-and-pillars.md` §7 | The game drifts toward twitch action |
| **C6** | The interface teaches the real thing | New product goal (§5) | The educational promise becomes decoration |

### C1 — The interface *is* the toolset, not a menu about it

Pillar 5 says story arrives as recovered logs, emails and database records. Pillar 1 says the puzzle is the game. Both collapse if the player experiences them through a generic inventory grid and a dialogue box. So: a Passive Sniffer is a window that shows adjacent node types; the Provenance Tracer is an audit view over your own deployed network; a recovered email is *an email*, rendered as an email, in a reader — not a lore entry with a "New!" badge.

The concrete rule this generates: **a tool's UI is built from the tool's actual output shape, and its cost is shown where the tool is used, not in a shop.** Every tool in `../design/06-intrusion-tools.md` and `../design/07-recon-tools.md` carries compute and noise stats; those stats belong on the tool's own control surface, priced at the moment of commitment.

The counter-pressure to resist: diegesis is not an excuse for hiding information. Making the player squint at a fake CRT to read their balance is not immersion, it is a defect. C1 governs *what the surface represents*; C5 governs *whether it can be read*.

### C2 — Compute is never off-screen

`../design/01-core-resources.md` §1.4 is unusually direct: the player must always be able to see total cycles, allocated (by consumer), available, and recovering with time-to-recover, and the compute ledger is "the game's most important HUD element." That doc explicitly justifies a dedicated, always-on-top rig-monitor window.

This pillar makes three demands:

1. The **rig monitor** (`rig-monitor`) is the only window that opens by default, the only one that defaults to always-on-top, and the only one that cannot be permanently closed — closing it minimises it to a compact strip, it does not dismiss it.
2. **Allocation is shown by consumer**, not as a single number. A 40-cycle draw reads completely differently when it is `Breacher 22 / Miner 10 / Recon 8` (`../design/10-botnets.md` §3) than when it is one grey bar, because the player's actual decision is *which one to stop*.
3. **Recovery is shown as a distinct state with a clock**, never folded into "available." The Thermal Budget curve (`../design/01-core-resources.md` §1.3, `../design/11-rig-infrastructure.md` §2) is the stat that makes overextension hurt; if the UI shows only allocated-vs-total, that stat is invisible and the punishment reads as arbitrary.

### C3 — Every outcome shows its cause

`../design/00-vision-and-pillars.md` §5: *"Losing should feel attributable — 'I got greedy,' not 'the dice hated me.'"* `../design/05-hacking-minigame.md` §4 makes it a hard requirement for the trace meter specifically: its inputs must be legible — which action added how much — so that a loss reads as "I was too loud" rather than "the game decided."

Generalised to the whole client: **any number that moved must be able to explain itself.** Every meter, chip and readout supports an *attribution view* — hover, focus, or a keyboard toggle — that decomposes the current value into the events that produced it, newest first, with timestamps and magnitudes. This is not a nice-to-have; it is the mechanism by which Pillar 4's "escalation the player can attribute" actually reaches the player.

Correlated sweep losses (`../design/04-mining.md` §4, OQ-3) are the hardest case and the most important one to get right: a network-wide wipe must present as *one event with one cause* — your heat band, the roll, the list of what was lost, and what survived and why (Firmware Implant, `../design/11-rig-infrastructure.md` §3) — not as eleven separate "miner lost" notifications. Eleven notifications turn a designed dramatic beat into a bug report.

### C4 — The client never claims authority it doesn't have

Optimistic UI is standard practice and it is wrong here. In an ordinary app, optimistically showing a sent message is harmless. In a game whose entire anti-cheat model rests on "the server decides and the client cannot lie" (I14/I15), a UI that shows an outcome before the server confirmed it teaches the player that the client is the source of truth — and then contradicts itself the moment latency or a rejection happens, at exactly the highest-stakes moment.

The rule: **every displayed value carries an authority state**, and the UI distinguishes four of them (tokens and rendering in `01-visual-language.md` §2.2.8):

| State | Meaning | Rendering rule |
|---|---|---|
| `confirmed` | The server sent this value | Default appearance; no decoration |
| `pending` | Intent sent, no confirmation yet | Value shown as the *last confirmed* value, plus a pending marker naming what is in flight |
| `stale` | Connection degraded; this is the last value we received, with its age | Dashed boundary + age (`14s ago`) |
| `unknown` | Never received, or invalidated | Rendered as `—`, **never** as `0` |

The last row is the one with teeth. Showing `0 EC` when the client does not know the balance is the client lying, and it is exactly the class of lie I14 exists to prevent. `—` is always available and always honest.

Two corollaries:

- **The client never pre-computes a gate.** Whether an offering is affordable, whether a proof-of-skill threshold is met, whether reputation suffices — all five gates (`../design/02-unlock-gates.md`) are evaluated server-side and rendered as received. The client may show *the requirement* (that is static content) but never its own verdict on satisfaction. This mirrors the module-level ban that `client/pom.xml` enforces with the `client-is-not-authoritative` enforcer rule.
- **A refusal names its author.** "The server refused this action" and "the client could not reach the server" are different facts with different remedies and must never be collapsed into one dialog (`01-visual-language.md` §9.4).

### C5 — Legibility under pressure, never reflex

`../design/00-vision-and-pillars.md` §7: *"Not a twitch action game: time pressure exists (trace timers, backlog timers) but the skill expression is planning, reading, and triage."*

That sentence is a UI specification. Under a live trace timer with a shrinking bot backlog (`../design/10-botnets.md` §1) and a split-attention penalty applied to everything at once (§1b), the player's task is *triage*: read four surfaces, decide which one to save. The interface must therefore make **scanning** fast, and must never make **precision** the bottleneck.

Practical consequences, each of which shows up as a rule later:

- No time-critical control smaller than the platform's minimum comfortable target, and no time-critical control that requires a drag or a double-click.
- No motion that moves a number the player is reading (`01-visual-language.md` §7.3).
- No modal dialog that can block the rig monitor (`01-visual-language.md` §5.3). A modal over a live breach is a design failure, not a tradeoff.
- Alerts *accrete into a triage list*, they do not steal focus. Focus theft during a breach is the single most damaging thing this client could do.

### C6 — The interface teaches the real thing

New product goal (§5): the game is educational as well as fun. The player should come away genuinely understanding a little about operating systems, networking and computation — and the vector for that is the UI's own vocabulary, because the UI's vocabulary is Unix.

This is not a veneer. `../design/04-mining.md` §3.1 already requires it structurally: manual investigation must work against **real, consistent** process, connection and storage data, because a careful reader must be able to find a rootkit-wrapped miner by noticing that cycle totals don't add up or that a connection has no owning process. That is a real skill — reading `ps`, `netstat` and `df` output against each other — and the design already demands the client render data faithful enough to support it. C6 says: since we are already obliged to be honest about that, be honest everywhere, and then explain the terms.

The honesty rule that goes with it: **the teaching layer never teaches a fiction as a fact.** Ethecoin, noise, heat, and the five gates are game constructs. Ports, sockets, hashes, salting, rainbow tables, race conditions, side channels, onion routing, DIDs, canonicalization, and signature verification are real. Every glossary entry declares which it is (§5.3).

---

## 3. The two theme families

**The headline client decision, and the reason this doc exists.** The client ships two theme *families*, both first-class, user-selectable at any time:

- **`native`** — Native-Adaptive. The app looks and behaves like a well-made desktop application on whichever OS it is running on, following that OS's light/dark setting and accent colour.
- **`sickle-os`** — Story-Atmospheric. A Unix-terminal-register console true to the fiction, in the Cyberpunk / Blade Runner / Ghost in the Shell tradition. Identical everywhere.

### 3.1 Why two, instead of one good one

They serve different players and different sessions, and neither audience is a rounding error:

- The **native** family is for the player who wants the game to be a well-behaved program on their machine — who runs it on a second monitor next to their real work, who has an OS-wide accent colour and a high-contrast setting they chose deliberately, or who simply finds authored dark themes fatiguing. It is also the accessibility baseline: it inherits whatever the OS already does right, including the user's own contrast and motion preferences.
- The **story** family is the fantasy at full strength. `../design/00-vision-and-pillars.md` §5 wants the player to feel like an operator; the strongest version of that is a console that looks like the operator's own hardened distribution, not like a native settings pane.

Shipping only the native family would leave the fiction unserved on the surface players stare at for hours. Shipping only the story family would mean every player who needs the OS's accessibility settings is served worse than a plain desktop app would serve them. Both are real costs. Two families is cheap because AtlantaFX is CSS-first: the native family is largely *configuration* of themes that already exist, and the story family is one stylesheet implementing the same token contract (`01-visual-language.md` §1).

### 3.2 Family A — `native` (Native-Adaptive)

**What it is:** the AtlantaFX theme set, selected automatically by OS and colour scheme, with the OS accent colour applied to interactive chrome.

| Platform | Default light | Default dark | Why |
|---|---|---|---|
| macOS | `CupertinoLight` | `CupertinoDark` | AtlantaFX's Cupertino set is explicitly the macOS-flavoured pair |
| Windows | `PrimerLight` | `PrimerDark` | Neutral, high-legibility, close to Fluent's density and neutral ramp |
| Linux | `PrimerLight` | `PrimerDark` | Desktop-environment-neutral; Adwaita-adjacent without pretending to be Adwaita |

*(AtlantaFX 2.1 ships `PrimerLight`, `PrimerDark`, `NordLight`, `NordDark`, `CupertinoLight`, `CupertinoDark`, and `Dracula` — verified against the project's own listing. `NordLight`/`NordDark`/`Dracula` are offered as manual choices inside the native family; they are not auto-selected.)*

**What "adaptive" concretely means** — the client reads `javafx.application.Platform.getPreferences()` and reacts to changes live. The relevant properties, verified against the JavaFX API docs:

| Preference | Type | Since | Client behaviour |
|---|---|---|---|
| `colorScheme` | `ReadOnlyObjectProperty<ColorScheme>` | JavaFX 22 | Switch between the light/dark member of the active native theme |
| `accentColor` | `ReadOnlyObjectProperty<Color>` | JavaFX 22 | Overrides accent/interactive chrome only — see the rule below |
| `reducedMotion` | `ReadOnlyBooleanProperty` | JavaFX 24 | Disables all non-essential motion (`01-visual-language.md` §7.4) |
| `reducedTransparency` | `ReadOnlyBooleanProperty` | JavaFX 24 | Overlay/scrim translucency becomes opaque |
| `persistentScrollBars` | `ReadOnlyBooleanProperty` | JavaFX 24 | Scrollbars are always visible rather than auto-hiding |
| `reducedData` | `ReadOnlyBooleanProperty` | JavaFX 24 | Reserved; no current client behaviour (see **CL-6**) |

JavaFX 25 added CSS media-feature queries and JavaFX 26 extends them, so most of this can also be expressed in the stylesheet — `@media (prefers-color-scheme: dark)`, `@media (prefers-reduced-motion: reduce)`, `@media (prefers-reduced-transparency: reduce)`, `@media (-fx-prefers-persistent-scrollbars: persistent)` (verified in the JavaFX 26 CSS Reference). Prefer the CSS route for pure styling and the Java property route for behaviour (e.g. skipping an animation entirely rather than shortening it).

> **The accent-colour rule, and it is load-bearing:** the OS accent colour may drive **only** the chrome-accent tokens — focus rings, selection, primary buttons, links. It may **never** drive a game-semantic token. A player whose system accent is red must not end up with a UI where "compute available" or "breach succeeded" is red. Game semantics own their hues (`01-visual-language.md` §2.5); chrome borrows the user's.

### 3.3 Family B — `sickle-os` (Story-Atmospheric)

**What it is:** an authored dark console theme, shipped as a custom stylesheet implementing the same token contract, with its own bundled typefaces so it renders identically on all three platforms. In fiction, it is the hardened operator console a Sickle cell would actually run — which is why it is named like a distribution rather than like a colour scheme.

Register targets, in order of influence:

- **Ghost in the Shell (1995)** for the *information density and calm*: dense readouts that assume competence, no hand-holding chrome, cyan-on-near-black.
- **Blade Runner** for the *materiality*: phosphor warmth, a sense that the machine is old and has been repaired, amber warnings that feel electrical rather than decorative.
- **Cyberpunk** broadly for the *typographic voice*: monospace as the default register, terse machine-authored labels, and a deliberate absence of marketing polish.

What it deliberately is **not**: no scanline overlays, no CRT curvature, no glitch effects, no typewriter reveal on text the player needs to read. Every one of those is a legibility tax paid for atmosphere, and C5 outranks atmosphere. Atmosphere comes from palette, type, spacing, borders, and copy — all of which are free.

Two variants ship in this family:

- **`sickle-os`** — the default. Near-black surfaces, phosphor-cyan compute, amber ethecoin, violet noise. Palette in `01-visual-language.md` §2.3.
- **`sickle-os-hc`** — a high-contrast variant that raises every foreground to a wider margin over the 4.5:1 floor and replaces subtle borders with strong ones. It exists so that choosing the story family never costs a player legibility.

> **[PROPOSAL] The diegetic Eye skin.** Recovered documents from Eye infrastructure — memos, citizen-scoring records, propaganda payloads (`../design/14-world-and-narrative.md` §3) — render their *content pane* in The Eye's own institutional chrome, regardless of the player's theme. Cold blue-grey, a serif-ish institutional face, bureaucratic spacing. This is environmental storytelling for free: the scariest recovered document is a routine memo treating the player as a line item (`../design/14-world-and-narrative.md` §6), and it lands harder when the memo looks like it came from the office that wrote it. **Scope limit, non-negotiable:** it skins *recovered content* only. It never touches the player's own controls, meters, or chrome, and it obeys the same contrast floor. Tracked as **CL-3**.

### 3.4 What is shared, and what is not

> **The rule:** a theme is a **skin**. It changes how things look. It never changes what exists, where it is, what it is called, what it means, or whether you can read it.

| Shared across both families — a theme may not change it | Owned by the theme — a theme may change it |
|---|---|
| Information architecture: which windows exist, what each contains | Every colour value behind a semantic token |
| Layout, grid, spacing scale, density behaviour | Typeface selection (OS face vs. bundled face) |
| The token *contract* — names, meanings, usage rules | Border weight and radius within the specified range |
| Every interaction: shortcuts, focus order, gestures, command vocabulary | Elevation expression (shadow vs. border vs. surface step) |
| All copy, labels, units, number formatting | Texture and surface treatment within the legibility floor |
| Which data is shown, and at what precision | Icon stroke weight within the specified range |
| Every accessible name, role and description | — |
| The accessibility floor (§3.5) | — |

The practical test: **a screenshot of the same window in both themes must contain the same words and the same numbers in the same places.** If it does not, one of the two themes has a bug.

### 3.5 The floor both families must clear

Non-negotiable, and identical for both. Full specifications in `01-visual-language.md`; stated here because it is a product rule, not a styling detail.

1. **Contrast.** Text and meaningful glyphs meet **4.5:1** against every surface they can sit on (WCAG 2.2 SC 1.4.3, Level AA; large text ≥18pt / ≥14pt bold may use 3:1 but this client does not rely on that exemption for any value the player reads under pressure). Control boundaries, meter fills, focus indicators and state marks meet **3:1** (SC 1.4.11). Every value in `01-visual-language.md` §2.3 is published with its measured ratio.
2. **Colour is never the only carrier.** Every colour-coded state also carries a text label, a glyph, or a shape (SC 1.4.1). The enumeration is in `01-visual-language.md` §2.4.
3. **Identical information.** Neither theme may hide, defer, abbreviate, or de-emphasise a value the other shows. Atmosphere is never a reason to withhold data.
4. **Keyboard-complete.** Every action is reachable without a pointer, in both families, with a visible focus indicator.
5. **Accessible names.** Every control and every meaningful graphic has an accessible name via JavaFX's `accessibleText` / `accessibleRole` / `accessibleHelp` (verified on `javafx.scene.Node`), in both families.
6. **OS preferences are honoured in both.** Reduced motion and reduced transparency apply to the story theme exactly as they apply to the native one. The story theme does not get to be exciting at an accessibility cost.

---

## 4. Theme selection UX

### 4.1 First run

**Default: `native`, matching the OS colour scheme.** A new player has not opted into anything yet, and the first-run job is "this is a competent program that respects my machine," not "here is our art direction." The story theme is offered explicitly and early — a one-screen chooser during first-run setup showing both, side by side, on a real rig-monitor screenshot rather than a swatch — but the default if the player skips it is native.

This deliberately reverses the current scaffold, which hardcodes `PrimerDark` with the comment *"Dark by default; this is a game about being watched."* That instinct is right about the story theme and wrong as a global default: on a machine set to light mode, a forced dark app reads as a program that ignores its user. The instinct is preserved where it belongs — **`sickle-os` has no light variant**, because a surveillance-dystopia operator console in light mode is a different game.

### 4.2 Where the switch lives

Three routes, all reaching the same setting:

1. **Settings → Appearance**, the canonical place, with live preview.
2. **`Shortcut+Shift+T`** opens the theme switcher directly from any window (`Shortcut` = ⌘ on macOS, Ctrl elsewhere; see §6.3).
3. **The command palette** (`Shortcut+K`), via `theme` — because the client's vocabulary is Unix and `theme --list` / `theme sickle-os` should work (`04` will specify the command grammar).

The switcher shows: family, variant, the resolved OS state it is following (`following system: dark`), and a live-updating preview strip containing a compute gauge, a trace gauge, a ledger row and a log line — the four primitives most sensitive to palette. Preview strips beat swatches because the thing being chosen is legibility, not colour.

### 4.3 Live switching, no restart

Theme changes apply immediately to every open `Stage`, including mid-breach. The mechanism is Established by the toolkit: `Application.setUserAgentStylesheet(...)` sets the stylesheet for the whole application, so every `Stage` inherits it without per-window wiring (the scaffold already relies on this). Rebuilding scenes, or requiring a restart, would mean the setting is unreachable exactly when a player discovers they cannot read something.

Two constraints on the switch itself:

- **The switch never animates.** Cross-fading an entire application while a trace timer runs is a legibility failure with a stopwatch attached. It is an instant swap.
- **The switch never moves anything.** Because layout, spacing and density are shared (§3.4), a theme change must not reflow a single control. If switching themes reflows the rig monitor, a token has leaked into a stylesheet.

### 4.4 Per-window theme override: **no**

Considered and rejected. It sounds appealing — the story theme for the terminal, native for the ledger — and it fails on three counts:

1. **It breaks colour semantics.** The player learns "teal means compute" once. If teal means compute in one window and something else in the next, every meter becomes a lookup instead of a glance, which is C5 in reverse.
2. **It breaks cross-window reference under pressure.** During a breach the player is comparing the trace meter in one window against noise in another (`../design/05-hacking-minigame.md` §5). Two palettes make that comparison slower at exactly the wrong moment.
3. **It multiplies the contrast surface.** Every token would need verifying against every theme in every window combination, and the floor in §3.5 is only credible if it is verifiable.

What *is* allowed, because it does not carry semantics: **per-window density** (comfortable/compact, `01-visual-language.md` §4.4). A player may want the rig monitor compact and the recon reader comfortable. Density changes spacing, never meaning.

The one exception, and it is not a user setting: the diegetic Eye skin on recovered Eye content (§3.3), which is authored, scoped to content panes, and part of the fiction rather than a preference.

### 4.5 What persists

Theme family, variant, per-window density, teaching-layer level, and window geometry persist locally per profile — they are client-owned state (§1.1) and never round-trip to the server. `ToolWindow.id()` already exists in the scaffold for exactly this purpose. If the settings file is missing or unreadable, the client falls back to §4.1 defaults silently; a corrupt preferences file must never block sign-in.

---

## 5. The educational layer

**Stated product goal:** an average player should finish a session genuinely understanding a little more about how operating systems, networking and computation work. Detail lives in `04` (the teaching/tooltip system); this is the one-page frame the rest of the doc set builds on.

### 5.1 Why it works here rather than being bolted on

The game already speaks Unix, and it already requires real data. `../design/04-mining.md` §3.1 makes process/connection/storage views a hard implementation requirement — "the discrepancy is always present in the data" — and calls manual investigation "the game's second-strongest tutorial vector." `../design/04-mining.md` §5.1 calls cracking the strongest one. The teaching layer is therefore not new content; it is **labels on content the design already committed to being real**.

Concretely, the vocabulary the player meets is mostly load-bearing real-world vocabulary: ports and services (Enumeration), credential salting and rainbow tables (Credential), fuzzing malformed input (Logic), race conditions and timing windows (Timing), graph traversal and hop distance (Traversal), onion routing (Relay Chain), timing and power side channels (Side-Channel Reader), rootkits and audit-vs-scan detection, decentralized identifiers, JSON canonicalization and signature verification (provenance).

### 5.2 Three levels of disclosure

The teaching layer is a **level**, not a boolean, so that it can decay gracefully as a player learns instead of being a switch they flip off once and never revisit.

| Level | Who | Behaviour |
|---|---|---|
| `explain` | New players — **the default on a fresh profile** | Every term carries a marker; hovering or focusing opens a short definition, and the first appearance of a term in a session surfaces the definition inline once |
| `terms` | Most players after a few hours | Markers on terms not yet seen in this profile; already-seen terms stay quiet but remain queryable |
| `off` | Experienced players | No markers; definitions still reachable on demand via the term index and `man <term>` in the command palette |

**Definitions are never destroyed, only quieted.** At every level, `man <term>` resolves, and the term index is a searchable window. The failure mode of an opt-in-only help system is that the player who most needs it never finds it; the failure mode of an always-on one is noise. Levels solve both, and default-on solves the first properly.

### 5.3 The honesty rule

Every entry declares its own status, and the marker style differs so the player can see it at a glance:

| Status | Meaning | Example |
|---|---|---|
| `real` | A genuine concept, described accurately, with the game's simplifications named | *port*, *salt*, *race condition*, *onion routing*, *DID*, *SHA-256* |
| `real, simplified` | Real, but the game's model is a deliberate abstraction | *rainbow table* (the game's is instant; a real one is a space-time tradeoff over precomputed chains) |
| `game` | A construct of this fiction with no real-world referent | *ethecoin*, *noise*, *heat*, *schematic gate*, *the Sickle* |

A teaching system that quietly presents `noise` as a networking concept would actively miseducate, which is worse than teaching nothing. The status marker is what makes the educational claim defensible.

### 5.4 What the layer must never do

- **Never gate progress.** No "read this tooltip to continue."
- **Never interrupt.** Definitions open on hover, focus, or request — never on their own, never during a live breach with a running timer.
- **Never move layout.** Definitions render in a popover over the surface, never in a panel that reflows the window (C5).
- **Never replace the label.** The marker decorates a term that is already fully readable on its own.

---

## 6. Information architecture

### 6.1 The tool-window catalogue

Every window is an independent `Stage` with a stable id (used for persistence and for the docked fallback's panel identity). The **Unix analogue** column is the vocabulary anchor for C6 and for the command grammar in `04` — it is how the window is *thought about*, not necessarily its literal title.

| Window id | Title | Unix analogue | What it holds | Source |
|---|---|---|---|---|
| `rig-monitor` | Rig Monitor | `top` | Compute ledger: total, allocated by consumer, available, recovering with time-to-recover; Thermal Budget state | `../design/01-core-resources.md` §1.4 **(Established: dedicated, always-on-top)** |
| `audit` | Audit | `ps` / `netstat` / `df` | Real process, connection and storage tables for **manual investigation** — the discrepancy is always present in this data | `../design/04-mining.md` §3.1 **(Established: must be real, consistent data)** |
| `map` | Network Map | `traceroute` | The node graph, hop distance, recon overlays, Traversal-class routing | `../design/05-hacking-minigame.md` §5, `../design/07-recon-tools.md` |
| `terminal` | Terminal | a shell session | The active breach layer, probe entry, live event stream | `../design/05-hacking-minigame.md` §5 |
| `recon` | Recon | `less` over recovered files | Flavour logs, emails, database records — the human-read material breach steps depend on | `../design/05-hacking-minigame.md` §3.2, `../design/14-world-and-narrative.md` §3 |
| `mining` | Mining | a miner dashboard | Self-mining allocation and block progress; the deployed network, tiers, buffers, control channels | `../design/04-mining.md` §1.3, §2 |
| `storage` | Storage | `ls` across three mounts | Vault / Standard / High-Hackable, by exposure; item provenance state | `../design/01-core-resources.md` §6 |
| `ledger` | Ledger | a transaction log | EC balance, the public ledger, transfers, Dead Drop | `../design/01-core-resources.md` §2.2 |
| `market` | Market | a package manager | Gated offerings with the blocking gate surfaced per item | `../design/02-unlock-gates.md` |
| `botnet` | Botnet | `jobs` / `systemctl` | Frames, instances, socketed tools, backlog timers, split-attention state | `../design/10-botnets.md` |
| `defense` | Defense | a firewall/IDS console | Armed defenses and their permanent compute draw; canary trips; honeypot state | `../design/09-defense-and-hardening.md` |
| `identity` | Identity | `whoami` / `id` | Handle, DID, personal and server heat bands, faction reputation, burner handles | `../design/12-identity-and-social.md`, `../architecture/02-identity-and-auth.md` |
| `switcher` | Windows | `jobs` | The window list: what is open, what is alerting, one keystroke to raise | — |

Three notes worth carrying forward:

- **`rig-monitor` and `audit` are the two windows whose content the design docs specify directly.** Everything else is this proposal's arrangement and is safe to rearrange; those two are not.
- **`storage` is organised by exposure, not by category.** The three tiers *are* a risk gradient (`../design/01-core-resources.md` §6), and sorting by item type would bury the only property that matters.
- **`market` surfaces the blocking gate per item, never a generic "locked."** Five gates (`../design/02-unlock-gates.md`) is already flagged as possible cognitive overload (OQ-2); the UI's job is to make *which* gate legible so the answer to "why can't I buy this" is always on screen. This is also where C4 bites: the gate verdict is rendered as received, never computed locally.

### 6.2 Moving between windows

- **The switcher** (`switcher`) is a persistent, compact window listing every tool: open/closed, alerting, and its current headline value. It is the multi-window analogue of a taskbar the player controls, and it is what makes twelve windows navigable rather than lost behind each other.
- **Cross-window links are first-class.** A ledger row referencing a counterparty opens `identity` focused on that handle; a canary trip in `defense` opens `recon` on the captured evidence; a discovered miner in `audit` opens `mining` on that host. Under C5, a player should never have to *find* the window that explains what they are looking at.
- **Raising is never stealing.** A cross-window link raises and focuses the target window. A background *event* never does — it marks the switcher entry and the window's own indicator, and waits.

### 6.3 Keyboard

All shortcuts use JavaFX's `KeyCombination.SHORTCUT_DOWN`, which resolves to ⌘ on macOS and Ctrl on Windows/Linux — written `Shortcut` below. This is the one mechanism that gives correct platform behaviour from one codebase.

| Binding | Action |
|---|---|
| `Shortcut+0` | Raise the rig monitor (always bound, never remappable) |
| `Shortcut+1` … `Shortcut+9` | Raise tool window *n* from the switcher order |
| `Shortcut+\`` | Cycle open tool windows |
| `Shortcut+K` | Command palette |
| `Shortcut+F` | Find within the focused window |
| `Shortcut+Shift+D` | Toggle multi-window ↔ docked layout |
| `Shortcut+Shift+T` | Theme switcher |
| `Shortcut+Shift+E` | Cycle teaching level (`explain` → `terms` → `off`) |
| `Shortcut+Shift+C` | Toggle density (comfortable ↔ compact) for the focused window |
| `Shortcut+.` | Abort the current breach — **always confirms**, because `aborted` is a persisted outcome with real consequences (`../design/05-hacking-minigame.md` §4) |
| `Alt`/`Option` held | Reveal attribution overlays on every visible meter (C3) |

`Shortcut+0` being unremappable is a C2 decision: the most important number in the game must always be one keystroke away, and a player must not be able to lose that by accident.

### 6.4 The docked fallback

**Established requirement**, from two independent sources (`../architecture/01-tech-stack.md` §1, `../design/05-hacking-minigame.md` §5): a single-window / docked layout must exist because window management under time pressure is a real barrier.

Design position for this doc set, expanded in `05`:

- It is a **layout mode**, not a second UI. The same tool views render into docked panels instead of `Stage`s. If a tool exists in one mode and not the other, the mode split has been implemented wrong.
- It is **switchable live** (`Shortcut+Shift+D`), including mid-breach, for the same reason the theme switch is: a setting that becomes unreachable when you need it is not a setting.
- The scaffold's own guidance stands: whoever builds the *second* tool window builds the docked fallback in the same change, while it is still cheap.

---

## 7. Non-goals

Stated plainly so they do not get relitigated one plausible feature at a time.

- **Not a 3D scene, and not a rendered 2D game world.** The toolkit choice is a windowing toolkit precisely because the subject matter is UI (`../architecture/01-tech-stack.md` §1). No camera, no scene graph beyond JavaFX's, no shader pipeline.
- **Not photoreal chrome.** No CRT curvature, scanlines, chromatic aberration, glitch effects, film grain, or animated typewriter reveals on readable text. Every one of these costs legibility to buy atmosphere, and C5 outranks atmosphere. The story theme gets its character from palette, type, spacing and copy.
- **Not a real shell.** The client's Unix syntax is a *vocabulary and interaction idiom*, not an execution surface. The command palette dispatches to a fixed, enumerated set of game commands. There is no arbitrary command execution, no subprocess spawning, no filesystem access outside the client's own profile directory, and no scripting hook that could be turned into one. This is a security boundary, not a scope decision, and it does not move.
- **Not a Unity/Godot/Unreal game.** Already decided (`../architecture/01-tech-stack.md` §1) and restated because the multi-window fantasy is a native capability of the chosen toolkit and a fight against every engine.
- **Not a storefront.** No real-money surfaces of any kind: no currency packs, no cosmetics shop, no battle pass, no "buy compute." I1 and I2 make several of these outright unimplementable, and the rest are not being designed around. The in-game `market` window trades ethecoin only.
- **Not an authority.** No client-side gate evaluation, no client-side loot rolls, no client-side balance arithmetic, no optimistic outcome display (C4, I14). The `client-is-not-authoritative` enforcer rule in `client/pom.xml` is the mechanical half of this; C4 is the design half.
- **Not a telemetry client.** Nothing is collected beyond what the game protocol requires. Playtest instrumentation (`../design/03-economy.md` §6) is server-side, over data the server already owns.
- **Not skinnable by players.** Two families, authored and verified against the §3.5 floor. User CSS injection would make the contrast floor unenforceable and would give the *appearance* of client authority over presentation of server truth. Revisit only with a signed-theme model — see **CL-5**.

---

## 8. Open questions

Deliberately undecided here. Log in `../design/15-open-questions.md` §2 if this doc set is adopted.

- **CL-1: Does the native family need a fourth auto-selected pairing for Linux desktop environments?** Currently all Linux sessions get Primer. GTK/Adwaita and KDE/Breeze are visibly different design languages, and "native on Linux" is not one target. Resolvable by reading the desktop environment at startup, but that is platform-detection code with a long tail. Watch for: Linux players reporting the app looks foreign on KDE.
- **CL-2: How much of the game-semantic palette should the story theme be allowed to move?** §3.4 says a theme owns colour values, but if `sickle-os` and `native` disagree too far on what "noise" looks like, a player switching themes has to relearn. Candidate rule: hue **family** is fixed by the contract, and only lightness/chroma may vary per theme. Needs a second theme to exist before it can be judged.
- **CL-3: Is the diegetic Eye skin (§3.3) worth its complexity?** It is a genuinely strong narrative device and a second contrast surface to verify. Decide before the recovered-document reader is built, not after.
- **CL-4: Does the teaching layer's default level (`explain`) survive contact with players who already know Unix?** Default-on serves the stated product goal and risks annoying the audience most likely to evangelise the game. Possible mitigation: a first-run question ("how familiar are you with the command line?") that sets the initial level. Watch for: experienced players turning it off in the first ten minutes, which is fine, versus bouncing off it, which is not.
- **CL-5: Community theming.** §7 rules out player CSS for now. A signed/curated theme model that still enforces the §3.5 floor is plausible and would be well-received. Not v1.
- **CL-6: Is there a meaningful client behaviour for `reducedData`?** JavaFX 24 exposes it; this client's network cost is small. Possibly: suppress non-essential background polling and defer provenance chain fetches until an item is opened. Low urgency.
- **CL-7: Where does audio sit?** This doc set covers visual and interaction design only. A surveillance-dystopia client with no sound design is leaving Pillar 4's escalation on the table, and an alert sound is one of the few ways to serve C5's triage problem without stealing focus. Needs its own doc and its own accessibility pass; deliberately not decided here.
- **CL-8: Multi-monitor and DPI-mixed layouts.** The "second monitor" case is named explicitly in `../design/00-vision-and-pillars.md` §6, and JavaFX's behaviour when a `Stage` is dragged between screens of different scale factors needs verifying on all three platforms before window geometry persistence is trusted. Test, then decide.
