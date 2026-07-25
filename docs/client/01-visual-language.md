# 01 — Visual Language

**Status:** ⚠️ **[PROPOSAL]** — nothing in this document was decided in the design or technology sessions; it is a first-pass design system written so the six client docs that follow have one vocabulary to cite. The *substrate* is Established (JavaFX 26 + AtlantaFX 2.1, `../architecture/01-tech-stack.md` §1) and so is the *resource vocabulary* it names (`../design/01-core-resources.md`, `../design/glossary.md`) — those are cited, not invented. Platform and toolkit claims were verified against live sources; anything unverified is marked **⚠ unverified** inline.
**Depends on:** `00-client-overview.md`, `../design/01-core-resources.md`, `../design/glossary.md`, `../design/02-unlock-gates.md`, `../design/05-hacking-minigame.md`, `../architecture/01-tech-stack.md` §1
**Depended on by:** every other doc in `docs/client/`; the `client/` module implementation. **This document is the token contract — downstream docs cite these names verbatim.**

---

## 1. The token model

### 1.1 Three layers, and why there are exactly three

```
Layer 0   AtlantaFX / theme primitives      -color-bg-default, -color-accent-fg, …
             ▲ supplied by whichever AtlantaFX theme is active,
             │ or by the story theme's own stylesheet
Layer 1   Chrome semantics (ours)            -es-surface-base, -es-fg-primary, …
             ▲ thin aliases over Layer 0 — this is what makes the
             │ native theme family nearly free
Layer 2   Game semantics (ours)              -es-compute-available, -es-trace-fill, …
             ▲ meanings AtlantaFX has no opinion about, defined per theme
             │
Feature code and stylesheets reference Layer 1 and Layer 2 ONLY.
```

The layering earns its keep in two directions:

- **Downward**, Layer 1 is mostly a rename. AtlantaFX already publishes a well-considered semantic colour set, so aliasing onto it means every AtlantaFX theme — `PrimerLight`, `PrimerDark`, `NordLight`, `NordDark`, `CupertinoLight`, `CupertinoDark`, `Dracula` — becomes a working native theme for this game with no palette work. That is the entire economic case for the native theme family (`00-client-overview.md` §3.2).
- **Upward**, Layer 2 is where the game's actual semantics live, and it must exist separately because AtlantaFX has no concept of *compute allocated vs. recovering*, *heat band*, or *provenance broken*. A theme that only implemented Layer 0 would give us a pretty app with no way to say what the numbers mean.

> **The rule that keeps the layers real:** feature code and feature stylesheets never reference a Layer 0 name. If a control needs `-color-danger-fg`, it is asking for a Layer 1 or Layer 2 role that has not been named yet — name it, then use it. Direct Layer 0 references are how a semantic drifts into being "whatever red we had," and they are the CSS analogue of the pressure `CLAUDE.md` describes on the `protocol` module boundary.

### 1.2 Naming convention

```
-es-<domain>-<role>[-<variant>]
```

- **`-es-`** — project prefix (Eye and Sickle). Present so a token is greppable and can never collide with `-fx-` or `-color-`.
- **`<domain>`** — the subject: a chrome domain (`surface`, `fg`, `border`, `focus`, `accent`, `status`) or a game domain (`compute`, `ec`, `noise`, `heat`, `trace`, `storage`, `outcome`, `authority`, `provenance`, `faction`, `gate`).
- **`<role>`** — what it *means* within that domain (`available`, `recovering`, `imminent`, `verified`). Never what it looks like: a token named after a colour cannot survive a second theme, so no role is ever named `teal`, `red` or `bright`.
- **`<variant>`** — optional emphasis or index (`fg`, `emphasis`, `muted`, `subtle`, `band-0`…`band-4`).

Lowercase, hyphen-separated, singular. Style classes use the same grammar without the leading hyphen:

```
es-<primitive>                 es-gauge, es-stat, es-ledger-row
es-<primitive>-<variant>       es-gauge-compute, es-gauge-trace
es-state-<state>               es-state-pending, es-state-imminent
es-density-<density>           es-density-comfortable, es-density-compact
```

Multiple classes compose, matching JavaFX and AtlantaFX convention (`.button.flat.dense`) rather than BEM-style compound names. A compute gauge in compact density awaiting confirmation is `es-gauge es-gauge-compute es-density-compact es-state-pending`.

### 1.3 How this lands in JavaFX — the constraint that shapes everything

JavaFX's looked-up-value mechanism is documented for **colours**: the CSS Reference's "Looked-up Colors" section describes referencing another colour property set on the node or an ancestor, and notes that looked-up colours are live and react to style changes. That is exactly what a colour token needs.

There is **no documented `var()`-style mechanism for non-colour values** in the JavaFX CSS Reference — no custom lengths, no custom durations. ⚠ **unverified as an absolute**: the reference documents looked-up colours and nothing broader, but a negative is hard to prove from documentation. Treat the following as the working rule and confirm before relying on it:

> **Colour tokens are CSS looked-up colours. Every non-colour token (spacing, type size, radius, duration, icon size) is a Java constant** in `client/…/design/Tokens.java`, applied programmatically or emitted into the generated stylesheet at build time.

This is not a workaround, it is a useful discipline: it means the numeric scale has one authoritative definition in a typed language where it can be unit-tested, and it means a stylesheet cannot invent a fifteenth spacing value.

Two more toolkit facts that constrain sections below, both verified against the JavaFX CSS Reference:

- **`-fx-font-family` does not accept a comma-separated fallback list.** The reference states this explicitly. Font stacks must therefore be resolved *at startup* against `javafx.scene.text.Font.getFamilies()` and applied as a single resolved family name (§3.2).
- **JavaFX has no `font-variant` / `font-feature-settings` equivalent.** The reference states there is no equivalent for `font-variant`, and documents only `-fx-font`, `-fx-font-family`, `-fx-font-size`, `-fx-font-style`, `-fx-font-weight`. Tabular figures therefore cannot be switched on; the only reliable way to get column-aligned digits is a monospaced face (§3.5).

### 1.4 The Layer 0 vocabulary (verified)

AtlantaFX's global looked-up colours, verified against its published Global Colors reference. Layer 1 maps onto this set; the story theme *implements* this set so that stock JavaFX controls stay themed without per-control work.

| Group | Names |
|---|---|
| Foreground | `-color-fg-default`, `-color-fg-muted`, `-color-fg-subtle`, `-color-fg-emphasis` |
| Background | `-color-bg-default`, `-color-bg-overlay`, `-color-bg-subtle`, `-color-bg-inset` |
| Border | `-color-border-default`, `-color-border-muted`, `-color-border-subtle`, `-color-shadow-default` |
| Neutral | `-color-neutral-emphasis-plus`, `-color-neutral-emphasis`, `-color-neutral-muted`, `-color-neutral-subtle` |
| Accent | `-color-accent-fg`, `-color-accent-emphasis`, `-color-accent-muted`, `-color-accent-subtle` |
| Success | `-color-success-fg`, `-color-success-emphasis`, `-color-success-muted`, `-color-success-subtle` |
| Warning | `-color-warning-fg`, `-color-warning-emphasis`, `-color-warning-muted`, `-color-warning-subtle` |
| Danger | `-color-danger-fg`, `-color-danger-emphasis`, `-color-danger-muted`, `-color-danger-subtle` |
| Charts | `-color-chart-1` … `-color-chart-8` |
| Scales | `-color-dark`, `-color-light`, `-color-base-0…9`, `-color-accent-0…9`, `-color-success-0…9`, `-color-warning-0…9`, `-color-danger-0…9` |

AtlantaFX also publishes style-class constants in `atlantafx.base.theme.Styles` — verified to include `TITLE_1`…`TITLE_4`, `TEXT_CAPTION`, `TEXT_SMALL`, `TEXT_BOLD`, `TEXT_MUTED`, `TEXT_SUBTLE`, `TEXT_ON_EMPHASIS`, `ACCENT`, `SUCCESS`, `WARNING`, `DANGER`, `DENSE`, `FLAT`, `BORDERED`, `STRIPED`, `ROUNDED`, `INTERACTIVE`, `ELEVATED_1`…`ELEVATED_4`, `BG_*` and `BORDER_*` families, and `BUTTON_ICON` / `BUTTON_CIRCLE` / `BUTTON_OUTLINED`. Where one of these expresses exactly what we mean, use it rather than inventing a parallel class — §3.4 and §5.5 do this deliberately.

### 1.5 Layer 1 — chrome tokens and their Layer 0 mapping

| Token | Meaning | Usage rule | Maps to |
|---|---|---|---|
| `-es-surface-base` | The window's own background | Every `Stage`'s root fill. Nothing else. | `-color-bg-default` |
| `-es-surface-raised` | A panel or card *within* a window | Grouping related controls; the default card fill | `-color-bg-subtle` |
| `-es-surface-sunken` | A well: log transcript, table body, terminal buffer | Anything scrollable that holds a stream of records | `-color-bg-inset` |
| `-es-surface-overlay` | Popovers, tooltips, menus, the command palette | Only content that floats above the window's own plane | `-color-bg-overlay` |
| `-es-surface-emphasis` | A filled neutral block (selected header, active tab strip) | Requires `-es-fg-on-emphasis` for its text | `-color-neutral-emphasis` |
| `-es-fg-primary` | Text and glyphs the player reads | Default for all body text and all numbers | `-color-fg-default` |
| `-es-fg-secondary` | Supporting text: units, labels, timestamps | Never for a value, only for what labels one | `-color-fg-muted` |
| `-es-fg-tertiary` | Placeholders and decoration | **Never carries required information** (it is below 4.5:1 by design) | `-color-fg-subtle` |
| `-es-fg-on-emphasis` | Text sitting on any `*-emphasis` fill | Only on emphasis fills | `-color-fg-emphasis` |
| `-es-border-control` | The boundary of an interactive control | Must meet 3:1 (SC 1.4.11) — see §2.3 | project override, see §2.3 |
| `-es-border-divider` | Separation between items in a list or panel | Decorative only; never the sole indicator of a boundary | `-color-border-muted` |
| `-es-border-faint` | Barely-there structure inside dense tables | Decorative only | `-color-border-subtle` |
| `-es-focus-ring` | The keyboard focus indicator | Always 2px, always outside the control's own bounds | `-color-accent-emphasis` |
| `-es-accent-fg` | Interactive text: links, active tabs | Interactivity only, never state | `-color-accent-fg` |
| `-es-accent-emphasis` | Primary button fill, selection fill | Pairs with `-es-fg-on-emphasis` | `-color-accent-emphasis` |
| `-es-accent-muted` | Hover wash on an interactive row | — | `-color-accent-muted` |
| `-es-accent-subtle` | Selected-row background | — | `-color-accent-subtle` |
| `-es-status-ok-fg`, `-es-status-ok-emphasis`, `-es-status-ok-subtle` | Generic success | Only for outcomes that are genuinely good | `-color-success-fg` / `-emphasis` / `-subtle` |
| `-es-status-warn-fg`, `-es-status-warn-emphasis`, `-es-status-warn-subtle` | Generic caution | Recoverable; the player still has a choice | `-color-warning-fg` / `-emphasis` / `-subtle` |
| `-es-status-bad-fg`, `-es-status-bad-emphasis`, `-es-status-bad-subtle` | Generic failure | Something is lost or refused | `-color-danger-fg` / `-emphasis` / `-subtle` |
| `-es-status-idle-fg`, `-es-status-idle-emphasis`, `-es-status-idle-subtle` | Inert, disarmed, not running | The absence of a state, not a bad one | `-color-neutral-muted` / `-emphasis` / `-subtle` |

**In the native family, Layer 1 is pure aliasing** — one short stylesheet, seven themes supported. Only `-es-border-control` needs a project value, because AtlantaFX's `-color-border-default` is tuned for aesthetics rather than for SC 1.4.11 (§2.3 gives measurements).

---

## 2. Semantic colour roles

### 2.1 The three-question test for adding a role

Before any new colour token is added: (1) does an existing role already mean this? (2) will the player ever need to distinguish it from an adjacent role *at a glance, under a timer*? (3) does it survive §2.4's redundant-encoding rule without becoming noise? Three yeses, or it does not get a token.

### 2.2 Layer 2 — the game-semantic roles

#### 2.2.1 Compute — the master scarcity

`../design/01-core-resources.md` §1.4 requires four numbers visible at all times. They get four tokens because they are four *decisions*, not one number in phases.

| Token | Definition | Usage rule |
|---|---|---|
| `-es-compute-allocated` | Cycles reserved by a running consumer | Always segmented **by consumer** (bot frame, control channel, armed defense, mining, relay hop). A single undifferentiated allocated bar is a defect — the player's decision is *which one to stop* (`../design/09-defense-and-hardening.md` §3, `../design/10-botnets.md` §3). |
| `-es-compute-available` | Cycles free to commit right now | The only token permitted at full saturation. This is the number the player is looking for. |
| `-es-compute-recovering` | Spent cycles returning on the Thermal Budget curve | **Never merged into available.** Always accompanied by a time-to-recover figure (`../design/01-core-resources.md` §1.3). |
| `-es-compute-overcommit` | Allocation exceeding capacity | Should not occur, but a server-side rig change can produce it. Rendered as a hatched over-range segment, never by silently clamping the bar. |
| `-es-compute-track` | The empty part of the compute gauge | Background, not information; exempt from the 3:1 floor (§2.3 note) |

#### 2.2.2 Ethecoin

| Token | Definition | Usage rule |
|---|---|---|
| `-es-ec-fg` | An ethecoin quantity | Every EC figure, always mono, always two decimals (§9.3) |
| `-es-ec-credit` | Value moving toward the player | Ledger rows and block payouts |
| `-es-ec-debit` | Value moving away | Uses U+2212 MINUS SIGN plus a `DEBIT` label — never colour alone |
| `-es-ec-untraceable` | A transfer with no public-ledger entry (Dead Drop, `../design/08-stealth-and-noise.md` §1) | Distinct from credit/debit because *the absence of a ledger trail is the information*. Always paired with the `no ledger entry` label. |

EC does **not** reuse `-es-status-ok-fg` for credits. Money is not goodness in this economy — `../design/03-economy.md` §3 states plainly that an aggressive player runs near zero by design, so painting a positive balance green would editorialise against the intended shape of play.

#### 2.2.3 Noise

Short-horizon, decaying, pooled across the player and all active bots (`../design/01-core-resources.md` §3.1).

| Token | Definition | Usage rule |
|---|---|---|
| `-es-noise-fg` | The current pooled noise value | The pool is one number; per-source attribution appears in the attribution view (§8.1) |
| `-es-noise-decay` | The decaying tail of already-generated noise | Rendered behind the current value so the player can see it falling — this is what makes waiting a legible tactic |
| `-es-noise-threshold` | A defender-side response threshold | Drawn as a tick on the gauge track with its numeric value; crossing it is a discrete event, not a gradient |
| `-es-noise-track` | Empty gauge track | Background |

#### 2.2.4 Heat — five bands, and never a gradient

`../design/04-mining.md` §4 fixes five bands: Zero, Low, Moderate, High, Named-hacker. `../design/01-core-resources.md` §4 distinguishes **personal heat** from **server heat**.

| Token | Band | Sweep chance/hr (`../design/04-mining.md` §4) |
|---|---|---|
| `-es-heat-band-0` | Zero / cold | 2% |
| `-es-heat-band-1` | Low | ~8% |
| `-es-heat-band-2` | Moderate | ~25% |
| `-es-heat-band-3` | High | ~45% |
| `-es-heat-band-4` | Named-hacker | ~60% |

Two rules:

- **Heat renders as a banded chip carrying the band name, never as a continuous meter.** The player's decision is a threshold decision (which vendors are reachable, how likely a sweep is), and a smooth bar invites a precision the model does not have. It also resolves a palette collision: trace is the only continuous red meter in the client (§2.2.6), so heat and trace can never be confused.
- **Personal and server heat share the band ramp and are separated by an explicit scope label** — `PERSONAL · moderate`, `SERVER · high`. They are different quantities with different consequences (`../design/01-core-resources.md` §4.2) and giving them separate hues would consume the last of the hue budget (§2.5) to encode something a word encodes better.

#### 2.2.5 Trace — the defender's attribution meter

The single most consequential meter in the game (`../design/05-hacking-minigame.md` §4) and the one with an explicit legibility requirement in the source: its inputs must be legible so a loss reads as "I was too loud," never "the game decided."

| Token | Definition | Usage rule |
|---|---|---|
| `-es-trace-track` | Unaccrued trace | Background |
| `-es-trace-fill` | Accrued trace | **Moves only at its true rate**; may never be eased or interpolated (§7.3) |
| `-es-trace-imminent` | The final band before completion | A discrete state change with an announced label, not a colour fade |
| `-es-trace-contribution` | A single action's contribution segment | The mechanism by which C3 is satisfied: the bar is *composed of labelled segments*, each attributable to an action with its magnitude |

#### 2.2.6 Storage tiers — encoded by exposure, because exposure is the semantic

`../design/01-core-resources.md` §6 defines three tiers on a strict capacity/exposure trade. The tokens encode **risk**, which is why they borrow the status hues rather than claiming new ones.

| Token | Tier (`../design/glossary.md`) | Exposure | Mandatory glyph |
|---|---|---|---|
| `-es-storage-vault` | `vault` — Encrypted Vault | Never exposed | closed padlock |
| `-es-storage-standard` | `standardStorage` — Standard Storage | Exposed while owner is online | open padlock |
| `-es-storage-highhackable` | `highHackableZone` — High-Hackable Zone | Always exposed, raidable offline | broken padlock |

The glyph is mandatory, not decorative: this is a three-state risk ladder where getting it wrong costs the player items, so it must survive being read in greyscale, at a glance, by someone who has never seen the game.

#### 2.2.7 Outcomes

`../design/05-hacking-minigame.md` §2 fixes three: `breached`, `failed`, `aborted`.

| Token | Outcome | Note |
|---|---|---|
| `-es-outcome-breached` | Success; loot + `resolutionRecord` | — |
| `-es-outcome-failed` | Trace completed first; consequence applies | The consequence is always itemised alongside — what was lost, what heat was gained, what tripped |
| `-es-outcome-aborted` | Player withdrew | Deliberately **neutral, not bad**. Aborting is the sanctioned escape hatch when a read goes wrong; a UI that paints it red teaches players not to use the tool the design gave them. |

#### 2.2.8 Authority — Invariant I14 made visible

The token family that exists purely so the interface cannot imply the client decided something (`00-client-overview.md` §2, C4).

| Token | State | Rendering |
|---|---|---|
| `-es-authority-confirmed` | Server-sent value | **No decoration at all.** Confirmed is the default; decorating it would make honesty look like an exception. |
| `-es-authority-pending` | Intent sent, awaiting confirmation | Last confirmed value + a hollow ring glyph + the in-flight action's name |
| `-es-authority-stale` | Last received value, connection degraded | Dashed boundary + the value's age (`14s ago`) |
| `-es-authority-unknown` | Never received, or invalidated | **Rendered as `—`. Never as `0`.** |

The `unknown` rule is the sharp end of I14. Showing `0 EC` for an unknown balance is the client asserting a fact it does not have; `—` is always available and always true.

#### 2.2.9 Provenance — the one thing the client verifies for itself

`../architecture/04-item-provenance.md` §6 makes offline re-verification a client capability specifically so the player need not trust the server's UI. The result is *client-computed and therefore trustworthy to display* — the sole exception to C4, and it needs its own tokens so it is never confused with a server assertion.

| Token | State | Meaning |
|---|---|---|
| `-es-provenance-verified` | Chain walked to genesis, every signature resolved | The client checked this itself |
| `-es-provenance-unverified` | Not yet checked, or a key could not be resolved | Neutral, not alarming — `../design/15-open-questions.md` W-1 stubs external DID→key resolution, so this is the common case today |
| `-es-provenance-broken` | A verification step failed | The item is **not recognized** (`../architecture/04-item-provenance.md` §7). Always accompanied by *which* step failed. |

`unverified` and `broken` must never share a colour. "We haven't checked" and "we checked and it's forged" are opposite facts, and collapsing them would either cry wolf or hide a forgery.

#### 2.2.10 Faction — carried by mark, tinted only secondarily

| Token | Faction | Mark |
|---|---|---|
| `-es-faction-eye` | The Eye | an aperture / lens |
| `-es-faction-sickle` | The Sickle | a crescent |
| `-es-faction-unaligned` | Pre-commitment or neutral | an empty ring |

The hue budget (§2.5) is spent by this point, so **faction identity is primarily a mark plus a name; the tint is a thin secondary signal and is never the only carrier.** This is a constraint turned to advantage: a mark reads at 12px in a dense list where a tint does not, and it is what the fiction would actually use.

Note the disambiguation the glossary insists on: `factionReputation` (Eye/Sickle standing) and `validatorReputation` (federation server trust, `../architecture/05-validator-quorum.md`) are unrelated. They never share a token, a label, a colour, or a primitive. Validator reputation is server infrastructure and appears only in federation-facing surfaces.

#### 2.2.11 Gates — identity by glyph, state by colour

Five gates (`../design/02-unlock-gates.md` §1), and OQ-2 already flags them as possible cognitive overload. Giving each a colour would make that worse; giving each a **glyph** makes the answer to "why can't I have this" instantly scannable.

| Gate | Glyph | Token for state |
|---|---|---|
| Ethecoin | coin | `-es-gate-met` / `-es-gate-blocked` |
| Schematic | blueprint corner | " |
| Reputation | crest | " |
| Proof-of-Skill | tiered chevron | " |
| Heat State | thermometer | " |

`-es-gate-blocked` uses the idle/neutral role, **not** the danger role: a gate you have not cleared is a destination, not an error. The gate badge always states its requirement in words (§8.9), and per C4 the *verdict* is always server-rendered.

### 2.3 The palettes

Every value below was computed against the surfaces it can sit on. **Ratios are measured, not asserted** — the check script lives in the client module's test sources so a palette change that breaks the floor fails the build.

Reference backgrounds: story = the `sickle-os` surfaces defined here; native dark = GitHub Primer's dark canvases (`#0D1117` / `#161B22` / `#010409`), native light = Primer's light canvases (`#FFFFFF` / `#F6F8FA` / `#EAEEF2`). ⚠ **unverified**: that AtlantaFX's `PrimerDark`/`PrimerLight` use exactly these canvas values — they derive from Primer, but the exact hexes should be read out of the compiled theme CSS before the native palettes are frozen. The *method* is unaffected: resolve `-color-bg-*` at runtime and assert the floor against whatever the active theme actually supplies.

#### 2.3.1 `sickle-os` — surfaces and chrome

| Token | Hex | Note |
|---|---|---|
| `-es-surface-base` | `#0A0E0F` | near-black with a green-blue cast |
| `-es-surface-raised` | `#10171A` | |
| `-es-surface-sunken` | `#060909` | terminal buffers, log wells |
| `-es-surface-overlay` | `#141C1F` | popovers, palette |
| `-es-fg-primary` | `#C6D4D3` | 12.71:1 on base — warm phosphor grey, not pure white |
| `-es-fg-secondary` | `#8A9B9A` | 6.68:1 on base |
| `-es-fg-tertiary` | `#5C6C6B` | 3.52:1 — decorative/placeholder only, never information |
| `-es-border-control` | `#5F7B80` | 4.28:1 on base, 3.81:1 on overlay — clears the 3:1 non-text floor everywhere |
| `-es-border-divider` | `#2B3A3F` | decorative only (~1.6:1 by design) |
| `-es-border-faint` | `#1B2529` | decorative only |
| `-es-accent-fg` / `-es-focus-ring` | `#5AB2FF` / `#1F6FEB` | 8.54:1; white on the emphasis fill = 4.63:1 |

#### 2.3.2 `sickle-os` — game semantics

| Token | Hex | vs. base | vs. overlay (worst) |
|---|---|---|---|
| `-es-compute-available` | `#4FD6C4` | 10.85 | 9.66 |
| `-es-compute-allocated` | `#37A79D` | 6.63 | 5.90 |
| `-es-compute-recovering` | `#8B9C9B` | 6.77 | 6.03 |
| `-es-compute-track` | `#22302F` | — | background |
| `-es-ec-fg` | `#E3B341` | 9.97 | 8.88 |
| `-es-noise-fg` | `#BC8CFF` | 7.70 | 6.86 |
| `-es-trace-fill` | `#FF7B72` | 7.69 | 6.85 |
| `-es-trace-imminent` | `#FF4136` | 5.60 | 4.99 |
| `-es-status-ok-fg` | `#57D97C` | 10.73 | 9.56 |
| `-es-status-warn-fg` | `#E3B341` | 9.97 | 8.88 |
| `-es-status-bad-fg` | `#FF6B63` | 6.96 | 6.20 |
| `-es-heat-band-0…4` | `#7E8F8E` `#C9C05A` `#E09A4B` `#F0663F` `#FF4136` | 5.73 / 10.32 / 8.21 / 6.17 / 5.60 | all ≥4.99 |
| `-es-faction-eye` | `#A5B4C4` | 9.17 | 8.16 |
| `-es-faction-sickle` | `#E08A57` | 7.34 | 6.53 |

Lowest text-level value in the theme: **4.99:1** (`trace-imminent` on `-es-surface-overlay`), above the 4.5:1 floor. Meter fills read at ≥5.4:1 against `-es-compute-track`.

`sickle-os-hc` (`00-client-overview.md` §3.3) keeps these hues and raises every foreground toward 7:1, swaps `-es-border-divider` for `-es-border-control`, and drops all surface translucency.

#### 2.3.3 Native dark (reference: Primer Dark canvases)

| Token | Hex | Worst measured |
|---|---|---|
| `-es-compute-available` | `#4FD6C4` | 9.68 |
| `-es-compute-allocated` | `#2E9C93` | 5.18 |
| `-es-compute-recovering` | `#8B949E` | 5.62 |
| `-es-ec-fg` | `#E3B341` | 8.89 |
| `-es-noise-fg` | `#BC8CFF` | 6.86 |
| `-es-trace-fill` | `#FF7B72` | 6.86 |
| `-es-trace-imminent` | `#F85149` | 5.16 |
| `-es-status-ok-fg` | `#3FB950` | 6.81 |
| `-es-status-warn-fg` | `#D29922` | 6.85 |
| `-es-status-bad-fg` | `#F85149` | 5.16 |
| `-es-heat-band-0…4` | `#8B949E` `#D3C351` `#E0913F` `#F0673D` `#F85149` | ≥5.16 |
| `-es-faction-eye` / `-es-faction-sickle` | `#A5B4C4` / `#DB8A5A` | 8.18 / 6.42 |
| `-es-border-control` | `#6A737D` | 3.59 (non-text floor 3:1) |

#### 2.3.4 Native light (reference: Primer Light canvases)

| Token | Hex | Worst measured |
|---|---|---|
| `-es-compute-available` | `#0A6570` | 5.79 |
| `-es-compute-allocated` | `#083F45` | 9.97 |
| `-es-compute-recovering` | `#5B646D` | 5.16 |
| `-es-ec-fg` | `#7A5310` | 5.86 |
| `-es-noise-fg` | `#5B2FA8` | 7.45 |
| `-es-trace-fill` | `#A40E26` | 6.75 |
| `-es-trace-imminent` | `#7A0616` | 9.66 |
| `-es-status-ok-fg` | `#0F6B2C` | 5.70 |
| `-es-status-warn-fg` | `#845800` | 5.33 |
| `-es-status-bad-fg` | `#B31D28` | 5.77 |
| `-es-heat-band-0…4` | `#6E7781` `#8A7400` `#9A5B00` `#B33A17` `#A40E26` | ≥3.90 (non-text floor 3:1) |
| `-es-faction-eye` / `-es-faction-sickle` | `#41576F` / `#8A421D` | 6.39 / 6.26 |
| `-es-border-control` | `#7D8590` | 3.20 (non-text floor 3:1) |

Note the light theme inverts the compute pair: **`allocated` is darker than `available`**, because on a light surface "more ink" must mean "more committed." The relationship the player learns is *density = commitment*, and it holds in both directions.

### 2.4 The hard rule: colour is never the only carrier

Every colour-coded state also carries a **text label**, a **glyph**, or a **shape**. This is WCAG 2.2 SC 1.4.1, and it is simultaneously the mechanical form of the "losses must be attributable" pillar — a state you can name is a state you can reason about afterwards.

| State | Colour | + Text | + Glyph / shape |
|---|---|---|---|
| Compute allocated / available / recovering | 3 tokens | segment legend naming each consumer and its cycles | recovering segment is hatched; over-range is cross-hatched |
| Heat band | band ramp | `PERSONAL · moderate` | 5-pip band indicator, filled to the band |
| Noise threshold crossed | `-es-noise-threshold` | `threshold 60 crossed` | tick mark on the track |
| Trace imminent | `-es-trace-imminent` | `trace imminent` | meter switches to a stepped fill |
| Storage tier | 3 tokens | tier name in full | closed / open / broken padlock |
| Outcome | 3 tokens | `breached` / `failed` / `aborted` | check / cross / dash |
| Authority | 4 tokens | in-flight action name, or the value's age | hollow ring / dashed bound / `—` |
| Provenance | 3 tokens | `verified` / `unverified` / `chain broken at depth 4` | shield outline / hollow shield / broken shield |
| Faction | 2 tints | faction name | aperture / crescent mark |
| Gate | met / blocked | the requirement in words | the gate's own glyph |
| Ledger direction | credit / debit | `CREDIT` / `DEBIT` | `+` / `−` (U+2212) |
| Node defended / dormant | status roles | `live` / `dormant` | ring weight on the node |

**The greyscale test is the acceptance criterion:** screenshot any window, desaturate it, and every distinction above must survive. If it does not, the redundant encoding is missing, not the colour.

### 2.5 The hue budget

Nine distinct hues are in service across all themes. This is a ceiling, not a starting point.

| Hue | Owner |
|---|---|
| Azure | chrome accent, focus, interactivity |
| Teal-cyan | compute |
| Amber-gold | ethecoin, and generic warning |
| Violet | noise |
| Green | success / breached / verified |
| Red | danger / failed / trace |
| Orange | heat mid-bands |
| Blue-grey | The Eye |
| Rust | The Sickle |

> **A tenth hue may only be introduced by retiring one.** Past roughly nine hues a colour system stops being a legend and starts being decoration, and the greyscale test in §2.4 is what actually carries the meaning anyway. If a new domain needs distinguishing, reach for a glyph or a shape first.

---

## 3. Typography

### 3.1 Two families, and why the monospace one is load-bearing

- **UI face** — chrome: labels, buttons, prose, menus, headings.
- **Monospace face** — the game: every number the player compares, every identifier, every log line, every address, hash and DID.

The mono face is not stylistic. §1.3 established that JavaFX exposes no `font-variant` / `font-feature-settings`, so **tabular figures cannot be enabled**; a proportional face will render `1` narrower than `8` and a column of compute figures will not align. In this client the player is constantly comparing numbers in columns under a timer (C5). The only mechanism the toolkit offers for aligned digits is a monospaced face — so the mono rule is a technical requirement wearing a stylistic coat.

It also happens to be the right register (`00-client-overview.md` §3.3): a game whose vocabulary is Unix should have terminal typography where terminal typography belongs.

### 3.2 Font stacks and how they resolve

**The constraint:** `-fx-font-family` does not accept a comma-separated list (verified, §1.3). So the stacks below are *resolution orders*, evaluated once at startup against `Font.getFamilies()` (verified to exist on `javafx.scene.text.Font`, returning `List<String>`), with the first available family applied as a single name.

**UI face, native family:**

| Platform | Resolution order |
|---|---|
| macOS | `SF Pro Text` → `SF Pro` → `Helvetica Neue` → `System` |
| Windows | `Segoe UI Variable Text` → `Segoe UI Variable` → `Segoe UI` → `System` |
| Linux | `Adwaita Sans` → `Inter` → `Cantarell` → `Ubuntu` → `Noto Sans` → `DejaVu Sans` → `System` |

*(Verified: Windows 11's system font is Segoe UI Variable, with Text/Small/Display optical variants. Verified: GNOME's HIG names Adwaita Sans — a custom variant of Inter — as the current default. macOS SF Pro / SF Mono are longstanding Apple system faces; ⚠ **unverified** in this pass because Apple's HIG page did not return content — confirm the exact family strings JavaFX reports on macOS before shipping.)*

**Monospace face, native family:**

| Platform | Resolution order |
|---|---|
| macOS | `SF Mono` → `Menlo` → `Monaco` → `Monospaced` |
| Windows | `Cascadia Mono` → `Cascadia Code` → `Consolas` → `Monospaced` |
| Linux | `JetBrains Mono` → `Source Code Pro` → `Noto Sans Mono` → `DejaVu Sans Mono` → `Liberation Mono` → `Monospaced` |

`Monospaced` is JavaFX's logical family and is always present, so the chain can never fail to resolve.

**Story family (`sickle-os`) bundles its faces** and does not consult the OS at all — the point of an authored theme is that it looks the same everywhere:

| Role | Face | Licence |
|---|---|---|
| Mono (dominant) | **JetBrains Mono** | SIL OFL 1.1 — bundling/embedding in software is permitted; the licence file must ship alongside (verified) |
| UI (chrome only) | **Inter** | SIL OFL 1.1 — ⚠ **unverified** in this pass; confirm before bundling |

Loaded with `Font.loadFont(InputStream, double)` (verified static method) from the client jar's resources. Bundling makes the story theme deterministic and removes a whole class of "it looks wrong on my machine" reports.

### 3.3 A JavaFX conversion note on line height

JavaFX has no `line-height` property. `Labeled` and `TextFlow` expose `-fx-line-spacing`, which is **extra space between lines**, not the total line box. The scale below specifies *target line box height* because that is what a designer reasons about; the token layer converts to a spacing delta using the resolved face's natural line height. ⚠ **unverified**: the exact natural line height per resolved face — measure at startup rather than assuming, since it varies between SF Pro, Segoe UI Variable and Inter at the same nominal size.

### 3.4 The type scale

Comfortable density. Sizes in px (JavaFX CSS `-fx-font-size` in `px`; AtlantaFX's default is 14px, which this scale matches at body).

| Token | Face | Size / line box | Weight | Use | AtlantaFX class where equivalent |
|---|---|---|---|---|---|
| `TYPE_DISPLAY` | UI | 28 / 36 | 600 | A window's single headline value, rarely | `Styles.TITLE_1` |
| `TYPE_TITLE_1` | UI | 22 / 28 | 600 | Window title bar content, top-level section | `Styles.TITLE_2` |
| `TYPE_TITLE_2` | UI | 18 / 24 | 600 | Panel headings | `Styles.TITLE_3` |
| `TYPE_TITLE_3` | UI | 15 / 20 | 600 | Group headings inside a panel | `Styles.TITLE_4` |
| `TYPE_BODY` | UI | 14 / 20 | 400 | **Default.** All prose, labels, controls | — |
| `TYPE_BODY_STRONG` | UI | 14 / 20 | 600 | Emphasis within body; never for a number | `Styles.TEXT_BOLD` |
| `TYPE_CAPTION` | UI | 12 / 16 | 400 | Units, timestamps, secondary metadata | `Styles.TEXT_CAPTION` |
| `TYPE_MICRO` | UI | 11 / 14 | 500 | Dense table metadata only. **Absolute floor.** | — |
| `TYPE_MONO_READOUT_LG` | Mono | 28 / 34 | 500 | The rig monitor's headline compute figure | — |
| `TYPE_MONO_READOUT` | Mono | 20 / 26 | 500 | Primary numeric readouts in any tool | — |
| `TYPE_MONO_BODY` | Mono | 13 / 20 | 400 | Log lines, tables, terminal buffer, identifiers | — |
| `TYPE_MONO_CAPTION` | Mono | 12 / 18 | 400 | Dense mono metadata: hashes, DIDs, timestamps | — |

Mono runs one step smaller than UI at the same optical weight (13 mono ≈ 14 UI) because monospaced faces set optically larger at equal nominal size.

**11px is the floor and it applies to both themes and both densities.** Windows' own guidance names 12px regular / 14px semibold as the point below which text becomes illegible in some languages; 11px is reserved for metadata that is never the sole carrier of a value.

### 3.5 Where mono is mandatory

Not a preference — these are the cases where proportional digits actively cause errors.

1. **Any number the player compares against another number.** Compute figures, EC amounts, noise values, yields, tier stats, prices, timers, percentages, block progress. If it can appear in a column, it is mono.
2. **Any identifier.** Handles, DIDs, item ids, node addresses, `itemId`, `nonce`.
3. **Any hash or signature material.** `prevRecordHash`, JWS fragments, key ids — always mono, always middle-truncated with the full value copyable, never silently clipped (§9.3).
4. **Any log line.** The whole `LogLine` primitive, including its prose payload — recovered logs and system output are machine-authored in fiction and must look it.
5. **Any terminal or command surface.** Input and output both.
6. **Any tabular cell containing a value**, even when the column header is UI-face.

Everything else — headings, button labels, explanatory prose, teaching-layer definitions, error messages — is UI face. Mono for running prose is a legibility cost with no compensating benefit.

The `es-numeric` style class packages the rule: mono face, right-aligned, no wrap, and `TYPE_MONO_BODY` unless overridden. Applying it is how a developer opts into correctness rather than remembering three properties.

---

## 4. Spacing, grid, density

### 4.1 Base unit: 4px

Every spatial value is a multiple of 4. Four is small enough that 12/20/28 are expressible without half-steps and large enough that the scale stays short.

| Token | px | Typical use |
|---|---|---|
| `SPACE_0` | 0 | Deliberate flush contact |
| `SPACE_HAIR` | 2 | Chip inner padding, icon-to-label at 12px |
| `SPACE_1` | 4 | Inside a chip; between a value and its unit |
| `SPACE_2` | 8 | Between related controls; dense row padding |
| `SPACE_3` | 12 | Standard control gap; comfortable row padding |
| `SPACE_4` | 16 | Panel padding; between control groups |
| `SPACE_6` | 24 | Between panels; window padding (comfortable) |
| `SPACE_8` | 32 | Major section separation |
| `SPACE_12` | 48 | Empty-state and first-run layouts only |

### 4.2 Windows and panels

| Surface | Comfortable | Compact |
|---|---|---|
| Window content inset | `SPACE_6` (24) | `SPACE_4` (16) |
| Panel / card inset | `SPACE_4` (16) | `SPACE_3` (12) |
| Between panels | `SPACE_4` (16) | `SPACE_2` (8) |
| Table cell horizontal | `SPACE_3` (12) | `SPACE_2` (8) |
| Chip inner | `SPACE_1` / `SPACE_2` | `SPACE_HAIR` / `SPACE_1` |

### 4.3 Row heights and hit targets

| Element | Comfortable | Compact | Floor |
|---|---|---|---|
| Table / ledger / log row | 32 | 26 | — |
| List item with a chip | 36 | 28 | — |
| Standard control | 32 | 28 | — |
| **Any time-critical control** | **40** | **36** | never below 32 |

The last row is a C5 rule: a control the player must hit while a trace timer runs does not shrink with density. Abort, defend, reallocate, and disarm are the named cases.

### 4.4 The density toggle

Per-window (`Shortcut+Shift+C`), persisted per window id. An operator dashboard is dense by nature — twelve deployed miners, eight armed defenses, a scrolling log — and forcing one density on all twelve windows serves none of them.

| Density changes | Density never changes |
|---|---|
| Padding and gaps (§4.2) | Font sizes |
| Row heights (§4.3) | Which columns are shown |
| Chip inner padding | Any value's precision |
| Divider visibility in tables | Hit targets for time-critical controls |
| Whether secondary metadata is on its own line or inline | Any accessible name |

> **The rule that keeps compact honest:** compact removes *space*, never *information*. The moment compact hides a column, the two densities stop presenting identical information and the §3.5 floor in `00-client-overview.md` is broken. Compact also never reduces a font below the 11px floor, because a density preference must not become an accessibility problem.

`Styles.DENSE` (AtlantaFX, verified) is applied alongside `es-density-compact` so stock controls follow.

### 4.5 Corner radius

Four values, deliberately few. A design system with a continuous radius scale ends up with fourteen of them.

| Token | px | Use |
|---|---|---|
| `RADIUS_0` | 0 | Tables, log wells, terminal buffers, gauge tracks in `sickle-os-hc` |
| `RADIUS_SM` | 2 | Chips, gauge tracks and fills, small inputs |
| `RADIUS_MD` | 4 | Buttons, text fields, panels, item cards |
| `RADIUS_LG` | 8 | Popovers, `ModalPane` content, the command palette |

A theme may shift the whole scale by one step to change its character — `sickle-os` runs one step tighter than native, because sharper corners read as instrumentation — but it may not use a value outside the scale, and `RADIUS_0` is the floor in both directions.

### 4.6 Alignment

- **Labels left, values right, in any two-column readout.** Right-aligned mono values with a shared right edge is the only arrangement in which a column of numbers can be compared at a glance.
- **Units are right-attached to their value with `SPACE_1`** and set in `-es-fg-secondary` at `TYPE_CAPTION` — the unit is never mistaken for a digit.
- **No centred body text anywhere**, including empty states.

---

## 5. Elevation and layering

### 5.1 "On top" means something different here

In a single-window app, elevation is invented. In this client every tool is a real OS window, so **z-order belongs to the window manager and the player**, and JavaFX will not fight it for us. Elevation therefore has two distinct meanings that must not be conflated:

- **Window order** — the OS's. We influence it exactly twice: the rig monitor's `alwaysOnTop`, and explicit user-initiated raises (`00-client-overview.md` §6.2).
- **In-window layering** — ours. Surface steps and shadows *inside* one `Stage`.

### 5.2 The decision procedure: panel, Stage, or modal

Ask in order; first "yes" wins.

1. **Is it a different tool the player might want open beside this one?** → a new **`Stage`**. This is the fantasy; err toward it.
2. **Is it detail about something in this window, that stops mattering once read?** → a **popover** on `-es-surface-overlay`, dismissed on Escape or focus loss.
3. **Is it a sub-view of this tool the player switches between?** → a **panel** inside the same `Stage`. Never a new window for a tab.
4. **Does it require a decision before this window can continue, with a real consequence?** → a **`ModalPane`** scoped to this window (AtlantaFX, verified: "a container for displaying application dialogs on top of the current scene without opening a modal Stage").
5. **Anything else** → it is not modal. Say it in the window.

### 5.3 The modal ban

> **`Modality.APPLICATION_MODAL` is banned outright in this client.** It blocks input to every window in the application — including the rig monitor. A dialog that blocks the compute ledger during a live breach is a design failure, and C2 makes the rig monitor's availability a pillar, not a nicety.

The permitted alternatives, in preference order:

1. **`ModalPane`** (AtlantaFX) — in-scene, no new `Stage`, blocks one window's content only. The default.
2. **`Modality.WINDOW_MODAL`** with an explicit owner — blocks only that owner. Use when a real `Stage` is genuinely needed.
3. **Inline confirmation** — a bar within the affected panel. Best for reversible-but-consequential actions.

Destructive and irreversible actions still confirm — abort a breach, kill a miner, install Ghost Protocol (`../design/08-stealth-and-noise.md` §3), forfeit faction tools (`../design/01-core-resources.md` §5) — they just confirm *inside their own window*.

### 5.4 Always-on-top policy

Exactly one window sets it by default: `rig-monitor`, per `../design/01-core-resources.md` §1.4, and the scaffold's `ToolWindow.alwaysOnTop()` already encodes "true only for the rig monitor." The player may revoke it (it is their desktop); revoking it does not close the window, and `Shortcut+0` still raises it instantly. No other window may set `alwaysOnTop` without the player asking for it, and no *alert* may ever set it — an alert that forces itself above a live breach is focus theft with extra steps.

### 5.5 In-window elevation tokens

| Level | Surface | Shadow | Border | Used for |
|---|---|---|---|---|
| 0 | `-es-surface-base` | none | none | The window's own plane |
| 1 | `-es-surface-raised` | none | `-es-border-divider` | Panels and cards — **separation by surface step and border, not shadow** |
| 2 | `-es-surface-raised` | `Styles.ELEVATED_1` | `-es-border-divider` | A card the player is dragging or has selected |
| 3 | `-es-surface-overlay` | `Styles.ELEVATED_2` | `-es-border-control` | Popovers, menus, command palette |
| 4 | `-es-surface-overlay` | `Styles.ELEVATED_3` | `-es-border-control` | `ModalPane` content, over a scrim |

Levels 1–2 avoid shadows deliberately. On a near-black story surface a shadow is invisible; on a light native surface it is expensive to render across a dozen panels. Surface step plus a border works in both, which is what a shared design system requires. Levels 3–4 use shadow because a floating element genuinely needs to detach from the plane below it.

When `reducedTransparency` is set (JavaFX 24+, verified) or `@media (prefers-reduced-transparency: reduce)` matches, scrims become fully opaque and overlay surfaces lose all translucency.

---

## 6. Iconography

### 6.1 Style

- **Geometric line icons** on a 24×24 grid, 2px stroke at 24px scaled proportionally (1.5px at 16, 1px at 12), square caps, no rounded terminals, no filled shapes except state dots and the padlock bodies.
- **No perspective, no gradients, no dual-tone.** The set must read at 12px in a dense table and survive both palettes.
- **The story theme may go to 1.5px at 24px** for a thinner, more instrument-like feel. That is the full extent of the theme's licence over the icon set — geometry never changes between themes, because geometry is meaning (§2.4).

### 6.2 Delivery

Icons ship as **SVG path-data string constants** rendered through `javafx.scene.shape.SVGPath`, in one generated `Icons` class. Not an icon font (no external dependency, no font-loading failure mode, no missing-glyph boxes), and not raster assets (no DPI set to maintain across three platforms and mixed-scale multi-monitor setups, `00-client-overview.md` **CL-8**). Colour comes from the fill, so an icon inherits its token like any other node.

### 6.3 Sizes

| Token | px | Use |
|---|---|---|
| `ICON_12` | 12 | Inside a chip, inline with `TYPE_CAPTION`/`TYPE_MICRO` |
| `ICON_16` | 16 | Default — inline with `TYPE_BODY`, table cells |
| `ICON_20` | 20 | Toolbar buttons, panel headings |
| `ICON_24` | 24 | Window-level actions, empty states |

### 6.4 The accessible-name rule

> **An icon never appears without an accessible name.** Every icon-only control sets `accessibleText` (verified on `javafx.scene.Node`, alongside `accessibleRole`, `accessibleRoleDescription` and `accessibleHelp`). Every icon that carries state — a padlock, a gate glyph, a provenance shield — either sits beside a text label or sets `accessibleText` itself.

Purely decorative icons (a divider ornament) set no accessible text and are excluded from focus traversal. If an icon is *not* decorative and *not* named, it is a bug — this is checkable and should be checked in tests, because it is the single easiest accessibility regression to introduce.

Icon-only controls additionally carry a tooltip with the same string as their accessible name, so sighted keyboard users get the same information.

---

## 7. Motion

### 7.1 Durations

| Token | ms | Use |
|---|---|---|
| `DUR_INSTANT` | 0 | Theme switch, density switch, anything under a running timer |
| `DUR_FAST` | 120 | Hover, focus, press feedback |
| `DUR_BASE` | 180 | Panel expand/collapse, chip state change |
| `DUR_SLOW` | 240 | Popover and `ModalPane` entry |
| `DUR_DELIBERATE` | 400 | **Ceiling.** Once-per-session transitions only (first-run, sign-in). Never in a tool window. |

### 7.2 Easings

JavaFX supports CSS transitions with `transition-property`, `transition-duration`, `transition-timing-function` and `transition-delay`, and accepts `linear`, `ease`, `ease-in`, `ease-out`, `ease-in-out`, `cubic-bezier()`, `steps()` and piecewise `linear()` (all verified in the JavaFX 26 CSS Reference).

| Situation | Easing | Why |
|---|---|---|
| Entering / appearing | `ease-out` | Decelerating arrival reads as settling |
| Leaving / dismissing | `ease-in` | Accelerating exit gets out of the way |
| State change in place | `ease-in-out` | Symmetric, no implied direction |
| **Any continuously-driven meter** | **`linear`** | See below |
| Discrete band change | `steps(1, jump-end)` | A band change is an event, not a slide |

> **Continuous meters must be `linear`, and this is a correctness rule, not a taste rule.** An eased trace bar is *lying about rate*: it would appear to slow near the end while the underlying value accrues at constant speed. `../design/05-hacking-minigame.md` §4 requires the trace's inputs to be legible so a loss reads as "I was too loud"; an easing curve on the trace meter breaks that at the exact moment it matters most. Same for compute recovery — the Thermal Budget curve is already non-linear in the *data* (`../design/01-core-resources.md` §1.3), so any easing on top of it double-counts and misinforms.

### 7.3 What may animate, and what may never

**May animate:** hover and focus feedback; panel expand/collapse; popover entry and exit; chip state changes (colour and glyph swap, at `DUR_BASE`); meter fills *at their true rate, linearly*; the switcher's alert indicator (a two-step opacity pulse at `DUR_SLOW`, capped at three cycles, then steady).

**May never animate — no exceptions:**

- **Any numeric readout.** No count-up, no roll, no odometer. A number that is mid-animation is a number the player cannot read, and `01-core-resources.md` §1.4's "at a glance" requirement forbids it. Values snap.
- **The trace meter's position.** It moves at its true rate or not at all.
- **Anything that reorders a list the player may be pointing at.** New log lines and alerts append; the view does not re-sort under the cursor.
- **Window open, close, or resize.** The window manager owns that; we do not add to it.
- **Anything at all while a breach is live**, beyond the meters themselves. That includes the theme switch, the density switch, and every alert indicator, which go straight to their end state.
- **Text reveal.** No typewriter effects on content the player needs to read (`00-client-overview.md` §7).

### 7.4 Reduced motion

Honoured in **both** theme families. Two mechanisms, both verified, used together:

- **CSS** — `@media (prefers-reduced-motion: reduce)` (JavaFX 25+) zeroes every `transition-duration` in the stylesheet.
- **Java** — `Platform.getPreferences().reducedMotionProperty()` (JavaFX 24+) is observed so that motion driven in code is *skipped*, not merely shortened. The distinction matters: a 0ms fade still schedules a frame; skipping it entirely is what the preference actually asks for.

Under reduced motion, every transition becomes an instant state change. Nothing is hidden, deferred, or replaced by a fade — the end state simply arrives immediately. The alert-indicator pulse becomes a static filled indicator.

---

## 8. Data display primitives

Ten primitives, specified once here and reused everywhere. Downstream docs cite these class names and anatomies verbatim rather than re-specifying. Each has an **anatomy** (its parts, in order) and a **state set**.

Every primitive that displays a server-owned value composes an **AuthorityBadge** (§8.4). That is what makes C4 mechanical rather than aspirational.

### 8.1 Gauge — `es-gauge`

The workhorse: a bounded quantity with a track, one or more fills, optional threshold marks, and a numeric readout.

**Anatomy** (left to right, top to bottom):
1. `label` — UI face, `TYPE_CAPTION`, `-es-fg-secondary`. Names the quantity.
2. `readout` — mono, `TYPE_MONO_READOUT` (or `_LG` on the rig monitor), `-es-fg-primary`. Current / total, e.g. `72 / 100`.
3. `unit` — UI face, `TYPE_CAPTION`, `-es-fg-secondary`, right-attached with `SPACE_1`.
4. `track` — the domain's `*-track` token; height 8 comfortable / 6 compact; radius `RADIUS_SM`.
5. `fill segments` — one or more, each with its own token and its own accessible name.
6. `threshold marks` — 2px ticks on the track, each labelled with its numeric value.
7. `legend` — one row per segment: swatch + name + value. **Mandatory whenever there is more than one segment.**
8. `authority badge` — §8.4.
9. `attribution affordance` — hover, focus, or `Alt` reveals the decomposition (C3).

**States:** `default` · `es-state-imminent` (final band; stepped fill + label) · `es-state-overcommit` (cross-hatched over-range segment) · `es-state-pending` · `es-state-stale` · `es-state-unknown` (track drawn empty, readout `—`).

**Variants:**

| Class | Domain | Segments | Notes |
|---|---|---|---|
| `es-gauge-compute` | compute | allocated (per consumer) · available · recovering | The legend is not optional here — it is the decision surface (`../design/01-core-resources.md` §1.4) |
| `es-gauge-trace` | trace | contribution segments, one per action | Linear only (§7.2). Each segment names its action and magnitude. |
| `es-gauge-noise` | noise | current · decay tail | Threshold ticks mandatory |
| `es-gauge-buffer` | yield buffer fullness | filled portion of the 4-hour cap | The crack-timing bet (`../design/04-mining.md` §5.1) — fullness *is* the payout |
| `es-gauge-block` | mining block progress | progress toward the next block | Required so mid-block reallocation forfeit is an informed choice (`../design/04-mining.md` §1.3) |

### 8.2 StatReadout — `es-stat`

A single labelled value. The most-used primitive in the client.

**Anatomy:** `label` (UI, `TYPE_CAPTION`, `-es-fg-secondary`) · `value` (mono, `es-numeric`, `TYPE_MONO_READOUT` or `TYPE_MONO_BODY`) · `unit` (UI, `TYPE_CAPTION`, secondary) · optional `delta` (mono, signed with U+2212 for negatives, plus a direction glyph) · optional `chip` (§8.3) · `authority badge`.

**States:** `default` · `es-state-pending` · `es-state-stale` · `es-state-unknown` (value `—`) · `es-state-blocked` (a gate applies; composes a GateBadge).

**Rule:** a `delta` always names its window (`+12 EC / 1h`). A bare delta is unattributable and violates C3.

### 8.3 StateChip — `es-chip`

The universal carrier that makes §2.4 mechanical. Any state that has a colour is expressed as a chip.

**Anatomy:** `glyph` (`ICON_12`) · `text` (UI, `TYPE_CAPTION`, sentence case) · optional `value` (mono). Padding `SPACE_HAIR`/`SPACE_1`, radius `RADIUS_SM`, 1px border in the state's token, fill in the state's `*-subtle`.

**States:** it *is* a state. The chip vocabulary, exhaustively: heat band (5) · outcome (3) · authority (4) · provenance (3) · storage tier (3) · faction (3) · gate met/blocked (2) · node live/dormant (2) · miner tier T1–T3 (3) · puzzle class (5, **[PROPOSAL]** — `../design/05-hacking-minigame.md` §3.1) · difficulty tier 1–5.

**Rule:** a chip **never** ships without text. An icon-and-colour-only chip is precisely the failure §2.4 exists to prevent.

### 8.4 AuthorityBadge — `es-authority`

I14 made visible. Composed by every primitive showing server-owned state.

**Anatomy:** by state — `confirmed` renders **nothing at all**; `pending` is a hollow ring glyph plus the in-flight action's name; `stale` is a dashed boundary on the host primitive plus the value's age; `unknown` replaces the value with `—` and labels it `unknown`.

**Rule:** confirmed being invisible is deliberate. If honesty needed a badge, dishonesty would be the default.

### 8.5 LedgerRow — `es-ledger-row`

One public-ledger entry (`../design/01-core-resources.md` §2.2). Row height per §4.3.

**Anatomy:** `timestamp` (mono, `TYPE_MONO_CAPTION`) · `direction` (`+` / U+2212 plus `CREDIT`/`DEBIT`) · `amount` (mono, `es-numeric`, `-es-ec-credit` / `-es-ec-debit`) · `counterparty` (handle or DID, mono, middle-truncated, click-through to `identity`) · `reason` (UI, `TYPE_BODY`) · `traceability chip` (`on ledger` / `no ledger entry` for Dead Drop transfers) · `authority badge`.

**States:** `default` · `es-state-pending` (submitted, unconfirmed) · `es-state-selected` · `es-state-untraceable` (uses `-es-ec-untraceable` and the `no ledger entry` chip).

**Rule:** the ledger is a **gameplay feature** — investigators, player and NPC, follow EC flows to build evidence (`../design/12-identity-and-social.md` §3). So a ledger row is filterable, sortable, copyable, and every field is selectable text. It is not a pretty transaction list; it is evidence, and it must be usable as evidence.

### 8.6 ItemCard — `es-item-card`

A tool, frame, schematic, or consumable. Level 1 elevation (§5.5).

**Anatomy:** `name` (UI, `TYPE_TITLE_3`) · `type/class` (UI, `TYPE_CAPTION`, secondary) · `gate badge` (§8.9) · `cost row` — EC (mono), **compute** (mono), **noise** (band word) — the three economy-facing stats every tool in `../design/06-intrusion-tools.md` and `../design/07-recon-tools.md` carries · `storage tier chip` · `provenance chip` · `function` (UI, `TYPE_BODY`, one sentence) · `actions`.

**States:** `default` · `es-state-blocked` (gate unmet; the requirement is stated, the card is not hidden) · `es-state-equipped` · `es-state-socketed` (assigned to a bot — and therefore **out of the vault and mid-risk**, `../design/10-botnets.md` §1) · `es-state-pending` · `es-state-unknown`.

**Rules:** compute and noise are shown with equal weight to EC price. `../design/06-intrusion-tools.md` §5 makes those the real balance levers, so a card that shows price prominently and compute in fine print teaches the wrong economy. And `es-state-socketed` is visually loud on purpose — "this item left the vault" is the risk decision the storage system is built around.

### 8.7 LogLine — `es-log-line`

The atom of `terminal`, `recon`, `audit`, and every event stream.

**Anatomy:** `timestamp` (mono, `TYPE_MONO_CAPTION`, secondary) · `severity glyph` (`ICON_12`) · `source` (mono, secondary — process, node, or subsystem) · `message` (mono, `TYPE_MONO_BODY`, `-es-fg-primary`) · optional `term markers` (§8.10) · optional `attribution suffix` (what this line contributed to noise or trace).

**States:** `default` · `es-state-alerting` (a canary trip, a defense trigger) · `es-state-selected` · `es-state-recovered` (a story artefact, not system output — visually distinguished because provenance of *narrative* matters: `../design/14-world-and-narrative.md` §3).

**Rules:** log lines are **selectable, copyable text**, never rendered as images or non-selectable labels. Manual investigation depends on the player reading and cross-referencing them (`../design/04-mining.md` §3.1), and a player who cannot copy a connection id cannot cross-reference it. New lines append; the view never re-sorts under the cursor (§7.3).

### 8.8 GraphNode — `es-node`

A node on the network map. The one primitive where **shape carries primary meaning**, because the map is scanned spatially rather than read.

**Anatomy:** `shape` (node kind — circle: citizen/endpoint · square: corporate · hexagon: state/Eye infrastructure · diamond: another operator's rig · triangle: unknown/unresolved) · `ring` (defense profile; weight rises with firewall tier — `../design/09-defense-and-hardening.md`) · `fill` (live vs. dormant, per Traffic Analyzer, `../design/07-recon-tools.md`) · `badges` (miner present · canary · honeypot flag · yours) · `label` (mono, `TYPE_MONO_CAPTION`, the node address) · `hop distance` from the player's entry point (proximity is graph hop distance, `../design/01-core-resources.md` §3.1).

**States** — a knowledge ladder, then two overlays: `es-state-unknown` (never scanned — dashed outline, no fill) · `es-state-sniffed` (type known, one hop) · `es-state-mapped` (two hops, Topology Mapper) · `es-state-analyzed` (live/dormant known) · `es-state-breached`; overlaid by `es-state-suspected-trap` (Honeypot Detector positive — and note the detector has a deliberate ~20% false-negative rate, so a *clean* reading is never a guarantee, `../design/07-recon-tools.md` §2) and `es-state-engaged` (a breach is running here now).

**Rule:** the map must render usefully with most nodes in the `unknown` state. Recon is expensive and optional (`../design/07-recon-tools.md` §3); a map that only looks right when fully scanned is a map that punishes the intended play pattern.

### 8.9 GateBadge — `es-gate-badge`

Which of the five gates blocks this, and whether it is cleared.

**Anatomy:** `gate glyph` (§2.2.11) · `gate name` · `requirement in words` — `400 EC` · `Cold Storage Expansion schematic` · `Sickle standing: trusted` · `Logic class, tier ≥ 3, live target` · `heat ≥ moderate` · `state chip` (`met` / `blocked`).

**States:** `es-gate-met` · `es-gate-blocked` · `es-state-pending` (the server's verdict is in flight) · `es-state-unknown`.

**Rules:** the requirement is **always** stated in words, never as a bare lock. OQ-2 flags five gates as possible cognitive overload, and the mitigation is that the answer to "why can't I have this" is always on screen. Split gates (`../design/02-unlock-gates.md` §1.1 — Relay Chain, Rainbow Table, Cold Storage Expansion) show **both** components with the ceiling component first, because that is the one the player cannot buy their way past. And per C4, `met`/`blocked` is always a server verdict rendered as received.

### 8.10 TermTip — `es-term`

The educational layer's anchor (`00-client-overview.md` §5).

**Anatomy:** the term, rendered **exactly as it would be without the marker**, plus a 1px dotted underline in `-es-fg-tertiary`. On hover, focus, or `Shortcut+/`: a popover on `-es-surface-overlay` containing `term` (mono if it is a literal identifier, UI otherwise) · `status chip` (`real` / `real, simplified` / `game` — §5.3 of `00-client-overview.md`) · `definition` (UI, `TYPE_BODY`, ≤3 sentences) · optional `in this game` note where the model is simplified · optional `see also`.

**States:** `explain` (marked, and expanded inline once on first appearance in a session) · `terms` (marked only if unseen in this profile) · `off` (unmarked; still reachable via `man <term>`).

**Rules:** the marker never changes the term's metrics — no layout shift when the teaching level changes. The popover never steals focus. Never shown during a live breach unless the player asks for it. And the status chip is mandatory: an unmarked definition that presents `noise` as a networking concept would actively miseducate, which is worse than teaching nothing.

### 8.11 State-class matrix

The shared state vocabulary. A primitive supports a state or it does not; it never invents a synonym.

| Class | Meaning | Supported by |
|---|---|---|
| `es-state-pending` | Server confirmation in flight | Gauge, Stat, LedgerRow, ItemCard, GateBadge |
| `es-state-stale` | Last known value, connection degraded | Gauge, Stat, LedgerRow |
| `es-state-unknown` | No value; renders `—` | Gauge, Stat, ItemCard, GateBadge |
| `es-state-blocked` | A gate applies | Stat, ItemCard, GateBadge |
| `es-state-imminent` | Final band before a threshold | Gauge (trace, noise, buffer) |
| `es-state-overcommit` | Beyond capacity | Gauge (compute) |
| `es-state-alerting` | Requires triage, not focus | LogLine, GraphNode, switcher entry |
| `es-state-engaged` | An engagement is running here | GraphNode |
| `es-state-selected` | Player selection | LedgerRow, ItemCard, LogLine, GraphNode |
| `es-state-equipped` / `es-state-socketed` | Readied / assigned to a bot | ItemCard |
| `es-state-untraceable` | No public-ledger entry (Dead Drop) | LedgerRow |
| `es-state-recovered` | A narrative artefact, not system output | LogLine |
| `es-state-sniffed` / `es-state-mapped` / `es-state-analyzed` / `es-state-breached` | Recon knowledge ladder | GraphNode |
| `es-state-suspected-trap` | Honeypot Detector positive | GraphNode |
| `es-state-disabled` | Not actionable in this context | any control |

A primitive-specific state must still be declared here. The matrix is the whole vocabulary; a class that is not in it does not exist.

---

## 9. Writing and microcopy

### 9.1 Tone

**Operator register: terse, declarative, factual.** The interface is a tool the player owns, and tools do not have personality. No exclamation marks. No second person cheerleading ("Nice work!"). No apologies ("Oops!"). No filler ("Please wait while we..."). No emoji, ever.

The model is good system output: `05 miners active · 3 buffers above 75%`, not `You've got 5 miners running — nice!`.

Two register exceptions, both diegetic and both scoped: **recovered narrative content** speaks in the voice of whoever wrote it (`../design/14-world-and-narrative.md` §3 — Eye memos are bureaucratic, Sickle chat is fractious), and **teaching-layer definitions** are allowed to be warmer and more explanatory, because their job is explanation.

### 9.2 Capitalisation

- **Sentence case** for all UI text including headings, buttons and window titles. Title Case reads as marketing.
- **Canonical names keep their canonical capitalisation** exactly as `../design/glossary.md` writes them: Port Sweep, Rainbow Table, Overflow Kit, Credential Harvester, Side-Channel Reader, Passive Sniffer, Topology Mapper, Traffic Analyzer, Ping Sweep, Honeypot Detector, Provenance Tracer, Canary Token, Tarpit, Honeypot Stash, Auto-Counter Daemon, Rootkit Wrapper, Cold Storage Expansion, Compute Cores, Thermal Budget, Bandwidth, Memory Buffer, Isolated Partition, Firmware Implant, Worm Module, Cuckoo Patch, Payout Splitter, Log Scrubber, Identity Spoofer, Traffic Shaper, Dead Drop, Relay Chain, Ghost Protocol, Burner Handle, The Eye, The Sickle.
- **Command tokens are lowercase** and set in mono: `scan --thorough`, `man rainbow-table`, `theme sickle-os`. This is Unix, and Unix is lowercase.
- **No literal uppercase strings.** JavaFX provides no `text-transform` equivalent (the CSS Reference documents only the `-fx-font*` family), so uppercasing would have to be done in Java — which puts uppercase in the accessible name and makes screen readers spell words out. Where the story theme wants an institutional-header feel, it uses **letter-spacing and weight**, not case. A toolkit limitation forcing the accessible outcome is a good trade.

### 9.3 Numbers and units

| Quantity | Format | Example | Notes |
|---|---|---|---|
| Compute | integer + `cycles` | `72 / 100 cycles` | Never fractional. `c` only where space forbids the word. |
| Compute allocation | per-consumer list | `Breacher 22 · Miner 10 · Recon 8` | Never one aggregate number (C2) |
| Compute recovery | value + clock | `18 recovering · 4m 20s` | Time-to-recover mandatory (`../design/01-core-resources.md` §1.4) |
| Ethecoin | 2 decimals + `EC` | `1,240.00 EC` | Always two decimals — `../design/15-open-questions.md` P-8 fixed EC at 100 minor units. Suffix `EC`, never a glyph. |
| Negative EC | U+2212 + `DEBIT` | `−45.00 EC` | Never a hyphen-minus; never colour alone |
| Rates | value + `/hr` or `/cycle-hr` | `40 EC/hr`, `0.4 EC/cycle-hr` | Matches `../design/04-mining.md` §1 |
| Noise | scalar + band word | `43 · moderate` | Band words are exactly those in the tool tables: `none`, `low`, `moderate`, `high`, `very high` |
| Heat | scope + band | `PERSONAL · moderate` | Scope label mandatory (§2.2.4) |
| Difficulty / miner tier | `tier N` / `T1`–`T3` | `tier 3`, `T2` | Difficulty is 1–5 (`../design/05-hacking-minigame.md` §3.3, enforced at the wire boundary as P-10) |
| Percentages | integer + `%` | `47%` | Never more than one decimal, and only where the model justifies it |
| Durations | largest two units | `4m 20s`, `2h 14m` | Never `260 seconds` |
| Timestamps | `HH:mm:ss` local, mono | `21:04:33` | Absolute date on hover; relative (`14s ago`) only for staleness |
| Handles / DIDs / hashes | mono, middle-truncated | `did:plc:xxxx…yyyy` | Full value always copyable; **never** silently clipped by layout |
| Unknown | `—` | `—` | Never `0`, never blank, never `N/A` (§2.2.8) |

Thousands separators and decimal marks follow the OS locale; the *shape* of the format does not.

### 9.4 Error messages

Three parts, in order, each one sentence, and the third is required whenever an action is available:

```
<what failed>.  <why, in the player's terms>.  <what you can do>.
```

- ✅ `Deployment refused. The target already hosts one of your miners. Choose another node, or audit the existing miner.`
- ❌ `Error: DEPLOY_CONFLICT (409)`
- ❌ `Oops! Something went wrong. Please try again.`

Rules:

- **Name the authority.** *"The server refused this deployment"* and *"could not reach the server"* are different facts with different remedies and never collapse into one message (C4). A refusal states the rule that was applied; a connectivity failure states the connection state and what is safe to retry.
- **Never blame the player.** State the rule, not the mistake. `Reputation gate not met — requires Sickle standing: trusted.` not `You don't have enough reputation.`
- **Never expose a raw code as the whole message.** A correlation id may appear as copyable secondary text; it is never the sentence.
- **Distinguish refused from failed.** *Refused* means a rule applied and nothing changed. *Failed* means something went wrong mid-flight and the state may need reconciling. The player's next action differs, so the words must.
- **In-game consequences are not errors.** A failed breach, a swept network, a lost bot are outcomes with narrative weight and get outcome surfaces (§8.1, §8.3, C3), never an error dialog. Only the client and the transport produce errors.

### 9.5 Empty states

Say what would be here, why it is not, and the one action that changes it. Three lines maximum, left-aligned, `-es-fg-secondary`.

> `No deployed miners.` / `Deployed miners are the only income that accrues while you are offline.` / `[ Find a host ]`

Never a joke, never an illustration, never a blank panel. And per C4, "no data" and "we haven't heard from the server" are different empty states with different copy.

---

## 10. Open questions

Deliberately undecided here. Log in `../design/15-open-questions.md` §2 if this doc set is adopted.

- **V-1: Does JavaFX offer any custom-property mechanism for non-colour values?** §1.3 assumes not, and puts every numeric token in Java. If a general mechanism exists (or lands in a future release), the numeric scale could live in CSS alongside the colours, which would simplify the story theme considerably. **Verify against the JavaFX 26 CSS Reference and the `javafx.css` API before the token layer is built** — it is cheap now and expensive after fifty call sites.
- **V-2: Do AtlantaFX's `PrimerLight`/`PrimerDark` use exactly GitHub Primer's canvas values?** §2.3 measured against Primer's published canvases. Read the compiled theme CSS and re-measure before the native palettes are frozen. The *method* holds regardless; only the published ratios would move.
- **V-3: Exact macOS system-font family strings as JavaFX reports them.** §3.2's macOS resolution order is from general knowledge; Apple's HIG page could not be fetched in this pass. Log `Font.getFamilies()` on a real macOS machine and correct the list.
- **V-4: Is Inter the right bundled UI face for the story theme, and is its licence compatible with bundling?** JetBrains Mono's OFL 1.1 bundling terms were verified; Inter's were not. Confirm, or pick a face whose terms are confirmed.
- **V-5: Natural line heights per resolved face.** §3.3 converts target line boxes to `-fx-line-spacing` deltas, which requires knowing each face's natural line height. Measure at startup rather than hardcoding.
- **V-6: Should puzzle classes get colour tokens at all?** §8.3 lists them as chips and §2.5's hue budget is spent. Shape-coding five classes (`../design/05-hacking-minigame.md` §3.1) may be better — but the class set is itself `[PROPOSAL]` and may collapse to two or three (P-1), so this cannot be settled until the puzzle is.
- **V-7: How does the compute gauge segment a rig with fifteen consumers?** §8.1 requires per-consumer segmentation, which stays legible to roughly eight segments. A late-game player running six bots, four defenses, five control channels and a relay chain exceeds that. Candidate: group by consumer *class* with drill-down. Needs a real late-game loadout to design against.
- **V-8: Do the story theme's five heat bands stay distinguishable for the ~8% of players with common colour-vision deficiencies?** The band ramp runs grey → yellow → orange → red-orange → red, which is a lightness ramp as well as a hue ramp, and the mandatory 5-pip indicator carries the value regardless. Confirm with a simulation pass before the palette is frozen, and treat the pip indicator as non-removable until then.
- **V-9: Chart colours.** AtlantaFX publishes `-color-chart-1…8`; this document defines no charting tokens because no client doc has yet specified a chart. If historical views land (income over time, heat over a week, `../design/03-economy.md` §6), they need a categorical ramp that does not collide with the nine semantic hues.
- **V-10: Right-to-left and localisation.** All copy rules assume LTR English. The layout rules (§4.5's label-left/value-right in particular) would need mirroring, and mono middle-truncation of identifiers interacts badly with bidirectional text. Out of scope here; must be decided before any localisation work starts, not after.
