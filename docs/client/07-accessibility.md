# 07 — Accessibility

**Status:** ⚠️ **[PROPOSAL]** — but a proposal answering an *Established obligation*. `../architecture/01-tech-stack.md` §1 and `../design/05-hacking-minigame.md` §5 both flag this client for `design:accessibility-review` and both state, independently, that a **single-window / docked fallback layout must exist**. That requirement is Established and is cited, not invented. Everything else here — the conformance target, the pace scalar, the announcement model, the checklist — is first-pass design filling a gap the source material never covered. Platform and toolkit claims were verified against live sources in this pass; anything unverified is marked **⚠ unverified** inline.
**Depends on:** `00-client-overview.md`, `01-visual-language.md`, `02-platform-native-themes.md`, `05-tool-windows-and-layout.md`, `06-resource-and-inventory-ui.md`, `../design/00-vision-and-pillars.md`, `../design/01-core-resources.md`, `../design/05-hacking-minigame.md`, `../design/10-botnets.md`, `../architecture/01-tech-stack.md` §1
**Depended on by:** the `client/` module implementation; the release checklist. **§10 is the deliverable that lets the two `design:accessibility-review` flags be closed.**

> **Two housekeeping notes for the integrator.**
> 1. `03-story-theme.md`, `04-terminology-and-education.md`, `05-tool-windows-and-layout.md` and `06-resource-and-inventory-ui.md` landed during this pass. Where they specify a mechanism, this document **cites and audits** it rather than restating or competing with it — `03` §9 owns the story theme's own guardrails, `05` §5 the docked mode's mechanics, `05` §6 the attention ladder, `04` §5 the teaching layer's accessibility. §2, §4.5 and §6 here state the requirements those must satisfy and record where they already do. They are cited by filename on first mention, then as bare `03`…`06`; the game-design minigame doc is always written in full as `../design/05-hacking-minigame.md`, never as `05`.
> 2. `02-platform-native-themes.md` §8 (**PN-12**) refers to the onboarding/first-run doc as `07`. This document occupies `07`. One of the two needs renumbering.

---

## 1. The standard, and how much of it honestly applies

### 1.1 The target

> **The Eye and Sickle client targets WCAG 2.2 Level AA as its reference frame, translated for a desktop application, plus four Level AAA criteria adopted deliberately, plus three game-specific rules WCAG has no criterion for.**

WCAG is a *web content* standard. This is a JavaFX desktop application with no user agent, no viewport, no zoom control we do not write ourselves, and no document. Adopting it wholesale and claiming conformance would be dishonest; adopting nothing because it does not fit exactly is the more common and much worse failure. So the position is: **WCAG is the reference frame because it is the only accessibility standard with numbers in it**, and every criterion below is marked as applying as written, applying in translation, or not applying — with the reason.

Why a reference frame with numbers matters more than a policy: `00-client-overview.md` §3.5 already commits the client to a contrast floor, and `01-visual-language.md` §2.3 already publishes measured ratios and puts the check in the build. That only works because 4.5:1 and 3:1 are numbers someone else fought over. The same is true of the flash threshold, the target size, and the focus indicator. Adjectives cannot fail a build.

**Four AAA criteria adopted anyway**, each because this client has a specific reason:

| AAA criterion | Why we take it |
|---|---|
| **2.4.13 Focus Appearance** | The focus indicator is the only thing telling a keyboard player where a keystroke will land during a live breach. `01-visual-language.md` §1.5 already specifies a 2px ring outside the control's bounds, which is exactly what this criterion asks for — we are one measurement away from meeting it, so we meet it. |
| **1.4.6 Contrast (Enhanced), 7:1** | Only inside the high-contrast treatment (§5.5). `00-client-overview.md` §3.3 and `02-platform-native-themes.md` §2.8 already describe "raise every foreground toward 7:1"; naming the criterion makes it measurable. |
| **2.3.2 Three Flashes** | The AA criterion (2.3.1) lets content pass on an *area* exemption. We decline to rely on it: the client's blinking elements are enumerated, each is compliant on **rate** as well as area, and nothing red or full-screen blinks at any rate (§6.4, `03-story-theme.md` §9.4). |
| **2.2.3 No Timing** | Adopted **partially and honestly**: not for the trace timer, which is essential (§8.2), but for everything else — no notice auto-dismisses, no dialog expires, no session times out under the player. |

**Three game-specific rules WCAG does not cover**, each specified in this document:

1. **Pace scaling** (§8.3) — WCAG's Timing Adjustable has an essential-activity exception that a game can hide behind forever. We take the exception and then do the work anyway.
2. **One-action abort** (§8.6) — the escape hatch must not itself cost the thing you are escaping.
3. **The greyscale test** (§5.2) — `01-visual-language.md` §2.4's acceptance criterion, promoted here into a checklist item with a defined method.

### 1.2 Which criteria apply, and which do not

Level A and AA, by principle. Verdicts: **applies** (as written, meaningfully), **translated** (the intent applies; the mechanism differs), **n/a** (with the reason — an n/a with no reason is an excuse).

#### Perceivable

| SC | Level | Verdict | Notes |
|---|---|---|---|
| 1.1.1 Non-text Content | A | applies | Every icon has an accessible name (`01-visual-language.md` §6.4). Decorative icons are excluded from traversal and carry no name — that is the criterion's own "pure decoration" clause, not a loophole. |
| 1.2.1–1.2.5 Time-based media | A/AA | n/a **today** | The client ships no audio or video. Pillar 5 delivers story as recovered text (`../design/00-vision-and-pillars.md` §3). Becomes live the moment `00-client-overview.md` **CL-7** resolves — §9 is the forward contract. |
| 1.3.1 Info and Relationships | A | translated | Expressed as `AccessibleRole` + `AccessibleAttribute`, not markup (§4.4). |
| 1.3.2 Meaningful Sequence | A | translated | Focus traversal order, declared per window (§3.4). |
| 1.3.3 Sensory Characteristics | A | applies | No copy may say "the red meter" or "the panel on the right". Say `trace`, say the window name. This binds `01-visual-language.md` §9's microcopy rules. |
| 1.3.4 Orientation | AA | n/a | Desktop windows have no orientation lock. |
| 1.3.5 Identify Input Purpose | AA | n/a | JavaFX exposes no autocomplete-token mechanism, and the client collects no personal data (`02-platform-native-themes.md` §2.9). |
| 1.4.1 Use of Color | A | applies | Already mechanised as a table in `01-visual-language.md` §2.4; §5.2 makes it testable. |
| 1.4.2 Audio Control | A | n/a today | §9.3. |
| **1.4.3 Contrast (Minimum)** | AA | applies | **4.5:1** normal text, **3:1** for large text — *large* being **≥18pt, or ≥14pt bold** (verified against WCAG 2.2). **We do not use the large-text exemption for any value a player reads under pressure** (`00-client-overview.md` §3.5). |
| 1.4.4 Resize Text | AA | translated | No browser zoom; we ship the scale control ourselves, to **200%** (§7.2). |
| 1.4.5 Images of Text | AA | applies | Icons are `SVGPath` constants (`01-visual-language.md` §6.2); no bitmap in the client contains a glyph (§7.4). |
| 1.4.10 Reflow | AA | translated | No 320 CSS px viewport. The analogue: at minimum window size and 200% text scale, no readout requires scrolling on two axes (§7.5). |
| **1.4.11 Non-text Contrast** | AA | applies | **3:1** against adjacent colours for UI components and graphical objects (verified). This is the criterion that catches the gauge-segment finding in §5.3 — *adjacent* is doing real work there. |
| 1.4.12 Text Spacing | AA | translated, partially | The criterion assumes a user can override spacing via a stylesheet. JavaFX exposes no such hook and **no letter-spacing or word-spacing property at all** (verified against the JavaFX 26 CSS Reference: only `-fx-font`, `-fx-font-family`, `-fx-font-size`, `-fx-font-style`, `-fx-font-weight`, `-fx-line-spacing`, `-fx-tab-size`). We ship the line-spacing half as a setting and cannot ship the letter/word half — see §7.3 and **AX-4**. |
| **1.4.13 Content on Hover or Focus** | AA | applies, hard | The teaching layer (`es-term`) and the attribution overlays (pillar C3) are *both* hover-or-focus content. This is the AA criterion most load-bearing for this specific client. §3.6. |

#### Operable

| SC | Level | Verdict | Notes |
|---|---|---|---|
| **2.1.1 Keyboard** | A | applies | §3. The whole game, pointer unplugged. |
| **2.1.2 No Keyboard Trap** | A | applies | The terminal buffer is the realistic trap (§3.5). |
| 2.1.4 Character Key Shortcuts | A | applies | No unmodified single-character *global* binding exists. Two window-scoped instances rely on the criterion's focus clause and are correct: single characters in the `terminal` are input, and `05-tool-windows-and-layout.md` §2.5's plain `+` / `−` map zoom is explicitly *"suppressed while a text field has focus"*. Both must also be remappable (§3.9). |
| 2.2.1 Timing Adjustable | A | **essential exception claimed, then exceeded** | §8.2–§8.3. The exception is real; taking it and stopping there is the thing this document refuses to do. |
| 2.2.2 Pause, Stop, Hide | A | applies | The only repeating animation in the client is the switcher's alert pulse, and reduce-effects stops it (§6.5). |
| **2.3.1 Three Flashes or Below Threshold** | A | applies, exceeded | §6.4 bans flashing outright (i.e. 2.3.2 AAA). |
| 2.4.1 Bypass Blocks | A | translated | No repeated navigation blocks exist in multi-window mode — each `Stage` *is* the content. In docked mode, `F6` region cycling is the mechanism. |
| 2.4.2 Page Titled | A | translated | Every `Stage` sets a descriptive title from `00-client-overview.md` §6.1; the title is the window's accessible name. |
| 2.4.3 Focus Order | A | applies | §3.4. |
| 2.4.4 Link Purpose (In Context) | A | translated | Cross-window links (`00-client-overview.md` §6.2) name their destination and subject: `open identity for did:plc:…`, never `open`. |
| 2.4.5 Multiple Ways | AA | translated | Every tool is reachable three ways: the `switcher`, a `Shortcut+n` binding, and the command palette. |
| 2.4.6 Headings and Labels | AA | applies | Every panel heading and every control label describes its content. |
| **2.4.7 Focus Visible** | AA | applies | §3.3. |
| **2.4.11 Focus Not Obscured (Minimum)** | AA | applies | A popover, a `ModalPane` scrim, or a docked pane divider may never fully cover the focused control. §3.3. |
| 2.5.1 Pointer Gestures | A | applies | No multipoint or path-based gesture exists or may be added. |
| 2.5.2 Pointer Cancellation | A | applies | Every consequential action fires on **release**, over the control, and is abandoned if the pointer leaves before release. This matters most for `abort`. |
| 2.5.3 Label in Name | A | applies | The accessible name of a labelled control starts with its visible label, verbatim. |
| 2.5.4 Motion Actuation | A | n/a | No device motion input. |
| **2.5.7 Dragging Movements** | AA | applies | Every drag has a non-drag equivalent (§3.7). Map pan, socket-into-bot, and split resize are the three real cases. |
| **2.5.8 Target Size (Minimum)** | AA | applies, exceeded | **24 × 24 CSS px**, with the spacing exception (a 24px-diameter circle centred on each undersized target must not intersect another target's circle) — verified. `01-visual-language.md` §4.3 already floors standard controls at 32/28 and time-critical controls at 40/36-never-below-32. §3.8 closes the chip gap. |

#### Understandable and Robust

| SC | Level | Verdict | Notes |
|---|---|---|---|
| 3.1.1 Language of Page | A | translated | The JVM default `Locale` drives number and time formatting (`02-platform-native-themes.md` §2.9); string locale follows once localisation exists (`01-visual-language.md` **V-10**). |
| 3.2.1 On Focus | A | applies | Focus never triggers an action or raises a window. A `es-term` popover opening on focus is content-on-hover-or-focus (1.4.13), not a change of context — it steals nothing and moves nothing (`01-visual-language.md` §8.10). |
| 3.2.2 On Input | A | applies | No setting applies on selection change without an explicit commit, except theme/density/text-scale, which are live-preview settings whose whole purpose is immediate effect and which move no focus. |
| 3.2.3 / 3.2.4 Consistent Navigation / Identification | AA | translated | Cross-window rather than cross-page: the same primitive means the same thing in every window of the catalogue (`00-client-overview.md` §6.1, extended in `05-tool-windows-and-layout.md` §2.2), which is exactly why `00-client-overview.md` §4.4 rejects per-window themes. |
| 3.2.6 Consistent Help | A | applies | The term index and `man <term>` are reachable identically from every window (`00-client-overview.md` §5.2), plus `F1` on Windows (`02-platform-native-themes.md` §4.4). |
| 3.3.1 Error Identification | A | applies | `01-visual-language.md` §9.4's three-part shape. |
| 3.3.2 Labels or Instructions | A | applies | — |
| 3.3.3 Error Suggestion | AA | applies | The third sentence of `01-visual-language.md` §9.4 — *what you can do* — is that criterion. |
| 3.3.4 Error Prevention (Legal, Financial, Data) | AA | translated | The in-game analogue is irreversible loss: abort, kill a miner, Ghost Protocol, faction forfeiture. All confirm, in-window (`01-visual-language.md` §5.3), stating the consequence. |
| 3.3.7 Redundant Entry | A | applies | Sign-in never asks twice for the same value in one flow. |
| **3.3.8 Accessible Authentication (Minimum)** | AA | applies, **partially outside our control** | Sign-in is AT Proto OAuth (`../architecture/02-identity-and-auth.md`) and runs in the provider's flow. Our obligations: never add a cognitive-function test, never disable paste into any credential field, never impose our own CAPTCHA. What the provider does is **AX-11**. |
| 4.1.2 Name, Role, Value | A | applies | §4 in full. |
| **4.1.3 Status Messages** | AA | **cannot be met with confidence** | JavaFX has no live-region concept — verified: `AccessibleAttribute` has 61 constants and none is an alert or live region. §4.5 gives the mitigation and **AX-3**. This is the largest honest gap in this document and it is stated here rather than buried. |

### 1.3 What "conformance" means for us

We do not claim WCAG conformance, and should not, because 4.1.3 cannot be met with confidence on a toolkit that has no mechanism for it. What we claim is the checklist in §10: a specific, dated, per-platform statement of what was tested and what passed. That is more useful to a player deciding whether the game is playable for them than a badge would be, and it is the only claim that stays honest as the toolkit moves under us. Who publishes it and in what form is **AX-12**.

---

## 2. The multi-window barrier

### 2.1 The flag being closed

Two source documents raise this independently, which is why it is treated as an obligation rather than a suggestion:

> "Window management needs an accessibility fallback: a single-window / docked layout for players who can't manage many OS windows under time pressure … Multi-window is the default and the fantasy; it must not be the *only* option." — `../architecture/01-tech-stack.md` §1

> "This is a genuine design opportunity … but it is also an accessibility risk (window management under time pressure). Flag for `design:accessibility-review` before committing: single-window fallback layout must exist." — `../design/05-hacking-minigame.md` §5

The fantasy is Established and this document does not touch it. `../design/00-vision-and-pillars.md` §6 wants the player's screen to look like an operator's desk, and one `Stage` per tool across the whole catalogue (`00-client-overview.md` §6.1; fifteen in `05-tool-windows-and-layout.md` §2.2) is how that happens. The question is only whether a player who cannot work that way is excluded from the game, and the answer has to be no.

`05-tool-windows-and-layout.md` §5 designs the docked mode and reaches the same conclusion from the layout side — *"the docked mode has to be good, not merely present."* This section does not re-design it. It states **who is excluded and by what mechanism** (§2.2), the **accessibility requirements** the docked mode must satisfy (§2.3), and **how a player finds it** (§2.4) — the last of which no other document owns.

### 2.2 Who it excludes, and how

Naming the mechanism per group matters, because the mitigations differ.

| Group | The specific failure |
|---|---|
| **Motor / limited dexterity** | Window furniture — title bars, resize handles, close buttons — is drawn and sized by the OS. We cannot enlarge it, cannot restyle it, and cannot exempt it from `01-visual-language.md` §4.3's hit-target floors. Raising, moving and resizing a dozen-plus windows is a sustained precision-pointing task, and `../design/05-hacking-minigame.md` §5 puts it under a running trace timer. A puzzle game should not be gated on dexterity; `../design/00-vision-and-pillars.md` §7 says so directly. |
| **Screen-magnifier users** | The decisive case. At 400% magnification a 1920×1080 display shows roughly a 480×270 logical region. `../design/05-hacking-minigame.md` §5's live breach spans four surfaces — map, terminal, rig monitor, recon. **Those four can never be on screen simultaneously.** The player must pan between them, and `00-client-overview.md` §4.4 (point 2) already identifies cross-window comparison under pressure as a first-order need. Multi-window does not merely inconvenience this player; it makes a designed comparison impossible. |
| **Cognitive / attention / executive function** | Thirteen windows in arbitrary positions is spatial working memory the game did not intend to tax. Occlusion turns "read the trace meter" into "find the window that has the trace meter, then read it". `00-client-overview.md` C5 says the player's task under pressure is triage; a search step in front of triage is a second, uncompensated task. |
| **Screen-reader users** | Each `Stage` is a separate accessibility tree root. Moving between windows resets reading context, and no screen reader announces "the trace meter in another window just entered the imminent band". §4 addresses this, but fewer roots is strictly better. |
| **Single small display** | Not a disability, same failure. A 13" laptop at default scaling cannot hold four useful windows. |
| **Tiling window managers** | A tiler will lay every window out by its own rules, immediately, and the player has no say. `02-platform-native-themes.md` **PN-11** already flags detection; §2.5 here says what to do about it. |

> ⚠️ **AMENDED 2026-07-26 — the obligation is met by the deck, not by a second layout.**
> `../design/ui-design-language.md` §0 replaced both the multi-window desk and the docked tabbed shell
> with a single undecorated Stage containing a window manager the client draws itself. The Established
> obligation this section answers — *"a single-window / docked layout must exist for players who can't
> manage many OS windows under time pressure"* — is now met by default rather than by an alternative
> mode, because there is only one layout and it is a single window.
>
> Everything §2.3 requires of the docked mode still applies and is easier to hold: the compute readout
> is chrome in the top strip (no z-order, no tab), every tool has a rail launcher entry carrying its own
> accelerator, `F6` region cycling still applies across strip / rail / desk / command strip, and the
> "no functionality or information lost" contract is now structural rather than a promise between two
> implementations. **2.4.1 Bypass Blocks** changes reading: the multi-window row is void, and the docked
> row is the only one — regions are cycled, not navigated past.

### 2.3 The docked mode is a mode, not a fallback — the accessibility contract

> ✅ **Strengthened 2026-07-25: it is now the default.** This section argued that the docked layout
> must be a first-class equal rather than a degraded mode, and that argument was accepted further than
> it asked — `dockedLayout` now defaults to `true` for a new profile. The no-loss contract below is
> unchanged and is now load-bearing for *every* player rather than for the ones who opt in, which is
> the strongest form of the guarantee this section was reaching for. `ClientCommands`'s `dock` verb
> and Settings → Layout switch back.

The word "fallback" appears in both source documents and it is the wrong frame to build against: a fallback is something you ship late, test less, and let drift. `05-tool-windows-and-layout.md` §5 already refuses that framing and specifies the shell — one `Stage` (`dock`), a fixed 96px rig strip, a rail, a `SplitPane` of one to three `TabPane`s, an alert tray, with `1440×900` default / `1024×640` minimum / `800×600` floor. That design is not restated here.

What this document fixes is the **accessibility contract the docked mode must satisfy**, so that a reviewer can check it and so that a future refactor knows which of `05` §5's properties are load-bearing rather than incidental. Each row names where `05` already satisfies it.

| # | Requirement | Satisfied by | Why it is load-bearing |
|---|---|---|---|
| **D-1** | The rig strip is **chrome, not a pane**: it never scrolls, never collapses, never hosts a tab, and cannot be occluded by a pane, popover, banner or `ModalPane`. | `05` §5.2, §6.2 (banners are overlays that do not reflow, and no window's top 32px holds a time-critical control) | Pillar C2. In multi-window mode `alwaysOnTop` does this; in one window there is no z-order to lose, which is *stronger*. "One of the panes happens to be the rig monitor" would not be. |
| **D-2** | The shell is **fully usable at one and two content columns**, and the live-breach layout must not *require* three. | `05` §5.6 (the `map` column collapses to a 32px target strip inside `terminal` below 1024px of content width) | Three columns is `05` §5.2's ceiling, not its floor. At 400% magnification the *effective* column count is one. The degradation path is the accessibility feature, and `05` §5.6 specifies it rather than letting it emerge. |
| **D-3** | During a breach, **compute, trace and the target are simultaneously visible** in one window. | `05` §5.5, which states this as its own requirement and shows the geometry at 1280×800 | This is the specific thing `../design/05-hacking-minigame.md` §5 flagged. If it fails, the flag is not closed. |
| **D-4** | The rail shows every window, its state, its alert rung and its headline value, always. | `05` §5.2, §6.7 | It is the docked mode's whole navigation surface, and the answer to "where is the thing shouting at me". |
| **D-5** | Mode switching is **live, mid-breach, and state-preserving**: the same `Node` instance is re-parented, never rebuilt. Scroll position, table selection, map viewport, half-typed probe text, filter state all survive. | `05` §5.3 | Same reasoning as the theme switch (`02-platform-native-themes.md` §2.6 constraint 3). *"A player who switches modes mid-breach to get a manageable layout and loses their typed probe has been punished for using the accessibility feature."* |
| **D-6** | The shell is usable at its stated minimum (**1024 × 640**) with **text scale 150%**, and at the **800 × 600** floor at 100%, without two-axis scrolling in any readout. | `05` §5.2, §5.6 + §7.5 here | The magnifier and small-display cases, made into two numbers a reviewer can set. Checklist **G3**. |
| **D-7** | No information or functionality differs between modes — only simultaneity. | `05` §5.4's four mechanical rules, in particular **one pane factory per window id** and the `DockContext` that exposes width, height and density but has no `isDocked()` | A promise in prose erodes; `05` §5.4 makes the wrong thing unwritable rather than merely discouraged. That is the right shape and this document adopts it as the acceptance criterion for **G1**. |
| **D-8** | The docked mode is reachable, and **discoverable**, by a player who does not know it exists. | **§2.4 below — owned here** | Everything above is worthless if the barrier and its remedy never meet. |

One structural consequence worth stating, because it is what makes D-7 checkable rather than aspirational: since a pane cannot ask which mode it is in, testing is **one matrix with a mode axis**, not two products. Whether a player may additionally *pop out* a single pane from docked mode into a `Stage` — the hybrid a second-monitor user would want — is deliberately not asserted here; `05` §3.1 defers the related `openNew()` question and it is raised as **AX-10**.

### 2.4 How a player finds out it exists

An accommodation nobody discovers is an accommodation that does not exist. Four routes, in the order a player meets them:

1. **First run asks, as a preference.** A one-screen choice — *separate windows* or *one window with panels* — shown with two live miniature layouts, not screenshots (§7.4). **It is not labelled as accessibility, and it is not framed as a fallback.** Two failure modes are avoided at once: players who need it skip anything labelled "accessibility mode" out of self-image, and players who would simply prefer it never look in that menu. Copy is owned by the onboarding doc; the framing constraint is owned here.
2. **A one-time inline notice in the `switcher`**, the first time a fourth window is opened in a session: one line, three buttons (`try docked` / `keep windows` / `don't ask again`), inline in the rail. Not a modal, never during a live engagement, never repeated.
3. **The command palette and settings.** `layout docked`, `layout windows`, `layout gather` — Unix vocabulary per pillar C6 — plus Settings → Layout. `Shortcut+Shift+D` toggles (`00-client-overview.md` §6.3, `05-tool-windows-and-layout.md` §5.3).
4. **A conditional offer, never an application.** When any of these hold at startup, the notice in route 2 is shown immediately rather than at the fourth window:
   - a single display below **1600 × 900** logical px;
   - text scale **≥ 150%** (§7.2);
   - a tiling-WM token in `XDG_CURRENT_DESKTOP` (`02-platform-native-themes.md` **PN-11**);
   - OS reduced-motion set — a weak but real correlate, and the cost of a wrong guess here is one dismissible line.

> **The client never switches layout mode by itself.** Layout is client-owned state the player chose (`00-client-overview.md` §1.1). A program that rearranges your desk because it inferred something about you is a program that gets its inference overridden and its judgement distrusted. Offering is help; applying is presumption.

Detecting an active screen magnifier or screen reader would be a far better signal than any of the above, and is probably not portably available — and would sit awkwardly against the closed read-list in `02-platform-native-themes.md` §2.9. **AX-8**.

### 2.5 What multi-window mode owes even when it stays

Docked mode is not permission to leave the default hostile.

- **Raising is never stealing** (`00-client-overview.md` §6.2) — restated here because it is an accessibility rule, not just a politeness rule. A window that raises itself during a breach is a focus loss for a keyboard player and a lost reading position for a screen-reader user.
- **Every window is reachable by keyboard alone** (§3.9), so window management never requires the pointer.
- **Window geometry persists per profile** (`00-client-overview.md` §4.5, `05-tool-windows-and-layout.md` §3.8), so the arrangement cost is paid once, not per session. A player who has laid out a dozen windows exactly right must never have to do it twice, and `05` §3.8's placement validator repairs displacement automatically when a display disappears.
- **`Shortcut+0` is unremappable** and raises the rig monitor from anywhere (`00-client-overview.md` §6.3). At minimum, the master scarcity is always one keystroke away regardless of what the desktop looks like.
- **A deliberate "gather" action** (`layout gather`) is the manual counterpart to `05` §3.8's automatic repair: it moves every open `Stage` onto the display holding the focused window, in a deterministic tiled arrangement, without changing which tools are open. Automatic repair fixes windows that became invalid; gather fixes windows that are valid and still unreachable — behind a maximised application, on a virtual desktop the player cannot navigate to, scattered by a tiler. Cheap to build, and the alternative is a player editing `layout.json` by hand.

---

## 3. Keyboard

### 3.1 The rule

> **No game-critical action is pointer-only.** *Game-critical* means: anything that changes server-owned state, anything with a consequence in `../design/05-hacking-minigame.md` §2's contract, and anything reachable while a clock is running.

Concretely and exhaustively for the current design: probe entry and layer interaction; equipping, socketing and unsocketing tools; compute reallocation; abort; deploy, kill, crack, hijack and sabotage a miner (`../design/04-mining.md` §5); arm and disarm defenses; storage tier moves; market purchase; ledger inspection, filtering and copy; map node selection, traversal and recon application; bot instantiation and backlog response; theme, density, layout and text-scale settings.

The single best test of this section is checklist **B1**: complete a full breach with the pointer physically unplugged, in both layout arrangements. It is also the cheapest, and it catches more than every static check combined.

### 3.2 Sticky-keys and hold-free operation

- **No binding requires more than two modifiers.** The heaviest in the client is `Shortcut+Shift+X`, which is two.
- **No action requires a key to be *held*** as its only form. `00-client-overview.md` §6.3 binds `Alt`-held to attribution overlays, and `05-tool-windows-and-layout.md` §3.5 gives it a 200 ms threshold with any chord suppressing it; `02-platform-native-themes.md` §3.4/§6.3 already requires `Shortcut+Shift+A` as an equivalent toggle on every platform, because Windows and GNOME contest `Alt`. That mitigation is restated here as an accessibility rule in its own right: **a hold is never the only way** — and the 200 ms threshold is acceptable *only* because the equivalent exists.
- **No action requires a double-click, a chord, a timed release, or a repeat rate.** `../design/00-vision-and-pillars.md` §7 already rules out twitch input as a design matter; this makes it an input-mechanism rule too.
- **No action requires simultaneous inputs**, which is what makes the client work with sticky keys, an on-screen keyboard, a head pointer, or a switch device.

### 3.3 The focus indicator

| Property | Value | Source |
|---|---|---|
| Thickness | **2px**, drawn *outside* the control's own bounds | `01-visual-language.md` §1.5 |
| Colour | `-es-focus-ring` | `01-visual-language.md` §1.5 |
| Contrast | **≥3:1** against the control fill **and** against the surface behind it | SC 1.4.11; enforced by the guard in `02-platform-native-themes.md` §2.7 ("the focus ring never negotiates") |
| Area | ≥ the area of a 2px perimeter of the unfocused control, at ≥3:1 between the focused and unfocused states of those pixels | SC 2.4.13 (AAA), verified text |
| Obscuring | Never fully covered by a popover, scrim, pane divider or sticky header | SC 2.4.11 |

Two rules JavaFX makes cheap and that we take:

- **`:focus-visible` is used, and `:focused` is not sufficient.** Verified: the JavaFX 26 CSS Reference documents `focus-visible` and `focus-within` pseudo-classes alongside `focused`, backed by `Node.focusVisible` and `Node.focusWithin` read-only properties. Ring on `:focus-visible`; container emphasis on `:focus-within` in docked mode, so the player can see which pane owns the keyboard.
- **The ring is never suppressed for pointer users.** A player who alternates mouse and keyboard — which is most players, and disproportionately players with intermittent pain or fatigue — must not have to press Tab twice to find out where focus went. `focusVisible` handles the common case; we do not add a second heuristic on top of it.

### 3.4 Traversal order

- **Every window declares its traversal order explicitly.** Default scene-graph order is an accident of build sequence and silently regresses when a panel is refactored.
- Order follows the **visual reading order** of the window: rig strip or header, then panels top-to-bottom, then within a panel left-to-right, then the action row.
- **Decorative nodes set `focusTraversable = false`** and expose no accessible name (`01-visual-language.md` §6.4).
- **`F6` / `Shift+F6` cycle regions** — panes in docked mode (already committed in `02-platform-native-themes.md` §4.4), and panel groups within a window in multi-window mode. Same key, same meaning, both arrangements.
- A **composite primitive is one stop, not many.** An `es-gauge` with eight segments takes one Tab, and its detail is reached by entering it (`Enter`/`→`) or by the attribution overlay. Eight tab stops per gauge would make the rig monitor forty stops deep and unusable under a timer.
- **Traversal never wraps silently between windows.** Tab from the last control returns to the first control of the same window. Moving between windows is an explicit action (§3.9), because a wrap that jumped windows would be indistinguishable from a bug.

### 3.5 No traps, and the one real trap

Every popover, `ModalPane` and inline confirmation dismisses on `Escape` and returns focus to the control that opened it. That covers the ordinary cases.

**The terminal buffer is the real risk**, because a text surface that swallows `Tab` is the classic keyboard trap, and the Unix idiom actively wants `Tab`:

| Key | Behaviour in the `terminal` window | Note |
|---|---|---|
| `Tab` | **Completes** the current command token | Pillar C6: this is what a shell does, and teaching that is the point (`00-client-overview.md` §5) |
| `Shift+Tab` | Cycles completion candidates backwards | — |
| `F6` | Leaves the terminal pane entirely | The guaranteed exit, identical to every other pane |
| `Escape` | Moves focus from the scrollback buffer back to the prompt; at the prompt, clears the line | Never aborts a breach — see §8.6 |
| `Shortcut+Tab` | Reserved; **not bound**, because window managers on all three platforms contest it | — |

The exit must be **discoverable, not merely present**: the first time the terminal takes focus in a profile, an inline one-line hint states `tab completes · F6 leaves this pane`, dismissible and never repeated. A trap the player cannot escape *and does not know how to escape* are the same trap.

Text editing inside the terminal follows the **platform's** editing key set, not one invented set (`02-platform-native-themes.md` §3.4); the key table belongs to `04`.

### 3.6 Hover-or-focus content (SC 1.4.13)

The teaching layer and the attribution overlays are the client's two hover-or-focus surfaces, and both are pillar-level features (C3, C6). All three sub-requirements apply, verbatim:

- **Dismissible** — `Escape` closes any popover **without moving the pointer** and without moving focus off the trigger. Nothing else is required to close it.
- **Hoverable** — the pointer may travel from the trigger into the popover without it vanishing. This means a tolerance corridor and a dismissal delay, not an immediate `onExit` close. A definition that disappears when you reach for it is a definition you cannot read, and this is the single most common way tooltip systems fail this criterion.
- **Persistent** — it remains until dismissed, until focus moves away, or until its content becomes invalid. **No popover auto-dismisses on a timer.**

Additionally, per `01-visual-language.md` §8.10: the popover never moves layout and is never shown unrequested during a live breach. `04-terminology-and-education.md` §5.2 refines the focus rule usefully — a popover opened **by keyboard** takes focus, because a keyboard user asked for it and otherwise could not read it; a popover opened by **pointer** never does.

> **The `Tooltip` landmine, and it is worth repeating in this document because it is the single easiest way to fail 1.4.13 without noticing.** `04-terminology-and-education.md` §5.1 verified that `javafx.scene.control.Tooltip.showDuration` **defaults to 5000 ms**. A stock JavaFX tooltip therefore fails *Persistent* out of the box, and fails it invisibly — nobody notices in testing, because nobody in testing is still reading at second six. Its documented activation is pointer-only, so it fails SC 2.1.1 as a definition mechanism as well.
>
> **The global rule, generalised from `04` §5.1's local one:** the teaching layer does not use `Tooltip` at all, and **every other `Tooltip` in the client sets `showDuration` to `Duration.INDEFINITE`** — including the icon-only-control tooltips `01-visual-language.md` §6.4 mandates. One factory method, and constructing a bare `Tooltip` anywhere in the client is an ArchUnit failure. Checklist **C12**.

### 3.7 Drag alternatives (SC 2.5.7)

| Drag interaction | Required non-drag equivalent |
|---|---|
| Pan the network map | Already specified in `05-tool-windows-and-layout.md` §2.5: arrows pan, `Home` fits to content, `Tab`/`Shift+Tab` walk nodes in a deterministic order, `Shortcut+F` filters and the filter drives the traversal order |
| Socket a tool into a bot | The `es-item-card` action menu carries `socket into…`, and the `botnet` window's frame row carries `socket…` — reachable from either end |
| Resize a docked split | `Shortcut+Shift+←/→` moves the active divider in 5% steps; `dock reset` in the palette restores the ratio. **Deliberately not an `Alt` chord** — `05-tool-windows-and-layout.md` §3.5 keeps `Alt` free of chords so the attribution reveal is never suppressed by one |
| Reorder the `switcher` | Move-up / move-down actions in the rail's context menu |
| Select a range of ledger rows | `Shift+↑/↓`, standard list semantics |
| Move an item between storage tiers | Tier is a control on the item, not a destination you drag to — the drag is the alternative here, not the primary |

The last row states the design bias generally: **where a drag and a control both make sense, the control is primary and the drag is the affordance layered on top.** That ordering costs nothing and means the equivalent can never be forgotten, because it was built first.

### 3.8 Target size (SC 2.5.8)

`01-visual-language.md` §4.3 already exceeds the 24 × 24 floor for controls (32/28 standard, 40/36-never-below-32 for time-critical). Two gaps this document closes:

- **A chip is not a target unless it is actionable.** `es-chip` at compact density with `SPACE_HAIR`/`SPACE_1` padding and `ICON_12` can fall below 24px tall. That is fine for a *status* chip, which is text. **An actionable chip is a target and gets 24 × 24 minimum regardless of density**, achieved by padding the hit area beyond the visual bounds rather than by growing the chip — so density still looks dense and still meets the floor.
- **Undersized targets take the spacing exception explicitly.** Where a control genuinely cannot be 24px (an inline gate glyph inside a table cell), the 24px-diameter-circle rule applies: circles centred on each undersized target's bounding box must not intersect another target's circle. This is a layout constraint on dense tables and it is checkable (checklist B11).

The **time-critical** floor is the one that matters most and is already stated: abort, defend, reallocate and disarm do not shrink with density (`01-visual-language.md` §4.3).

### 3.9 Moving between windows by keyboard

This is the part a single-window app never has to solve, and getting it wrong makes §2's barrier worse rather than better.

**The OS owns window cycling and we do not fight it.** `Cmd+\`` on macOS is the platform's own frontmost-application window cycle and is deliberately left unbound (`02-platform-native-themes.md` §3.4); `Ctrl+\`` is bound on Windows and Linux, where no OS owner exists. `Alt+Tab` / `Cmd+Tab` are never touched anywhere.

**What we add, and what each addition must guarantee:**

| Binding | Action |
|---|---|
| `Shortcut+0` | Raise `rig-monitor` (unremappable, `00-client-overview.md` §6.3) |
| `Shortcut+1`…`9` | Raise tool window *n* in switcher order |
| `Shortcut+\`` | Cycle open tool windows (not bound on macOS) |
| `Shortcut+Shift+\`` | Cycle backwards (not bound on macOS) |
| `Shortcut+Shift+J` | Raise the switcher (`05-tool-windows-and-layout.md` §6.7) — breadth |
| `Shortcut+Shift+N` | **Notices** — move focus into the **alert tray** (`05-tool-windows-and-layout.md` §6.7), on its newest unacknowledged row. Added here; the tray is `05`'s surface, this is its keyboard door (§4.5) |
| `Shortcut+=` / `Shortcut+-` | Text scale up / down (§7.2); `text-scale reset` in the palette |
| `Escape` | Dismiss the topmost popover or confirmation. **Never aborts an engagement** (§8.6) |

> **Raising a window must also move keyboard focus into it, and the arrival must be announced.** Three things happen in order: `Stage.toFront()` + `requestFocus()`; focus lands on the window's designated **entry node** (its primary readout, not a scrollbar or a close button); the window's accessible name is what the screen reader reads on arrival. A raise that leaves focus in the previous window is worse than no raise — the player is now looking at one window and typing into another, which under a trace timer is a losing state that looks like a bug.

**Remapping.** Every binding is remappable except `Shortcut+0` (a pillar-C2 decision, `00-client-overview.md` §6.3) and `Escape`'s dismiss. Conflicts are detected at bind time and reported inline with both claimants named — never silently last-writer-wins. Remapping matters more than usual for a client with this many accelerators (`00-client-overview.md` §6.3 plus `05-tool-windows-and-layout.md` §3.5): a player using a one-handed layout, a foot switch, or an alternative keyboard needs bindings within their reach, and no default we choose is right for all of them.

---

## 4. Screen readers

### 4.1 What JavaFX actually gives us, verified

| Surface | Status |
|---|---|
| `Node.accessibleText`, `accessibleHelp`, `accessibleRole`, `accessibleRoleDescription` | **Verified** present on `javafx.scene.Node` (JavaFX 26 javadoc), all four as `ObjectProperty` with getters/setters |
| `Node.queryAccessibleAttribute(AccessibleAttribute, Object...)` | **Verified** — how assistive technology reads a custom node |
| `Node.executeAccessibleAction(AccessibleAction, Object...)` | **Verified** — *"called by the assistive technology to request the action indicated by the argument should be executed"* |
| `Node.notifyAccessibleAttributeChanged(AccessibleAttribute)` | **Verified** — the only push mechanism that exists |
| `AccessibleRole` | **Verified** — includes `PROGRESS_INDICATOR`, `SLIDER`, `TEXT`, `TEXT_AREA`, `IMAGE_VIEW`, `NODE`, `PARENT`, `TABLE_VIEW`/`TABLE_ROW`/`TABLE_CELL`, `LIST_VIEW`/`LIST_ITEM`, `TREE_VIEW`, `TITLED_PANE`, `TOOL_BAR`, `BUTTON`, `TAB_PANE`, and others; each documents the attributes it must support |
| `AccessibleAttribute` | **Verified** — 61 constants. **None of them is an alert, live region, or announcement.** §4.5. |
| Implementation strategy | JEP 204: JavaFX implements the platform accessibility API directly, so **no Java Access Bridge is required** |

### 4.2 Which screen readers actually work — and the Linux problem

| Platform | Bridge | Screen readers | State |
|---|---|---|---|
| Windows | UI Automation, in JavaFX's own native layer | Narrator, NVDA, JAWS | Supported |
| macOS | NSAccessibility | VoiceOver | Supported |
| **Linux** | **none — no ATK / AT-SPI implementation** | Orca | **Not supported** |

The Linux row is sourced from Oracle's own statement on the GNOME `orca-list` (2015): *"we currently support Windows and Mac platforms. We have no plan to make FX accessible on Linux"*, corroborated by later `openjfx-dev` and `orca-list` discussion (2022–2023) reporting JavaFX applications as unreadable by Orca while Swing applications are readable via the Java ATK Wrapper. OpenJFX maintains an "Accessibility Exploration" wiki page, which is itself evidence that the work is exploratory rather than shipped (⚠ the page returned HTTP 403 in this pass and was not read).

> ⚠ **This must be re-verified against JavaFX 26 on a current Linux desktop before any statement is made to players.** A decade-old vendor statement is a strong signal and not a current fact. Tracked as **AX-1**, and it is shipping-blocker-shaped, because Linux is a first-class platform for this game (`../architecture/01-tech-stack.md` §1) and self-hosting culture skews toward it. `04-terminology-and-education.md` §5.3 raises the same question as **T-5** and marks it unverified; the sources above are this document's answer to it, and **AX-1 subsumes T-5**.

**The honest consequence, stated rather than buried:** if AX-1 confirms, **a blind player cannot play this game on Linux**, and no amount of `accessibleText` we write changes that. What we do anyway:

1. **Author the accessibility tree correctly regardless of platform.** It costs the same, it is exercised on two of three platforms, and the day a Linux bridge lands the client is ready rather than starting.
2. **Never let the bridge's absence degrade Linux for sighted players.** Nothing may depend on accessibility APIs for its ordinary behaviour.
3. **Say so in the system requirements**, in specific words, before install — not after. "Screen reader support: Windows and macOS. Not available on Linux (toolkit limitation)." A player who finds this out after installing has been misled by omission.
3b. **Adopt `04-terminology-and-education.md` §5.3's mitigation globally, because it works with no bridge at all.** Every surface renders **real, selectable, copyable text in the normal reading order** — which `01-visual-language.md` §8.7 already requires of log lines for gameplay reasons. A player using screen magnification, OS text selection, or a clipboard-reading tool then gets the content even where no accessibility API is listening. That is a genuinely different failure mode from "the accessible name is wrong", and it is the only mitigation that survives AX-1 resolving badly.
4. Evaluate whether a client-side self-voicing layer is worth it (**AX-2**) — noting that it collides with the client's non-goals: an external TTS dependency, and a security boundary that forbids subprocess spawning (`00-client-overview.md` §7).

### 4.3 The composition rule

> **A primitive announces itself as one sentence containing its label, its value, its unit, its authority state, and its state classes — in that order. Detail is available on entry; it is never a prerequisite.**

The reason is pillar C5 (`00-client-overview.md` §2). A sighted player takes the compute state in with one glance; a screen-reader player must not have to traverse eight segment nodes to learn the same thing while a trace timer runs. **Composition is how a screen reader gets a glance.** Traversal remains available for the player who wants the decomposition — that is pillar C3 — but it is opt-in, not the only path.

Two corollaries with teeth:

- **Authority state is an adjective on the value, never a sibling node.** `es-authority` (`01-visual-language.md` §8.4) never becomes a separate tab stop. `unknown` announces the **word** "unknown" — never the `—` glyph, which screen readers variously read as "em dash", "dash", or nothing at all. The whole point of `01-visual-language.md` §2.2.8 is that `—` is honest where `0` is a lie; that honesty must survive into speech.
- **A truncated identifier exposes its full value.** `01-visual-language.md` §9.3 middle-truncates handles, DIDs and hashes. The accessible text carries the complete string, always, and so does copy. A screen-reader user must never receive `did:plc:xxxx…yyyy` as the value, because that is not a value.

### 4.4 What every primitive must expose

Against the ten primitives in `01-visual-language.md` §8. Roles are chosen from the verified `AccessibleRole` set, and each role's documented required attributes must actually be answered by `queryAccessibleAttribute`. Where `06-resource-and-inventory-ui.md` specifies the surface's content, the composed text follows it rather than inventing a second wording.

| Primitive | Role | Required attributes | Composed accessible text |
|---|---|---|---|
| **`es-gauge`** | `PROGRESS_INDICATOR` | `VALUE`, `MIN_VALUE`, `MAX_VALUE`, `INDETERMINATE` (all documented as required for this role) | `compute, 72 of 100 cycles available; allocated: Breacher 22, Miner 10, Recon 8; 18 recovering, 4 minutes 20 seconds; loaded, 84 percent, recovery times 4.6` — the thermal band, load and multiplier are part of the sentence because `06-resource-and-inventory-ui.md` §2.3 makes them the decision. `accessibleRoleDescription` = the domain word (`compute gauge`, `trace meter`, `noise meter`, `yield buffer`, `block progress`) |
| — trace variant | " | " | Adds the band and every contribution segment on entry: `trace 61 percent, imminent; +12 from Overflow Kit at 21:04:33; +8 from Fuzzer at 21:04:11` — this is `../design/05-hacking-minigame.md` §4's legibility requirement carried into speech |
| — noise variant | " | " | Adds thresholds with values: `noise 43, moderate; threshold 60 not crossed` |
| — projections | " | " | A hover/focus projection (`06-resource-and-inventory-ui.md` §2.3c) announces as `projected: working, 62 percent, recovery times 1.9` — the word **projected** is mandatory, because an outline is C4's honesty mechanism and speech has no outlines |
| **`es-stat`** | `TEXT` | `TEXT`, `FONT` | `label, value unit, authority, delta with its window` — e.g. `deployed yield, 128.40 EC per hour, confirmed, up 12.00 EC over 1 hour`. Never the bare number. |
| **`es-chip`** | `TEXT`, or `BUTTON` when actionable (then `FIRE`) | `TEXT` (+ `FIRE` action) | **The domain is part of the name**: `heat, personal, moderate` — not `moderate`. A chip stripped of its domain is unattributable, which is C3 in reverse. |
| **`es-authority`** | none — not a node | — | Folded into the host primitive's text: `…, pending: deploy miner`, `…, last seen 14 seconds ago`, `…, unknown` |
| **`es-ledger-row`** | `TABLE_ROW` inside a `TABLE_VIEW`; cells `TABLE_CELL` | Row: `TEXT`, `INDEX`. Cells: `TEXT`, `ROW_INDEX`, `COLUMN_INDEX`, `SELECTED`. Table: row/column counts, `CELL_AT_ROW_COLUMN`, `HEADER`, `SELECTED_ITEMS` | `21:04:33, debit 45.00 EC, to did:plc:z72i7hd… (full value in the cell), reason: relay hop, no ledger entry, confirmed`. Column headers must resolve via `HEADER`, or every cell is an orphan number. |
| **`es-item-card`** | `LIST_ITEM` inside a `LIST_VIEW` | `TEXT`, `INDEX`, `SELECTED` | `Overflow Kit, intrusion tool; blocked: schematic gate requires Overflow Kit schematic; 220 EC, 14 cycles, very high noise; standard storage; provenance verified` — the gate requirement is stated **in words** (`01-visual-language.md` §8.9) and the verdict is the server's (pillar C4). `06-resource-and-inventory-ui.md` §6.1 requires one accessible name shared by all three renderings of an item (vault list, market listing, loadout slot, bot socket) — so this string is derived once from the model, never per surface |
| **`es-log-line`** | `LIST_ITEM` inside a `LIST_VIEW` | `TEXT`, `INDEX`, `SELECTED` | `21:04:33, warning, sshd: connection from 10.4.19.2 has no owning process` — severity as a **word**, since the glyph is not speech |
| **`es-node`** | see §4.6 | — | see §4.6 |
| **`es-gate-badge`** | `TEXT` | `TEXT` | `reputation gate, requires Sickle standing: trusted, blocked` — and for a split gate, both components, ceiling component first (`01-visual-language.md` §8.9) |
| **`es-term`** | inherits its host; the Tier-2 popover's root uses `TOOLTIP` | `TEXT`, plus `HELP` | Specified in `04-terminology-and-education.md` §5.3 and adopted verbatim: `accessibleText` = `"<term>, term, <status>"`, `accessibleHelp` = the gloss. The term's rendered text is **unchanged** — the marker must not alter it or its metrics. This gives screen-reader users the teaching layer (`00-client-overview.md` §5) without hover, which is the one interaction they cannot perform, and the status is **spoken as a word** so the chip's fill is never the sole carrier. |

Plus the rule already in `01-visual-language.md` §6.4, restated because it is the easiest regression to introduce: **every icon-only control sets `accessibleText`, and it is the same string as its tooltip.**

### 4.5 Urgent events — the 4.1.3 gap and what we do about it

**Verified: JavaFX has no live region.** `AccessibleAttribute`'s 61 constants contain nothing for alerts or announcements. There is exactly one push mechanism — `notifyAccessibleAttributeChanged(AccessibleAttribute)` — and whether a screen reader announces a `TEXT` change on an **unfocused** node is a per-screen-reader, per-platform behaviour that is not specified anywhere we could verify.

The design therefore does not rest on it. It rests on a surface that already exists.

**Path A — the alert tray, read by keystroke (guaranteed, no toolkit dependency).**

`05-tool-windows-and-layout.md` §6.7 already specifies an **alert tray** holding every rung-3 and rung-4 item with its deadline, sorted by time remaining ascending, with inline resolving actions — a section of the switcher in multi-window mode, the bottom band in docked mode, same content and order in both. That is precisely the surface a screen-reader user needs, and it was designed for a different reason (triage under split attention), which is a good sign it is the right shape.

What this document adds to it, and nothing more:

1. **`Shortcut+Shift+N` moves focus into the tray**, on its newest unacknowledged row, from any window, at any time, including mid-breach.
2. **The tray is a `LIST_VIEW` of `LIST_ITEM`s** (§4.4), so every row is individually navigable and speaks its cause, its deadline and its available actions.
3. **The unacknowledged count is part of the switcher's accessible name**, so "is anything waiting" is answerable without leaving the current window.
4. **Rung-0/1/2 events also reach it**, in a second collapsed section (`recent`) that the tray does not expand for. `05` §6.2 keeps rungs 0–2 out of the tray deliberately, and that is right for a sighted player who can see a dot on a switcher entry — a screen-reader user cannot, so the events must be *retrievable* even though they are not *promoted*.

This works with no live-region support anywhere, on any platform, including a Linux build with no accessibility bridge at all — because it is **pull**, and pull needs only a keystroke and a name.

**Path B — spoken announcement (best-effort, verify per screen reader).**
One persistent, never-focused node per window whose `accessibleText` is replaced with the alert sentence, followed by `notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT)`. ⚠ **unverified**: whether Narrator, NVDA, JAWS and VoiceOver announce this. Testing it is checklist **C7** and **AX-3**. If it works it is enabled by the setting below; if it does not, Path A is unaffected.

**The setting** — `announce urgent events`, three values:

| Value | Behaviour |
|---|---|
| `off` | Tray only; no emphasis beyond the unacknowledged count |
| `tray` (**default**) | Tray, plus `05` §6.2's ladder exactly as specified for every player |
| `speak` | The above, plus Path B for rung ≥ 3 events, where it is verified to work on this platform |

**Rung ≥ 3, and not lower, is the announcement threshold.** `05` §6.2 assigns rung 3 to events *carrying a deadline* and rung 4 to events that *change what a window means*. Those are exactly the events a player must know about without looking, and rungs 0–2 are exactly the events that would turn speech into a firehose. Reusing `05`'s classification means there is one list to maintain, not two that drift.

> **No level steals focus.** Not one, and `05` §6.3 says the same from the layout side — *"there is no rung 5."* `00-client-overview.md` C5 calls focus theft during a breach "the single most damaging thing this client could do", and that is *more* true for a screen-reader user, not less: stolen focus loses their reading position and their place in the puzzle in the same instant.

**Coalescing is an accessibility feature.** `05` §6.5's `causeId` rule — correlated events produce exactly one ladder entry naming the cause, itemising the members — matters more in speech than on screen. Eleven "miner lost" lines are noise on a display and are an unusable wall in a screen reader. One entry that says `Eye sweep · heat: high · 45%/hr` and then lists what was lost and what survived is the difference between a designed beat and an unrecoverable session.

**Rate limiting is an accessibility requirement, not a performance one.** A trace meter changing continuously would flood any announcement channel. **Continuous values are never announced per tick**: they announce on **band change** and on **threshold crossing** only, and the continuous value stays queryable at all times. This is the same reasoning that made heat a banded chip rather than a meter (`01-visual-language.md` §2.2.4) — thresholds are the decision, so thresholds are the event. It composes with `05` §6.4's existing limit of one promotion to rung ≥ 2 per window per 2000 ms.

**Log streams do not auto-announce.** During a breach the terminal produces lines faster than speech can carry them. The player opts in per stream and per severity (`announce this stream: warnings and above`), and the default is off.

**A note on the ladder ceiling.** `05` **WL-7** asks whether the alert-ladder ceiling should be a player setting, and observes the risk: a player capping everything at rung 1 could lose a botnet to a backlog timer they never saw. From this document's side the answer is that it *should* be a setting, and the risk is handled by the setting's own copy stating the consequence plainly — a player is allowed to choose fewer interruptions and should be told what it costs. Capping the ladder must not, however, remove items from the tray: the ceiling governs **promotion**, never **retrievability**.

**Log streams do not auto-announce.** During a breach the terminal produces lines faster than speech can carry them. The player opts in per stream and per severity (`announce this stream: warnings and above`), and the default is off.

### 4.6 The map, and one deliberate concession

`es-node` (`01-visual-language.md` §8.8) is the one primitive where shape carries primary meaning because the surface is scanned spatially. Speech has no spatial channel, and a graph read node-by-node with no spatial cue is worse than useless — it is a list in a random order.

**Half of this is already solved.** `05-tool-windows-and-layout.md` §2.5 makes the `map` fully keyboard-complete with a **deterministic** traversal order — hop distance ascending, then address lexicographic — and states the reason in the same terms this section would have: *"deterministic traversal order is what lets a screen-reader user walk the graph at all, and it is what makes 'the third node at two hops' a sentence two players can exchange."* That is the ordering problem closed.

What remains is that walking 41 nodes one `Tab` at a time is navigation, not comprehension. So:

> **Decision: the `map` window ships a first-class *table view* of the same graph, in the same deterministic order, and that table is the map's accessible representation.**

- Columns: address · kind (the shape's meaning, in words) · hop distance from the entry node (`../design/01-core-resources.md` §3.1) · knowledge state (`unknown` / `sniffed` / `mapped` / `analyzed` / `breached`) · defense profile · badges (miner present, canary, suspected trap, yours) · live/dormant.
- It is a `TABLE_VIEW` with the roles and attributes in §4.4, so it is sortable, filterable and scannable by column — which is what turns 41 nodes into an answerable question rather than 41 announcements.
- `05` §2.5's `Shortcut+F` filter already drives the traversal order; it drives the table's rows too, so filter and view never disagree.
- The graph canvas itself is **one** accessible node whose text is a summary — `network map, 41 nodes, 12 mapped, 3 breached, 1 suspected trap`. Individual glyph positions are not read aloud; the nodes are reached through the traversal order and the table.

**The tradeoff, stated:** a table is a genuinely worse tool for the Traversal puzzle class (`../design/05-hacking-minigame.md` §3.1), which is *about* reading a graph. A sorted table with a hop-distance column preserves the pathfinding information; it does not preserve the shape of the graph. This may be the point at which the Traversal class needs a non-spatial expression of its own, which is puzzle design and belongs to `05-hacking-minigame` — see **AX-7**.

**It also serves pillar C6 and everyone else.** `traceroute` and `netstat` present graphs as tables; that is what the fiction's own tools do. The table view is bound to `Shortcut+Shift+L` in the `map` window and is offered to every player, not routed behind an accessibility setting. Accommodations that turn out to be good tools should be shipped as good tools.

---

## 5. Colour and contrast

### 5.1 What is already decided

`01-visual-language.md` owns the palette and this document does not relitigate it. Established-by-that-doc and cited here:

- The 69-token colour contract, three-layer model, and the naming grammar (`01-visual-language.md` §1–§2).
- The floors: **4.5:1** text, **3:1** non-text, measured and asserted in the build (`01-visual-language.md` §2.3).
- The never-colour-alone table (`01-visual-language.md` §2.4) and the greyscale acceptance test.
- The nine-hue budget (`01-visual-language.md` §2.5).
- The high-contrast treatments: `uos-hc` (`00-client-overview.md` §3.3) and the native equivalent (`02-platform-native-themes.md` §2.8).

What this section adds: the **testable form** of the colour rule, a **CVD analysis** of the risky families with numbers, a **finding** the contrast harness as currently specified would not catch, and the **high-contrast floor**.

### 5.2 Never colour alone, made testable

Two checks, one mechanical and one human, because neither is sufficient alone.

**⚙ Mechanical — the redundancy assertion.** For every node carrying an `es-state-*` class from the exhaustive matrix in `01-visual-language.md` §8.11, assert that the node's subtree contains **either** a text node with non-empty content **or** a glyph node with a non-empty accessible name. This is a scene-graph walk over a rendered window and it runs in the client's test sources beside the contrast harness. It catches the regression that actually happens: someone removes a label to fit a dense row and leaves the colour.

**◻ Human — the greyscale test.** `01-visual-language.md` §2.4 already names it; here is the method so a reviewer can run it: capture every window in the catalogue, in each shipped theme variant, at each density, in a populated state; desaturate to true greyscale (luminance-preserving, not "remove saturation" — a hue-rotate is not a test); then confirm every distinction in `01-visual-language.md` §2.4's table is still readable. Pass is binary per row. Failures are missing redundancy, never missing colour.

### 5.3 Colour-vision deficiency: the three risky families

Roughly 8% of men and 0.5% of women have a colour-vision deficiency, dominated by deuteranomaly/deuteranopia and protanopia. Three token families in this client are structurally at risk. Luminance figures below are computed from the published hexes in `01-visual-language.md` §2.3 using the WCAG relative-luminance formula (`L = 0.2126R + 0.7152G + 0.0722B` with the sRGB linearisation, verified).

**(a) The heat band ramp — and it is worse than it looks.**

`-es-heat-band-0…4` in `uos` runs `#7E8F8E` → `#C9C05A` → `#E09A4B` → `#F0663F` → `#FF4136`: grey, yellow, orange, red-orange, red. Under protanopia and deuteranopia the yellow→red span collapses toward a single yellow-brown, so the only surviving cue would be lightness. And:

> **Computed: band-0 and band-4 differ in relative luminance by 1.02:1.** (`#7E8F8E` L = 0.2603; `#FF4136` L = 0.2531.) **The two ends of the heat ramp are the same lightness.** Zero heat and named-hacker status are, in greyscale, indistinguishable.

`03-story-theme.md` §2.5 reaches the same conclusion independently from the other end, measuring **1.10–1.80** mutual contrast between *adjacent* bands and drawing the same rule: the ramp is continuous and the pip is what carries the value. Two independent measurements agreeing is as close to settled as a palette question gets.

That is not a defect — it is exactly why `01-visual-language.md` §2.2.4 renders heat as a **banded chip carrying the band name**, and why §2.4 mandates a **5-pip band indicator filled to the band**. The word and the pips carry the whole value; the hue is decoration. So the rule this section adds is a hardening:

> **The band word and the 5-pip indicator on a heat chip are non-removable.** Not at compact density, not in any theme, not under any setting, not to fit a row. `01-visual-language.md` **V-8** asks for a simulation pass before the palette is frozen and says to treat the pip as non-removable until then; the two measurements above resolve V-8 in the direction of *permanently* non-removable, because no palette adjustment within the nine-hue budget will make a five-step ramp survive both CVD and greyscale on hue alone. `03` §2.5 states the corollary this document endorses: the story theme **may never substitute a glow or a tint for the pip**.

**(b) Eye versus Sickle.**

`-es-faction-eye` `#A5B4C4` (blue-grey) vs `-es-faction-sickle` `#E08A57` (rust). Blue-vs-orange is one of the better CVD-safe oppositions, and the luminances differ (9.17 vs 7.34 against base — a mutual ratio near 1.25:1, which is *not* a distinguishing margin). `01-visual-language.md` §2.2.10 already resolves this correctly: **faction is a mark plus a name; the tint is secondary and never the only carrier.** Restated as a testable rule: the aperture and crescent marks must differ in **silhouette** at `ICON_12`, and no surface anywhere — including a 12px list row, including the map — conveys faction by tint alone.

**(c) Provenance: the classic red-green pair.**

`verified` / `unverified` / `broken` maps onto success / neutral / danger hues, and green-vs-red is the archetypal CVD failure. `01-visual-language.md` §2.2.9 already mandates distinct glyphs — shield outline, hollow shield, broken shield. Hardening: **the three shields must differ in silhouette, not merely in fill**, so the difference survives greyscale, 12px, and a monochrome print of a bug report. And per §2.2.9's own rule, `unverified` and `broken` never share a colour: "we haven't checked" and "we checked and it's forged" are opposite facts.

*(Ethecoin credit/debit is a fourth red-green pair and is already fully solved by `+` / U+2212 and the `CREDIT` / `DEBIT` words — `01-visual-language.md` §2.2.2.)*

### 5.4 A finding: adjacent meter fills do not clear 3:1

SC 1.4.11 requires **3:1 against adjacent colours** for graphical objects needed to understand content. The contrast harness described in `01-visual-language.md` §2.3 measures each token **against its surfaces**. It does not measure tokens **against each other** — and two fills in the same gauge are, definitionally, adjacent colours.

Computed from the published hexes:

| Adjacent pair | Palette | Mutual contrast | vs. 3:1 |
|---|---|---|---|
| `-es-compute-available` `#4FD6C4` vs `-es-compute-allocated` `#37A79D` | `uos` | **1.64:1** | fails |
| `-es-compute-available` `#0A6570` vs `-es-compute-allocated` `#083F45` | native light | **1.72:1** | fails |
| `-es-trace-fill` `#FF7B72` vs `-es-trace-imminent` `#FF4136` | `uos` | **1.37:1** | fails |

This is a real gap and it is not a palette mistake — the pairs are *deliberately* the same hue at two lightnesses, which is the right design (`01-visual-language.md` §2.3.4: "density = commitment"). Making them clear 3:1 against each other would either break the hue-family rule or push one of them below its own floor against the surface.

**The fix is structural, and it is the one this document owns:**

1. **Adjacent fill segments in any `es-gauge` are separated by a 2px gap drawn in the domain's `*-track` token.** A boundary becomes a *shape*, not a colour difference, and it clears 3:1 by construction because the track already contrasts with both fills. This also happens to solve a legibility problem `06-resource-and-inventory-ui.md` §2.2 creates for good reasons: control channels aggregate and bot frames render one segment per instance, so the compute gauge routinely shows several same-token segments in a row, where the gap is the only thing making the count readable.
2. **A texture is mandatory per segment role, not optional decoration.** `available` solid · `allocated` solid · `recovering` hatched · `overcommit` cross-hatched (this is already in `01-visual-language.md` §2.4; here it becomes the mechanism that makes the boundary legible rather than a nicety). The rendering mechanism is settled — `06-resource-and-inventory-ui.md` §2.2 specifies a 45° two-stop repeating `linear-gradient` over a 6px period, with no image asset and no per-theme raster. `noise-decay` takes the same hatch behind `noise-fg`.
3. **The legend is mandatory whenever a gauge has more than one segment** — already required for `es-gauge-compute` in `01-visual-language.md` §8.1, generalised here to all variants.
4. **A state change on a meter always changes texture or shape, never only colour.** `01-visual-language.md` §2.4 already does this for `imminent` (stepped fill) — which is precisely why the 1.37:1 trace pair is *safe in practice*. Generalising it is what makes the guarantee hold for the next state anyone adds.

**And the harness gains a second assertion:** for every gauge variant, every pair of segments that can be rendered adjacent is measured, and either clears 3:1 **or** is separated by the track gap in (1). Checklist **A2**. Whether the palette should also move is a question for the owning documents — `01-visual-language.md` **V-2** and `02-platform-native-themes.md` **PN-2** — and is raised here as **AX-5** rather than decided.

### 5.5 High contrast

Both families have a high-contrast treatment already (`00-client-overview.md` §3.3, `02-platform-native-themes.md` §2.8). Three additions:

1. **Reachable without an OS signal.** `Windows.SPI.HighContrast` is the only reliable platform signal (`02-platform-native-themes.md` §2.3, §2.8); macOS's "Increase contrast" is not surfaced as a typed property, and Linux has none without the XDG portal (**PN-1**). So **an explicit client setting exists on all three platforms**, defaulting to *follow system where available*. A player on macOS or Linux must not be worse served than a player on Windows because of a toolkit mapping gap.
2. **The floor rises by one step, in both directions.** Text **7:1** (SC 1.4.6, AAA); non-text, meter fills, boundaries and the focus ring **4.5:1** (one step above SC 1.4.11). Every `*-subtle` fill is suppressed, every `-es-border-divider` becomes `-es-border-control`, all translucency drops, gauge tracks go to `RADIUS_0` — all already specified in `02-platform-native-themes.md` §2.8; the numbers are what this adds.
3. **High contrast removes no information.** Same rule as density (`01-visual-language.md` §4.4): it removes *subtlety*, never a column, a value, a precision, or an accessible name.

### 5.6 The two settings that are not a palette fork

There is no colour-blind-safe palette variant, and there should not be one: a fourth palette would multiply the contrast surface (the reason `00-client-overview.md` §4.4 rejected per-window themes), and it would imply that the redundant encoding is optional for everyone else. If redundancy is mandatory, a CVD palette buys nothing.

What is genuinely useful is control over *how loudly* the redundancy is expressed:

| Setting | Default | Effect |
|---|---|---|
| **`always show state text`** | off | Every `es-chip` renders its text at every density, and no control is ever icon-only in a dense row. Costs horizontal space; buys the word on every state. |
| **`pattern fills`** | off | Every gauge segment gets a distinct texture, not only `recovering` and `overcommit`. Makes §5.4's fix universal rather than role-specific. |

Whether these should simply be the defaults is a sharp question and is left open (**AX-13**): if redundant encoding is truly mandatory, a setting to enforce it is evidence the mandate is not holding.

---

## 6. Motion, effects and photosensitivity

### 6.1 What already exists, and the one thing that changed

`01-visual-language.md` §7 owns motion and is unusually strict already: five duration tokens with `DUR_DELIBERATE` 400ms as a hard ceiling; `linear` mandatory for continuously-driven meters (a *correctness* rule, since an eased trace bar lies about rate); an explicit never-animate list covering numeric readouts, trace position, list reordering, window geometry, text reveal, and **anything at all while a breach is live**.

`00-client-overview.md` §7 additionally ruled out scanlines, CRT curvature, chromatic aberration, glitch, grain and typewriter reveal as non-goals. **`03-story-theme.md` §4.1 reopens that**, and does so honestly rather than quietly: it argues the ban treated a spectrum as a boolean, and replaces it with a bounded `atmosphere` setting — `off` (**the default**, and the state of every `-hc` variant), `low`, `high` — whose effects are scoped away from every surface holding data by `03` §4.2's three surface classes. Its own summary of that rule is the right one: *"if the player reads a number off it, nothing is drawn on top of it."*

From this document's side that reopening is **acceptable, with one exception noted in §6.3**, for four reasons: the default is unchanged, so no player is opted in; the effects are static paints with a measured 0 ms per-pulse cost (`03` §8), so they are not a motion question at all; `03` §9.4 measures each blinking element against the actual WCAG numbers rather than asserting safety; and `03` §9.2 adds **plain mode**, a strictly information-preserving subtraction that no other document had proposed and that a screen-reader or magnifier user genuinely needs.

So the honest statement is now: **the client ships a small, bounded, default-off effects layer, three enumerated blinking elements, and one repeating animation.** §6.3 states the test that layer must keep passing; §6.4 verifies the blinking.

### 6.2 Reduced motion, and a tri-state setting

Two mechanisms, both verified, used together (`01-visual-language.md` §7.4): the CSS media feature `@media (prefers-reduced-motion: reduce)` (verified present in the JavaFX 26 CSS Reference), and `Platform.getPreferences().reducedMotionProperty()` (JavaFX 24+) so that code-driven motion is **skipped**, not merely shortened.

`reducedMotion` is the one accessibility preference mapped on **all three** platforms (`02-platform-native-themes.md` §2.3: `accessibilityDisplayShouldReduceMotion` on macOS, `Windows.SPI.ClientAreaAnimation` inverted on Windows, `GTK.enable_animations` inverted on Linux). That is worth stating because `reducedTransparency` is *not* — which is why §6.6 exists.

**The client setting is tri-state**, matching the colour-scheme pattern in `02-platform-native-themes.md` §2.4: `follow system` (default) · `reduce` (pinned on) · `allow` (pinned off). Two intentions, not one: "match my desktop" and "I want this here regardless" are different, and collapsing them means a player who wants motion reduced in one game but not system-wide cannot have it.

Under reduction, every transition becomes an instant state change. Nothing is hidden, deferred, or replaced by a fade — the end state simply arrives (`01-visual-language.md` §7.4). The alert-indicator pulse becomes a static filled indicator.

### 6.3 The six-part test every atmospheric effect must pass

Applies to `03-story-theme.md` §4.3's catalogue today and to anything proposed later, in either family. An effect ships only if all six hold.

| # | Test | `03` §4.3's catalogue |
|---|---|---|
| 1 | **Individually controllable**, not bundled behind one switch | ⚠ **partial.** `03` §4.4 gives one `atmosphere` level (`off`/`low`/`high`) plus a separate experimental `atmosphere.aberration` toggle. A player who wants the vignette but not the scanlines cannot say so. Acceptable for v1 given the default is `off`; revisit if players ask. |
| 2 | **Off under reduced motion**, OS-signalled or pinned | ✅ for the moving ones — `03` §9.3 disables flicker and solidifies the block cursor. **Deliberately not** for the static ones, and `03`'s reasoning is right: scanlines and vignette do not move, and removing them would answer a request the player did not make. |
| 3 | **Off under `reduce effects`** (§6.5) | ✅ — §6.5 below binds `reduce effects` to `atmosphere: off`, so one switch reaches it. |
| 4 | **Never applied to a surface carrying a value** | ✅ by construction via `03` §4.2's surface classes — **except glow.** See below. |
| 5 | **Measured against §6.4's thresholds**, with the measurement recorded | ✅ — `03` §9.4 tabulates rate, luminance swing and area per element. |
| 6 | **Off by default in its first release** | ✅ — `atmosphere: off` is the default, the first-run state, and forced in every `-hc` variant. |

> **The one exception, and it is on the most important number in the game.** `03` §4.3.2 permits a 2–3px zero-offset glow on **readouts**, and `03` §2.5/§7.4 name the `rig-monitor`'s headline compute figure as *"the one node that may glow"*. A readout is a data surface, so this is `03` §4.2's own rule being carved. The carve is small and defensible — `TYPE_MONO_READOUT_LG` is 28px, glow is forbidden at `TYPE_MONO_BODY` and below, and `03` §4.3.2 already flags that a node under an `Effect` rasterises offscreen and is expected to cost sub-pixel text antialiasing (**SK-8**). But it lands on the number pillar C2 exists to protect, at the size where a filled counter turns `8` into `0`. **Position: glow on `rig-monitor`'s headline figure does not ship until SK-8 is measured on all three platforms and the greyscale and 200%-text-scale checks (A3, E1) pass with it on.** Raised as **AX-15**; it is a measurement, not an argument.

### 6.4 Photosensitivity

The verified thresholds (WCAG 2.2 SC 2.3.1 and its definitions):

- **Rate:** nothing flashes **more than three times in any one-second period**.
- **General flash threshold:** a *flash* is a pair of opposing changes in relative luminance of **≥10% of maximum relative luminance (1.0)**, where the darker state is below **0.80** relative luminance.
- **Red flash threshold:** any pair of opposing transitions **involving a saturated red**.
- **Small safe area:** the combined concurrently-flashing area is no more than **0.006 steradians** within any 10-degree visual field — approximately **25% of a 10° field**, estimated in practice as a **341 × 256 px rectangle** at 1024 × 768.

**The client's own rule is stricter than the standard in the way that matters: the area exemption is never relied on.** Every blinking element must be compliant on **rate** as well as area, so no argument about steradians can ever be the thing keeping the client safe. `03-story-theme.md` §9.4 enumerates all three current elements against exactly these numbers — flicker at ≤1 Hz and ≤0.04 luminance swing (one third the rate limit, two fifths the 10% threshold); the block cursor at 0.83 Hz over one ~8×20px cell; the switcher pulse, verified below — and adds two self-imposed rules this document endorses and restates as global: **nothing red flashes at any rate**, and **no effect is ever synchronised across windows**, because a dozen windows flickering in step converts a small-area effect into the full-screen one the steradian clause is measuring.

Two further bans, named because they are the effects a game like this reaches for by reflex:

- **No full-window or large-area luminance change on any outcome.** A red wash on `failed`, a white flash on `breached`, a screen-edge vignette on `trace-imminent` — all banned. Outcomes get outcome surfaces (`01-visual-language.md` §8, pillar C3), which is also the only presentation that satisfies "every outcome shows its cause".
- **`-es-trace-imminent` never pulses.** `#FF4136` is a saturated red, so any oscillation of it is a red-flash candidate by definition, and it is the one colour in the client that appears at the highest-stress moment. `01-visual-language.md` §2.4 already makes `imminent` a **stepped fill plus a label** — a texture change, once, not a repeating one.

**Verification of the one repeating animation that exists.** `01-visual-language.md` §7.3 permits the switcher's alert indicator, and `05-tool-windows-and-layout.md` §6.2 makes it rung 2 of the attention ladder with the same specification: a two-step opacity pulse at `DUR_SLOW`, capped at three cycles, then steady. Two steps at 240ms is a **480ms cycle ≈ 2.08 cycles/second**, under the 3/second rate threshold; the indicator is roughly `ICON_16` square, four orders of magnitude below the small-safe-area threshold; and it is an opacity change on a chrome glyph, not on saturated red. It passes on all three counts, and `05` §6.4 rule 3 already stops it entirely while a breach is live. The general rule is stated so no future indicator has to be re-argued: **no repeating animation may exceed 3 cycles per second, which puts the period floor at 334ms.**

### 6.5 The `reduce effects` switch

Separate from reduced motion, because they are different requests: reduced motion is about *movement*, this is about *rendering load and visual noise*. It is also the accommodation for vestibular sensitivity that is not movement-triggered, for low-end hardware, and for anyone who finds translucency and shadow hard to parse.

When on:

| Off with `reduce effects` | Replaced by |
|---|---|
| All surface translucency | Opaque `-es-surface-overlay` |
| Shadows at elevation levels 3–4 (`01-visual-language.md` §5.5) | `-es-border-control` boundaries |
| The switcher alert pulse | A static filled indicator |
| Meter fill interpolation | Values snap to their new position |
| The whole atmosphere layer — scanlines, vignette, grain, glow, flicker (`03-story-theme.md` §4.3) | `atmosphere: off`, which `03` §4.4 already defines as the default state |
| The `ModalPane` scrim's blur, if any is ever added | Flat scrim at full opacity |

It **never** removes: a value, a label, a boundary, a state glyph, a legend, or an accessible name.

**`reduce effects` is not plain mode, and both should exist.** `03-story-theme.md` §9.2 distinguishes them correctly: `atmosphere: off` removes *treatment*; **plain mode** additionally removes the story theme's *performance* — banners, ASCII rules inside text streams, the block cursor, brackets, prompt sigils, diegetic framing — for a player who finds decorative characters in a screen-reader stream or ASCII art in a magnifier actively obstructive. `reduce effects` is the third, family-agnostic control: it reaches translucency, shadow and interpolation in **both** families, which neither of the other two does. Three controls answering three distinct requests is correct; the failure would be one switch that half-answers all of them.

### 6.6 Transparency, and why the setting must exist

`reducedTransparency` is mapped on macOS only — ⚠ not listed for Windows in the javadoc tables read in `02-platform-native-themes.md` §2.3, and not mapped on Linux at all. `02-platform-native-themes.md` §2.3 draws the correct conclusion and it is repeated here because it is an accessibility rule rather than a theming one:

> **Translucency may never be the only way something is legible, on any platform, ever.**

`01-visual-language.md` §5.5 already confines translucency to elevation levels 3–4; that confinement is load-bearing, not stylistic. And because the OS signal is unreliable on two of three platforms, the **client setting is the primary control** and the OS signal is a default for it — the reverse of the reduced-motion arrangement, and for a stated reason.

---

## 7. Text

### 7.1 Minimum sizes

Already fixed by `01-visual-language.md` §3.4 and restated because they are accessibility floors, not typographic taste:

| Rule | Value |
|---|---|
| Absolute floor, any text, any theme, any density | **11px** (`TYPE_MICRO`) |
| 11px is permitted only for | Dense table metadata that is **never the sole carrier of a value** |
| Default body | **14px / 20px line box** (`TYPE_BODY`) |
| Secondary metadata | **12px** (`TYPE_CAPTION`) |
| Density may never | Reduce a font size (`01-visual-language.md` §4.4) |

### 7.2 Text scaling, independent of OS scaling

> **A client-owned text scale, 80%–200%, applied to the type scale only.**

| Steps | 80 · 90 · **100** · 115 · 130 · 150 · 175 · 200 % |
|---|---|
| Binding | `Shortcut+=` / `Shortcut+-`; `text-scale 150` and `text-scale reset` in the command palette |
| Scope | Application-wide, all windows, both layout arrangements, both theme families |
| Persistence | Per profile, client-owned state (`00-client-overview.md` §4.5) |
| Mechanism | The type scale is Java constants (`01-visual-language.md` §1.3), so the multiplier is applied once at token resolution and emitted into the generated stylesheet. **One place.** |
| Rounding | Round to whole px. **Never below 11px after scaling** — at scales under 100%, `TYPE_MICRO` and `TYPE_CAPTION` clamp rather than shrink, so a density preference cannot become an accessibility problem. |
| Live | Applies immediately, no restart, no `Scene` rebuild — same constraint as the theme switch (`02-platform-native-themes.md` §2.6) |

**Why independent of OS scaling**, which is the obvious question: OS display scaling scales *everything*, including window chrome we do not own, and it is usually a per-display setting shared with every other application. A player may reasonably want a normal-size desktop and a large game — especially this game, which `00-client-overview.md` §3.1 explicitly expects to be run on a second monitor beside real work. HiDPI output scale (`02-platform-native-themes.md` §3.8, §4.8) is a third, orthogonal thing. All three compose; none substitutes for the others.

200% satisfies SC 1.4.4 without a browser.

### 7.3 Line and character spacing

**Line spacing.** JavaFX has no `line-height`; `-fx-line-spacing` is *extra space between lines*, not the line box (`01-visual-language.md` §3.3). Verified against the JavaFX 26 CSS Reference: `-fx-line-spacing` is documented on `Text` and `TextFlow`. ⚠ **unverified** whether it is settable from CSS on `Labeled` — `Labeled` exposes a `lineSpacing` property in the Java API, and the token layer should set it programmatically rather than assume the CSS route.

The type scale's prose line boxes sit slightly under SC 1.4.12's 1.5× recommendation: `TYPE_BODY` 14/20 = **1.43×**, `TYPE_CAPTION` 12/16 = **1.33×** (`TYPE_MONO_BODY` 13/20 = 1.54× already clears it). So a setting is required rather than optional:

> **`reading spacing`** — off by default. When on, prose surfaces raise to **≥1.5× line box** (body 14/21, caption 12/18) and paragraph spacing to **≥2× font size**.

**Scoped to prose surfaces only** — the `recon` reader, teaching-layer popovers, error messages, empty states, help text. It does **not** apply to tabular readouts, ledger rows, log lines or gauges, because SC 1.4.12's intent is readability of running text, and expanding a numeric column's leading would break the column comparison that `01-visual-language.md` §4.6 exists to protect. Helping a reader by harming the same reader's numbers is not compliance.

**Letter and word spacing cannot be shipped.** Verified: the JavaFX 26 CSS Reference documents no letter-spacing or word-spacing property, and none exists on `Text`. SC 1.4.12's 0.12em letter / 0.16em word requirements are therefore **not met and cannot be met** without per-glyph layout, which would break selection, copy, and screen-reader text. Stated plainly rather than quietly skipped. Note that `01-visual-language.md` §9.2 also proposes letter-spacing as the story theme's substitute for uppercase — that proposal needs the same mechanism and inherits the same problem. **AX-4**.

### 7.4 No text in images

- Icons are `SVGPath` string constants (`01-visual-language.md` §6.2) and contain no glyphs.
- **No bitmap shipped in the client contains text.** Checkable by asset scan (checklist E3), and easy to keep true because the client ships almost no bitmaps.
- **The theme chooser and the theme switcher preview must be live rendered controls, not screenshots.** `00-client-overview.md` §4.2 asks the first-run chooser to show both themes "on a real rig-monitor screenshot rather than a swatch" and the switcher to show "a live-updating preview strip". This document makes the first literal too: a raster screenshot would carry text that does not scale, cannot be read by a screen reader, and would not honour the text-scale setting the player may be about to need in order to read it. Live miniature windows satisfy §4.2's intent — *the thing being chosen is legibility, not colour* — better than a screenshot does.
- The same rule governs the layout chooser in §2.4.

### 7.5 Reflow: how scaled text stays readable in dense readouts

At 200% scale a compute gauge's readout is 56px tall and a ledger row's five columns no longer fit. Six rules, in priority order when they conflict:

1. **No fixed-width text container, anywhere.** Every label and value sizes from its content. A hard-coded pixel width is the single most common cause of clipping at scale, and it is greppable.
2. **A number never truncates and never ellipsises.** If a value cannot fit, the row **wraps** — label above value — before the value is clipped. An ellipsised number is a wrong number.
3. **Only identifiers truncate**, always middle-truncated, always with the full value in `accessibleText`, in the tooltip, and on copy (`01-visual-language.md` §9.3, §8.5).
4. **Minimum window size scales with text scale.** `05-tool-windows-and-layout.md` §2.1 fixes a per-window minimum and §5.2 fixes the dock's (`1024×640`, floor `800×600`), enforced with `Stage.setMinWidth` / `setMinHeight`. Those numbers were derived against 100% text scale, so they are a **function of scale, not constants**: each window declares its minimum in character-width terms and recomputes on scale change. If the window is smaller than the new minimum it grows; if the display cannot hold it, the window's content switches to a **stacked single-column** arrangement rather than clipping — which is the same degradation strategy `05` §5.6 already specifies for the dock's map column, applied at window level. `05` **WL-11** raises the same problem from the localisation side; it is one problem with one answer.
5. **Never two-axis scrolling in one readout** (the SC 1.4.10 analogue). A panel scrolls vertically or horizontally, never both. Data tables are the exception WCAG itself grants — and a horizontally scrolled table **pins its first column**, so a row never loses its identity.
6. **Above 130% text scale, a window forces comfortable density.** Compact plus large text is precisely the combination that clips. It is automatic, stated inline once (`compact density off at this text size`), and overridable by the player — an automatic behaviour that cannot be overridden is a new barrier, not a fix.

---

## 8. Time pressure

The hardest section, and the one where an honest answer is worth more than a compliant one.

### 8.1 What the clocks are

| Clock | What it does | Source |
|---|---|---|
| **Trace** | Accrues on the defender side during a breach; completing first *is* the failure state | `../design/05-hacking-minigame.md` §4 |
| **Backlog timer** | Per-item response window for bot alerts; **shrinks as bot count rises** | `../design/10-botnets.md` §1 |
| **Split attention** | Not a clock — a parallel performance penalty applied to every simultaneous engagement | `../design/10-botnets.md` §1 |
| **Yield buffer fill** | A *waiting* clock: payout scales with fullness, capped at 4 hours | `../design/04-mining.md` §5.1 |
| **Compute recovery** | Thermal Budget curve; not adversarial, but it gates what you can do next | `../design/01-core-resources.md` §1.3 |
| **Noise decay** | Exponential, ~90s half-life in-engagement | `../design/01-core-resources.md` §3.1 |
| **Sweep probability** | Per-hour probability, not a timer | `../design/04-mining.md` §4 |

### 8.2 Why "no time limits" is not available, and why that is not the end of it

SC 2.2.1 has an **Essential** exception: the time limit is essential and extending it would invalidate the activity. The trace timer qualifies unambiguously — it *is* the failure condition. Remove it and `../design/05-hacking-minigame.md` §2's contract loses `outcome`, since `breached` and `failed` collapse into one state, and every downstream system that reads `resolutionRecord` (proof-of-skill gates, bot-salvage guards) loses its meaning.

So we claim the exception. And then:

> **An exception is a licence to stop, not a reason to.** The Essential clause says we are not obliged to remove the limit. It says nothing about whether we may make it adjustable, and adjustable is both possible and better.

The design already agrees, in writing. `../design/05-hacking-minigame.md` §6 **P-2** — logged as an open question in `../design/15-open-questions.md` §2 — reads: *"Real-time trace timer vs. turn/probe-budget. Turn-based is more accessible and arguably truer to Pillar 1 — strong candidate to switch."* And `../design/00-vision-and-pillars.md` §7 says the skill expression is "planning, reading, and triage", not reflexes. The accessibility answer and the design's own stated direction point the same way, which is a good sign it is the right one.

### 8.3 The pace scalar

> **One setting, `pace`, multiplying every clock inside an engagement: 100% (default) · 125% · 150% · 200% · 300%.**

**What it scales:** trace accrual rate; the backlog response window; any timed input window inside a Timing-class layer; in-engagement noise decay; any in-engagement cooldown.

**What it does not scale:** the *magnitude* of anything. A Fuzzer probe still adds exactly the trace it always added, and the attribution segments in `es-gauge-trace` still read the same (`01-visual-language.md` §2.2.5). Only the clock stretches. This is what keeps pillar C3 intact: at 200% pace the player still loses because they were too loud, and the meter still says so in the same numbers.

> **The rule that keeps it balanced: pace stretches time uniformly inside an engagement; it never changes a rate ratio.** If trace slowed but noise decay did not, a slower pace would be a *strategic advantage* — more decay per unit of trace — and the setting would become a difficulty exploit rather than an accommodation. Scaling every in-engagement clock by the same factor leaves every decision identically shaped and only gives the player more wall-clock seconds to make it.

**Where the seam is.** An engagement is a bounded, instanced thing with its own contract (`../design/05-hacking-minigame.md` §2), so it can carry its own clock without leaking. **Economy-facing clocks are untouched**: mining yield rate, offline buffer accrual, heat decay, compute recovery between engagements, sweep probability per hour. Otherwise pace would become an income multiplier, and I1/I2's economy would be adjustable from the accessibility menu.

**Where it is enforced: the server.** Per I14 and pillar C4, the client sends the preference and the server owns the engagement clock. A client-side pace scalar is a cheat vector with a friendly name.

**Adversarial multiplayer.** `../design/13-multiplayer-and-federation-play.md` opt-in real-loss play makes this genuinely delicate. Three options were considered:

| Option | Verdict |
|---|---|
| Forbid non-100% pace in adversarial engagements | **Rejected.** It means "players who need an accommodation may not play the real game", which is the exact outcome this document exists to prevent. |
| Allow, and disclose the setting to other participants | **Rejected.** Disclosure of a pace setting is disclosure of a disability to a stranger in an adversarial context. Not acceptable. |
| **In a symmetric player-vs-player engagement, the server applies the *slower* of the participants' pace settings to both.** | **Proposed.** Nobody is disadvantaged, nobody discloses anything, and the only cost is that a 100% player occasionally plays a slower duel — which is a smaller cost than excluding players. In asymmetric engagements (player vs. node), pace is simply the player's own. |

That third option needs the multiplayer designer's sign-off and a decision about which server owns the clock in a cross-server engagement (`../architecture/05-validator-quorum.md`). **AX-6**.

### 8.4 The turn / probe-budget mode

`../design/05-hacking-minigame.md` §6 **P-2** already proposes this as a possible *global* design change. This document proposes it additionally as a **per-player mode**, because it is the accommodation that helps players a pace scalar cannot: someone whose input latency is unpredictable (switch access, eye tracking, an on-screen keyboard, intermittent tremor) is not helped by a longer clock — they are helped by there being no clock.

**How it works:** trace accrues **per probe** rather than per second. Difficulty's time-pressure knob (`../design/05-hacking-minigame.md` §3.3) maps to **probe budget** instead of timer speed. Everything else is unchanged.

**Why this is within what the minigame proposal permits, rather than a violation of it:** `../design/05-hacking-minigame.md` §2 states the economy-facing contract is the stable API and §3's content may change freely. Turn mode produces exactly the same `outcome`, `noiseGenerated`, `traceProgress`, `loot`/`consequence` and `resolutionRecord`. Nothing downstream — proof-of-skill (`../design/02-unlock-gates.md`), bot-salvage guards (`../design/10-botnets.md`) — can tell the difference, which is the test §2 sets.

**The one place it is not a free translation:** the **Timing** puzzle class is *about* a race or timing window (`../design/05-hacking-minigame.md` §3.1). In turn mode it must be re-expressed as a **sequencing/offset** puzzle — choosing *when* in an ordered sequence to act, rather than *when* in wall-clock time. That is genuine puzzle design and it belongs to `../design/05-hacking-minigame.md`. It is also arguably a better puzzle: `../design/00-vision-and-pillars.md` §7 says the skill is planning, and a reflex window is the one place the current proposal contradicts that.

**And a scope note:** if P-2 resolves toward turn-based *globally*, most of §8.3 becomes unnecessary. That would be a good outcome and the pace scalar should not be built in a way that makes it hard to delete. **AX-7**.

### 8.5 The backlog timer specifically

`../design/10-botnets.md` §1 makes the backlog window shrink with bot count *deliberately* — it is one of the five costs that make a botnet read as chosen overextension (§4 of that doc). The accommodation must stretch the axis without flattening the gradient:

```
window = base_window × pace ÷ f(botCount)
```

Pace multiplies; bot count still divides. A player at 200% pace running six bots still feels the six-bot squeeze, at exactly the same *shape*, with twice the seconds. The design pressure survives; the dexterity tax does not.

**Split attention** (`../design/10-botnets.md` §1) is a performance penalty, not a clock, so pace does not touch it. Whether it should be accessibility-scalable is a balance question owned by `10` — **AX-9**.

### 8.6 The abort escape hatch

`../design/05-hacking-minigame.md` §2 and §4 make `aborted` a real, persisted outcome — "the escape hatch when a read goes bad" — with real costs: noise already spent, no proof-of-skill credit. `01-visual-language.md` §2.2.7 deliberately colours it **neutral, not bad**, because "a UI that paints it red teaches players not to use the tool the design gave them."

An escape hatch that is hard to reach is not an escape hatch. Six rules:

1. **Reachable from every window**, not only `terminal`. The read goes bad while you are looking at the map as often as not. `Shortcut+.` is global during a live engagement (`00-client-overview.md` §6.3).
2. **One action.** One keystroke, or one click on a control that is **always visible** on the engagement surface: never behind a menu, never requiring a scroll, never occluded by a popover or a pane divider (SC 2.4.11).
3. **No hold, no double-click, no drag.** §3.2's rules apply here first.
4. **The confirmation is single, focused and keyboard-native.** It is a `ModalPane` in the affected window — never `APPLICATION_MODAL`, which `01-visual-language.md` §5.3 bans outright because it would block the rig monitor. Focus lands on the confirm; `Enter` confirms; `Escape` cancels. **`Escape` never aborts** — a destructive misfire on the universal dismiss key is exactly the accident this rule prevents.
5. **The confirmation states the consequence** — noise already spent, no proof-of-skill credit, what is kept — so the escape hatch is an informed choice (pillar C3), not a leap.
6. **The confirmation holds the engagement clock.**

Rule 6 is the one with teeth, so it gets its own statement:

> **Opening the abort confirmation pauses the engagement clock, once per engagement, for up to 30 seconds. The pause is server-owned. If the cap elapses, the engagement resumes — it does not auto-abort.**

Without it, reaching the escape hatch costs the thing you are escaping, and costs it most for the player slowest to reach it. The once-per-engagement cap and the 30-second ceiling stop it becoming a thinking-time exploit, and in a multi-participant engagement every participant sees `clock held` — the pause is visible, not secret, so it cannot be used as a hidden advantage.

### 8.7 Sessions never expire under the player

If the client's connection degrades mid-engagement, the server must **not** resolve the engagement as `failed` purely on client silence within a defined grace window, and the client must be able to **reconnect into a live engagement**. Otherwise a player using an assistive technology that steals focus, a slow input method, or a flaky link loses breaches to latency rather than to play — which reads as "the dice hated me", the exact failure `../design/00-vision-and-pillars.md` §5 forbids. Grace-window length and reconnection semantics are server design and belong with `../architecture/03-server-and-federation.md`; the requirement is stated here because this is where it is load-bearing.

Related and simpler: **no notice, banner, toast or dialog in this client auto-dismisses on a timer.** Everything persists until acted on (SC 2.2.3, partially adopted — §1.1).

---

## 9. Audio

`00-client-overview.md` **CL-7** leaves audio undecided and out of scope for the client doc set, correctly noting that a surveillance-dystopia client with no sound design leaves Pillar 4's escalation on the table, and that an alert sound is one of the few ways to serve pillar C5's triage problem without stealing focus.

So this section is a **forward contract**: what any future audio design must satisfy. Today's compliance is trivially true — there is no audio — and the value here is that the rules exist before the first sound is added, which is the only time they are cheap.

### 9.1 Nothing is conveyed by sound alone

> **Every audible event has a visual carrier that is *already* primary. Audio is a redundant channel, never a unique one.**

Every event on `05-tool-windows-and-layout.md` §6.2's attention ladder already has a visual home: the alert-tray row, the `switcher` entry's `es-state-alerting`, the window's own indicator, the log line. A sound is a fifth expression of a fact four surfaces already carry. Nothing may ever be audible-only — not a canary trip, not a sweep, not a trace threshold, not a bot alert.

This also happens to be the correct design for CL-7's own stated purpose: a redundant channel is what serves triage without interruption, and a unique channel would be interruption by another name.

### 9.2 Recovered narrative content

If a story artefact is ever audio — an intercepted call, a voice memo in an Eye archive (`../design/14-world-and-narrative.md` §3) — then:

> **The transcript is the canonical artefact and the audio is the redundant layer**, not the other way round.

This is a gameplay requirement before it is an accessibility one. `../design/05-hacking-minigame.md` §3.2 makes human-read recovered material a *dependency* of breach steps — the Traversal class hides the objective among decoys "distinguishable only by cross-referencing recovered logs". An artefact a player cannot read is not an accessibility inconvenience; it is a locked door on the critical path. Transcripts are also searchable, copyable and quotable, which is what `01-visual-language.md` §8.7 already requires of every log line.

### 9.3 Controls

| Requirement | Detail |
|---|---|
| Independent channels | **alerts · interface · ambience · voice**, each 0–100 with an independent mute |
| Defaults | alerts on, interface low, ambience low, voice on |
| SC 1.4.2 | Any sound over 3 seconds is pausable and stoppable **independently of system volume**. Ambience is the case this exists for. |
| System mute | Honoured. The client never plays through a route the player did not choose, and never ducks other applications — `00-client-overview.md` §3.1 expects this game to run beside real work. |
| Spatial audio | May exist as flavour; **never carries unique information**. Direction is not a channel. |
| Speech | No voice acting is planned (`../design/00-vision-and-pillars.md` §3, Pillar 5: no companion characters). If any ships, captions are mandatory, styled with the same tokens, at the same contrast floor. |

### 9.4 Visual equivalents for alerts

A "visual alerts" setting is the standard accommodation and the standard implementation of it — a screen flash — is banned by §6.4. The permitted form:

- The `switcher` entry and the window indicator enter `es-state-alerting` (a **static** filled indicator under reduced motion, or the ≤3-cycle pulse verified in §6.4 otherwise).
- The alert tray gains a row (`05` §6.7), and its unacknowledged count is part of the `switcher`'s accessible name.
- Optionally, a **brief, non-flashing** emphasis on the target window's own header — one state change, not a repetition.
- Never a full-screen flash, never a screen-edge vignette, never a colour wash. §6.4 applies to accessibility features exactly as it applies to atmosphere.

---

## 10. The reviewer's checklist

Written to be executed against a build, not read. **⚙** = automatable, belongs in the client module's test sources and should fail `mvn verify`. **◻** = manual, per platform, per release.

Legend for "Pass": what a reviewer records. A checklist item with a subjective pass criterion is not a checklist item.

### A. Colour and contrast

| # | | Check | How | Pass |
|---|---|---|---|---|
| A1 | ⚙ | Every colour token meets its floor against every surface it can sit on, in every shipped variant | The harness in `01-visual-language.md` §2.3 extended per `02-platform-native-themes.md` §2.5 | 4.5:1 text, 3:1 non-text; build fails otherwise |
| A2 | ⚙ | Adjacent gauge fill segments either clear 3:1 mutually or are separated by the 2px track gap | Enumerate segment pairs per `es-gauge` variant; measure or assert the gap | Every pair satisfies one of the two (§5.4) |
| A3 | ◻ | Greyscale test | Capture every catalogue window × each variant × each density, populated; desaturate luminance-preserving | Every row of `01-visual-language.md` §2.4 still distinguishable |
| A4 | ◻ | CVD simulation — protanopia, deuteranopia, tritanopia | Same captures through a simulator; focus on heat chip, faction marks, provenance shields, ledger rows, compute gauge | Every state identifiable **without** relying on hue |
| A5 | ⚙ | Redundant encoding present | Scene-graph walk: every node with an `es-state-*` class has a text node or a named glyph in its subtree | No violations (§5.2) |
| A6 | ⚙ | Focus ring contrast | Measure `-es-focus-ring` against control fill and surface, in all 7 native variants + `uos` + `uos-hc` + high contrast | ≥3:1 both sides; ≥4.5:1 in high contrast |
| A7 | ⚙ | `-es-fg-tertiary` carries no information | Assert no node styled tertiary is a value, label of a value, or sole state carrier | No violations |
| A8 | ◻ | High contrast reachable without an OS signal | Enable from Settings on macOS and Linux | Treatment applies; 7:1 text measured |
| A9 | ◻ | Heat chip band word and 5-pip indicator present | Every heat chip, both densities, both families, with `always show state text` off | Never absent (§5.3a) |

### B. Keyboard

| # | | Check | How | Pass |
|---|---|---|---|---|
| B1 | ◻ | **Full breach, pointer unplugged** | Physically disconnect the pointer. Open a target, run recon, breach, then abort — once in multi-window, once docked | Completed both times, no step impossible |
| B2 | ◻ | No keyboard trap | From every focusable node in every catalogue window: Tab, Shift+Tab, Escape, F6 | Focus always escapes; terminal exits via F6 |
| B3 | ◻ | Every action in `00-client-overview.md` §6.3 fires | Per platform | All fire; none swallowed by the OS |
| B4 | ◻ | Attribution reachable without `Alt` | `Shortcut+Shift+A` on all three platforms (`02-platform-native-themes.md` §6.3) | Overlay appears |
| B5 | ◻ | Focus visible everywhere, never suppressed for pointer users | Tab through each window after clicking with the mouse | Ring visible on every focusable node |
| B6 | ◻ | Traversal order matches visual reading order | Tab through every catalogue window, both densities | Order matches; no surprises |
| B7 | ◻ | Every drag has a non-drag equivalent | Map pan, socket-into-bot, split resize, switcher reorder, range select | All achievable by keyboard (§3.7) |
| B8 | ⚙ | No unmodified single-character global shortcut | Assert over the binding registry | None (SC 2.1.4) |
| B9 | ◻ | Raise moves focus and announces | `Shortcut+1…9`, `Shortcut+0`, cross-window links, with a screen reader running | Focus lands on the entry node; window name announced |
| B10 | ◻ | Abort reachable in one action from every window during a live engagement | Press `Shortcut+.` from each | Confirmation appears every time (§8.6) |
| B11 | ⚙ | Target size | Measure every actionable node at both densities and at 80/100/200% text scale | ≥24×24, or the spacing exception satisfied (§3.8) |
| B12 | ◻ | Terminal exit is discoverable | Fresh profile, focus the terminal | Hint shown once, states `tab completes · F6 leaves this pane` |
| B13 | ◻ | Remap and conflict reporting | Rebind an action onto an occupied combination | Conflict reported inline naming both claimants; `Shortcut+0` refuses rebinding |

### C. Screen readers

| # | | Check | How | Pass |
|---|---|---|---|---|
| C1 | ◻ | Full session with Narrator, NVDA, VoiceOver | One complete loop: sign in, read the rig monitor, deploy a miner, run a breach, read the ledger | Every step completable; every value spoken |
| C2 | ◻ | Linux state re-verified | Orca against a JavaFX 26 build on GNOME | Result recorded; system requirements updated to match (**AX-1**) |
| C3 | ⚙ | Every icon-only control names itself | Scene-graph walk | `accessibleText` non-empty and equal to the tooltip |
| C4 | ⚙ | Primitive composition | Assert the composed `accessibleText` of each primitive against §4.4 | Matches the specified order and content |
| C5 | ⚙ | Truncated identifiers expose full values | Assert on every middle-truncated node | Accessible text contains the untruncated string |
| C6 | ⚙ | `unknown` speaks as a word | Assert on `es-state-unknown` nodes | Text contains `unknown`; never only `—` |
| C7 | ◻ | Announcement path | Fire an urgent event with each screen reader, `announce = speak`, focus elsewhere | Record announced / not announced per reader (**AX-3**) |
| C8 | ◻ | The tray is sufficient without announcements | Set `announce = tray`; run a breach with alerts firing; use `Shortcut+Shift+N` only | Every rung-3/4 event found with its cause and deadline; rung-0/2 events retrievable in `recent` (§4.5) |
| C9 | ◻ | Gauge announces one sentence | Focus the compute gauge | One composed sentence; segments only on entry |
| C10 | ◻ | No focus theft | Run a breach while canary trips and bot alerts fire, at every announce level | Focus never leaves the player's window; no window opens itself; no `ModalPane` appears from a server event (`05` §6.3) |
| C11 | ◻ | Map table view | `Shortcut+Shift+L`, keyboard only | Every node reachable with kind, hop distance, knowledge state |
| C12 | ⚙ | No bare `Tooltip`, and none times out | ArchUnit: constructing `javafx.scene.control.Tooltip` outside the one factory is a failure; the factory sets `showDuration` to `Duration.INDEFINITE` | No violations; no tooltip disappears while being read (§3.6) |
| C13 | ⚙ | Decoration never reaches the accessibility tree | Scene-graph walk over `03` §5.1's decoration classes — box-drawing runs, brackets, banners, prompt sigils, block cursors | All have empty accessible text and `focusTraversable = false` (`03` §9.5) |
| C14 | ◻ | Everything is selectable, copyable text | Select and copy from a log well, a table cell, a gloss bar and a `man` page | Real text in reading order everywhere (§4.2 item 3b) |

### D. Motion and photosensitivity

| # | | Check | How | Pass |
|---|---|---|---|---|
| D1 | ◻ | OS reduced-motion honoured in both families | Toggle the OS setting on each platform | All transitions instant; pulse static |
| D2 | ◻ | Tri-state setting overrides in both directions | Pin `reduce` with the OS off, and `allow` with the OS on | Client setting wins both ways |
| D3 | ⚙/◻ | Nothing exceeds 3 cycles/second | Enumerate repeating animations; measure period | Every period ≥334ms; record the alert pulse's measured value |
| D4 | ◻ | No numeric readout animates | Force large value changes on compute, EC, trace, noise | Values snap (`01-visual-language.md` §7.3) |
| D5 | ◻ | Trace is linear and never pulses | Observe a full breach into the imminent band | Constant rate; imminent is a stepped fill plus a label, no oscillation |
| D6 | ◻ | `reduce effects` removes the listed set | Enable it | Translucency, shadows, pulse, interpolation gone; no information lost (§6.5) |
| D7 | ◻ | No large-area luminance change on outcomes | Trigger `breached`, `failed`, `aborted`, a sweep, a canary trip | No full-window flash, wash or vignette |
| D8 | ◻ | Atmosphere is off by default and forced off where required | Fresh profile; then each `-hc` variant; then OS reduced-motion; then a live breach | `atmosphere` reads `off` in all four (`03` §4.4) |
| D9 | ◻ | Atmosphere touches no data surface | Set `atmosphere: high`; inspect every `es-gauge`, `es-stat`, `es-ledger-row`, `es-log-line`, `es-item-card`, `es-node`, `es-chip`, `es-gate-badge`, `es-authority` | No treatment on any of them (`03` §4.2) — **except glow on the `rig-monitor` headline, which must be off pending AX-15** |
| D10 | ◻ | Blink rates measured, not assumed | Capture flicker, block cursor and switcher pulse; measure rate, luminance swing and area | Each within `03` §9.4's table; nothing red or full-screen blinks at any rate |
| D11 | ◻ | No effect is synchronised across windows | Open every window at `atmosphere: high`; observe | Phases independent (`03` §9.4) |

### E. Text

| # | | Check | How | Pass |
|---|---|---|---|---|
| E1 | ◻ | 200% text scale at minimum window size | Every catalogue window at its `05` §2.1 minimum, both densities, both families, populated | No clipped glyph; no two-axis scroll in any readout |
| E2 | ⚙ | Floor holds when scaling down | Set 80%; assert resolved sizes | Nothing below 11px |
| E3 | ⚙ | No glyphs in bitmaps | Asset scan of the client jar | No bitmap contains text |
| E4 | ◻ | Choosers are live, not screenshots | Open the theme chooser and the layout chooser at 200% scale with a screen reader | Text scales and is announced (§7.4) |
| E5 | ◻ | Numbers never ellipsise | Narrow every window to its minimum at 200% | Rows wrap; no truncated number |
| E6 | ◻ | `reading spacing` reaches ≥1.5× on prose | Enable; measure the `recon` reader and a teaching popover | ≥1.5× line box; tabular surfaces unchanged |
| E7 | ◻ | Density auto-switch above 130% | Set 150% on a compact window | Comfortable forced, stated inline once, overridable |
| E8 | ◻ | Plain mode changes no information | Enable plain mode (`03` §9.2); diff every window against plain mode off | No value, label, unit or precision differs |

### F. Time pressure

| # | | Check | How | Pass |
|---|---|---|---|---|
| F1 | ⚙ | Pace reaches every in-engagement clock and no economy clock | Server test at 100% and 300% | Trace, backlog, timing windows, in-engagement decay all scale; mining yield, buffer accrual, heat decay, compute recovery do not |
| F2 | ⚙ | Pace preserves rate ratios | Regression: trace accrued per unit of noise decayed | Identical at every pace value (§8.3) |
| F3 | ⚙ | Abort confirmation holds the clock | Server test | Held once per engagement, ≤30s, resumes on expiry, visible to all participants |
| F4 | ◻ | Abort needs no hold, double-click or drag | Try each input method, including sticky keys | Single press suffices |
| F5 | ⚙ | Turn mode preserves the contract | Resolve an engagement in both modes | Identical `resolutionRecord` shape; downstream systems cannot distinguish |
| F6 | ◻ | Reconnect into a live engagement | Kill the connection mid-breach; restore within the grace window | Engagement resumes; not resolved as `failed` on silence |
| F7 | ◻ | Nothing auto-dismisses | Leave every notice and confirmation untouched for 5 minutes | All still present |

### G. Layout modes

| # | | Check | How | Pass |
|---|---|---|---|---|
| G1 | ⚙ | Every tool exists in both modes | `05` §5.4 rule 1: `assertEquals(registry.ids(), dockHostableIds())`; plus assert no pane type references an `isDocked()`-shaped API | Identical id sets; `DockContext` exposes width, height and density only |
| G2 | ◻ | Mode toggle preserves state | `Shortcut+Shift+D` mid-breach with a scrolled log, a text selection, and unsent input | All preserved (D-5) |
| G3 | ◻ | Docked usable at its minimum with large text | Set the dock to `05` §5.2's minimum 1024×640 at 150% text scale, then to the 800×600 floor at 100% | No two-axis scroll in any readout; every pane clears its minimum or degrades per `05` §5.6; rig strip never occluded (D-6) |
| G4 | ◻ | Discoverable four ways | Fresh profile: first run, the fourth-window notice, the palette, Settings | All four present; none labelled as an accessibility fallback (§2.4) |
| G5 | ◻ | Offer never applies | Trigger every heuristic in §2.4 route 4 | Layout unchanged until the player chooses |
| G6 | ◻ | **Magnifier test** | macOS Zoom / Windows Magnifier at 400%: complete a breach in docked mode | Completable; compute, trace and the target are simultaneously reachable (`05` §5.5) |
| G7 | ◻ | Rig strip never occluded | Open every popover, confirmation, rung-4 banner and `ModalPane` in docked mode | Rig strip fully visible in all cases; no banner reflows content (D-1, `05` §6.2) |
| G8 | ◻ | `layout gather` recovers scattered windows | Move windows off-screen / to another display, then run it | All windows on the focused display, tools unchanged |

### H. Audio (when audio exists)

| # | | Check | How | Pass |
|---|---|---|---|---|
| H1 | ◻ | Nothing sound-only | Mute everything; run a full session | No information lost |
| H2 | ◻ | Independent channels and mutes; system mute honoured | Toggle each | Each independent; system mute silences all |
| H3 | ◻ | Every audio artefact has a canonical transcript | Enumerate narrative audio | Transcript present, searchable, copyable |

### I. Per platform

| # | | Check | How | Pass |
|---|---|---|---|---|
| I1 | ◻ | Run A–H on macOS, Windows and Linux | Platform specifics in `02-platform-native-themes.md` §7 | Results recorded per platform, per release, with dates |

---

## 11. Open questions

Deliberately undecided here. Prefix **`AX-`** chosen to avoid collision with `CL-` (`00-client-overview.md`), `V-` (`01-visual-language.md`), `PN-` (`02-platform-native-themes.md`), `SK-` (`03-story-theme.md`), `T-` (`04-terminology-and-education.md`), `WL-` (`05-tool-windows-and-layout.md`), `RI-` (`06-resource-and-inventory-ui.md`) and the existing prefixes in `../design/15-open-questions.md`. Log there in §2 if this doc set is adopted.

- **AX-1: Is JavaFX still unable to talk to AT-SPI/Orca on Linux?** Verified as far as public sources allow — Oracle's 2015 statement on `orca-list` (*"we currently support Windows and Mac platforms. We have no plan to make FX accessible on Linux"*) plus 2022–2023 `openjfx-dev` and `orca-list` discussion, and an OpenJFX wiki page still titled "Accessibility Exploration". **Re-test against JavaFX 26 on a current GNOME before any statement reaches a player.** If it confirms, the consequence is that a blind player cannot play on Linux, and that belongs in the system requirements, in words, before install. Shipping-blocker-shaped.
- **AX-2: Is a client-side self-voicing layer worth building?** It would make AX-1 moot, and would also cover any screen reader that handles our custom primitives badly. Against: an external TTS dependency, a large surface to maintain, and a direct collision with `00-client-overview.md` §7's security boundary (no subprocess spawning). Decide only after AX-1 and AX-3 have data.
- **AX-3: Does `notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT)` on an unfocused node get announced?** Per screen reader, per platform. This determines whether SC 4.1.3 is meetable at all with this toolkit. The alert tray (§4.5 Path A, on `05` §6.7's surface) is designed so the answer does not gate accessibility — but the answer determines whether `announce = speak` can exist.
- **AX-4: Letter and word spacing.** Verified: JavaFX CSS has no property for either. SC 1.4.12's 0.12em/0.16em requirements are therefore unmeetable without per-glyph layout, which would break selection, copy and accessible text. Also blocks `01-visual-language.md` §9.2's proposal to use letter-spacing as the story theme's substitute for uppercase. Someone should check whether a bundled font variant with wider default tracking is an acceptable answer for the story theme, and accept the gap for the general case.
- **AX-5: Should the palette move so adjacent meter fills clear 3:1 mutually?** §5.4 computes 1.64:1 (`uos` compute), 1.72:1 (native light compute) and 1.37:1 (`uos` trace) and specifies a structural fix — a track-coloured gap plus mandatory per-role texture — that this document owns. Whether the hexes also change is owned by `01-visual-language.md` **V-2** and `02-platform-native-themes.md` **PN-2**, and should be decided when the generated per-theme palette lands.
- **AX-6: Pace scaling in adversarial federated play.** §8.3 proposes the slower-of-two-participants rule, for privacy reasons as much as fairness ones. Needs the multiplayer designer (`../design/13-multiplayer-and-federation-play.md`) and a decision about which server owns the clock in a cross-server engagement (`../architecture/05-validator-quorum.md`). Decide before the first adversarial engagement is implemented, not after.
- **AX-7: If `../design/05-hacking-minigame.md` P-2 resolves to turn-based globally, does the pace scalar still need to exist?** Probably not for the trace, still yes for the backlog timer (`../design/10-botnets.md` §1) which is wall-clock regardless. Related: the **Timing** puzzle class needs a non-wall-clock expression before turn mode ships as a player-facing option (§8.4), and the **Traversal** class may need a non-spatial expression for the map's table view (§4.6). Both are puzzle design owned by `../design/05-hacking-minigame.md`.
- **AX-8: Can the client detect an active screen magnifier or screen reader, portably, to *offer* the docked layout?** It would be a far better signal than display size. Probably unavailable without platform-specific native calls, and it sits awkwardly against the closed read-list in `02-platform-native-themes.md` §2.9. Ask the privacy question and the feasibility question together.
- **AX-9: Should the split-attention penalty (`../design/10-botnets.md` §1) be accessibility-scalable?** It is a performance penalty, not a clock, so the pace scalar does not reach it — and `10` §1 already warns that two penalties on the same axis could tip bots from "tense" into "not worth running", with a stated resolution order (soften split attention first). A balance value owned by `10`, raised here because it is the remaining un-accommodated pressure source.
- **AX-10: Can a pane be popped out of docked mode into its own `Stage`?** The hybrid — a dock plus two windows on a second monitor — is what a magnifier user with two displays and a player with one small laptop screen would both want, and it is the arrangement between the two endpoints that neither document currently offers. `05-tool-windows-and-layout.md` §3.1 defers the related `openNew()` question (**WL-8**) because a second instance of the same tool needs a second pane instance, and §5.4's one-factory-per-id rule is what makes mode equivalence checkable — a pop-out that moved the *existing* instance would not violate either, which suggests this is cheaper than it looks. Decide with **WL-8** and **WL-12**, not separately.
- **AX-11: Accessible authentication we do not control.** Sign-in is AT Proto OAuth in the provider's own flow (`../architecture/02-identity-and-auth.md`). We can guarantee our side of SC 3.3.8 — no cognitive-function test, paste never disabled — and nothing about theirs. What is the project's position when a provider's flow is inaccessible? At minimum: document which providers were tested.
- **AX-12: Who owns the accessibility statement, and what shape does it take?** §1.3 declines a conformance claim and offers §10's checklist instead. Whether that becomes a published document, a VPAT-shaped report, or a section in the README is a product decision, not a design one — but it needs an owner or the checklist becomes internal-only and stops being run.
- **AX-13: Should `always show state text` and `pattern fills` (§5.6) simply be the defaults?** If redundant encoding is genuinely mandatory, a setting that enforces it is evidence the mandate is not holding at compact density. Resolve by running checklist A3 and A5 at compact density with both settings off: if either fails, the settings are a patch over a defect and the defect should be fixed instead.
- **AX-14: Localisation interacts badly with almost everything here.** `01-visual-language.md` **V-10** already flags RTL and bidirectional truncation. Add: longer strings (German compounds, French expansions) against §7.5's minimum window sizes; screen-reader pronunciation of mono identifiers; whether the teaching layer's `real` / `real, simplified` / `game` status markers translate at all. Must be decided before localisation starts, not after.
- **AX-15: Does the `rig-monitor` headline compute figure get to glow?** `03-story-theme.md` §4.2 states the rule — *"if the player reads a number off it, nothing is drawn on top of it"* — and `03` §4.3.2/§7.4 then carve out readouts, naming this specific figure as *"the one node that may glow"*. It is the number pillar C2 exists to protect, at `atmosphere: low` and above, using a mechanism `03` itself flags as expected to cost sub-pixel antialiasing (**SK-8**). §6.3's position is that it does not ship until SK-8 is measured on all three platforms and checklist **A3** and **E1** pass with it on. This is a measurement, not an argument, and it should be settled in the same pass as SK-8 rather than separately.
