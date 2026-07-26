# 03 — uOS: The Story-Atmospheric Theme Family

**Status:** ⚠️ **[PROPOSAL]** — the *existence* of this family, its two shipped variants and its register are proposed in `00-client-overview.md` §3.3; the `uos` surface and game-semantic hexes it publishes are cited here verbatim, never redefined. Everything else — the phosphor variants, the atmosphere layer, the diegetic scope model, the escalation skin, the performance ladder — is new ground. Three things underneath are **Established** and are cited rather than re-decided: JavaFX + AtlantaFX as the theming mechanism (`../architecture/01-tech-stack.md` §1), one `javafx.stage.Stage` per tool (`../design/00-vision-and-pillars.md` §6), and the client as a non-authoritative view layer (Invariant I14). Every toolkit and standards claim below was checked against a live source in this pass; anything not checked is marked **⚠ unverified** inline and repeated in §10.
**Depends on:** `00-client-overview.md` §3.3 · §3.4 · §3.5 · §7, `01-visual-language.md` (**the token contract — token, primitive and state-class names here are cited, never redefined**), `../design/00-vision-and-pillars.md` §3 (Pillars 4 & 5) · §5 · §7, `../design/01-core-resources.md` §1.4 · §4, `../design/04-mining.md` §4, `../design/05-hacking-minigame.md` §4 · §5, `../design/14-world-and-narrative.md` §3 · §4 · §6, `../design/glossary.md`
**Depended on by:** the story stylesheet and the generated token layer in the `client/` module — specifically the `StoryTheme`, `AtmosphereLevel` and `EscalationSkin` types this document specifies; `02-platform-native-themes.md` is the mirror doc for family A and shares the palette-verification harness

---

## 1. Design thesis

### 1.1 The three feelings, and what each demands of a skin

`../design/00-vision-and-pillars.md` §5 gives three tone targets. They are not decoration — each one has a direct, checkable consequence for how this theme is drawn.

| Tone target (`../design/00` §5) | What it demands of the skin | What would break it |
|---|---|---|
| **"An operator, not an action hero."** Tension from overextension, not reflexes. | The screen must look like *instrumentation somebody maintains*: dense, aligned, labelled, monospaced, unglamorous. Competence is assumed; nothing is explained twice. | Splash graphics, hero art, chunky game-UI buttons, progress bars that celebrate. Anything that treats the player as an audience rather than an operator. |
| **"Paranoia is a feature."** The game never fully confirms the player is safe. | The skin must be able to express *unresolved* states as first-class: `-es-authority-unknown`, `-es-provenance-unverified`, `es-state-suspected-trap`. An honest `—` in the right typeface is more frightening than a red flash. | A skin that only knows "good" and "bad." Collapsing "we haven't checked" into "it's fine" is the exact lie the fiction is about. |
| **"Winning feels like getting away with something."** | Success is *quiet*. A breach resolves with a settled readout and a line in a log, not a fanfare. The reward is that nothing else happened. | Victory animation, colour explosion, sound-and-light. Loud success turns a heist into a slot machine. |

### 1.2 Pillar 5 makes the interface a narrative surface, not a container for one

`../design/00-vision-and-pillars.md` §3 Pillar 5: *story arrives through logs, emails and database records recovered by hacking; there are no companion characters.* `../design/14-world-and-narrative.md` §3 turns that into a content-format list and §6 into a tone: **The Eye never monologues; the scariest recovered document is a routine memo treating the player as a line item.**

A game with no characters and no cutscenes has exactly one continuously-available narrative surface: **the chrome**. That is what this theme family is for. Every choice below is a sentence about the world:

- The client is *software the resistance built* — so it is monospaced, terse, self-hosted-looking, slightly worn, and it does not apologise.
- The Eye is *an institution, not a villain* — so when it appears in the chrome it appears as **formatting**: a different rule weight, a different register, a reference number (§6.5).
- The state is *older and shabbier than it pretends* — so the palette is phosphor, not neon signage. Blade Runner's contribution here is materiality, not rain.

### 1.3 The thesis in one rule

> **The story theme is a claim about who made this software and what it costs to run it. It is never a claim about what the numbers say.**

Everything the player *reads* — every value, label, gate requirement, error and outcome — is governed by `01-visual-language.md`. This document may change how the surface behind those values looks, feels and is framed. It may not change, obscure, delay, decorate or dramatise the values themselves.

### 1.4 The ledger of what atmosphere is allowed to cost

Stated once, as a list, because every subsequent section is an application of it. Atmosphere may cost:

- **GPU time**, within §8's budget, and only when the player has opted into it.
- **Bytes on disk** — bundled fonts, one noise tile.
- **Screen area** for authored decoration (rules, a status line, a banner) that carries no data.

Atmosphere may **never** cost:

- Contrast against the §9.1 floors — measured at the *worst* escalation state (§7.3), not at rest.
- Legibility of a value under a running timer (client pillar **C5**, `00-client-overview.md` §2).
- Information parity with the native family (`00-client-overview.md` §3.4, §3.5 item 3).
- An accessible name, a focus indicator, or a keyboard route.
- Latency between a server-confirmed value arriving and the player seeing it.

---

## 2. The aesthetic vocabulary — palettes

### 2.1 The phosphor lineage, and what each register connotes

Three registers, chosen because each says something different about *who is running this machine and how long they have had it*:

| Register | Reads as | Fictional claim | Risk it carries |
|---|---|---|---|
| **Cool cyan / blue-white** | Current, capable, cold, forensic. The register of *Ghost in the Shell*: dense readouts that assume competence, no hand-holding chrome. | This operator is good and their kit is modern. | Can read as generic sci-fi if the surfaces go too blue; hence the green-blue cast on near-black rather than navy. |
| **Amber** | Old, repaired, electrical, warm. The register of *Blade Runner*'s materiality — a machine that has been fixed several times. | This operator is running salvage, and it still works. | Amber is already spent on ethecoin and warning (`01-visual-language.md` §2.5). Handled in §2.3. |
| **Green** | Institutional, older still, faintly hostile. The green terminal is the register of the thing you are breaking into as much as the thing you are using. | This kit was state hardware once. | Green is already spent on success/verified/breached. Handled in §2.3. |

We do not chase CRT authenticity beyond colour. Curvature, phosphor bleed and interlace are simulated *displays*; this theme simulates a *program*. The distinction matters because a simulated display puts a lens between the player and the data, and §1.4 forbids that.

### 2.2 The variant catalogue

`00-client-overview.md` §3.3 names two variants in this family. This document proposes two more, and proposes that high contrast becomes a **modifier** rather than a variant id.

| Theme id | Name | Register | Status |
|---|---|---|---|
| `uos` | *neon rain* | Cool cyan / blue-white on near-black with a green-blue cast | Proposed in `00-client-overview.md` §3.3; palette published in `01-visual-language.md` §2.3.1–2.3.2. **This document completes it** (§2.5) and does not alter a published value. |
| `uos-amber` | *old iron* | Amber-warm chrome on warm near-black | ⚠ **[PROPOSAL], new here.** Extends `00-client-overview.md` §3.3's two-variant list — see **SK-2**. |
| `uos-phosphor` | *dead channel* | Green-cast chrome on cold near-black | ⚠ **[PROPOSAL], new here.** Same caveat. |
| *(modifier)* `-hc` | High contrast | Applies to any of the three | `uos-hc` is named in `00-client-overview.md` §3.3; §2.7 proposes generalising it to a modifier, which also answers **PN-6** in `02-platform-native-themes.md` §8 from the story side. |

There is no light variant in this family, and that is deliberate and already settled: `00-client-overview.md` §4.1 — *"a surveillance-dystopia operator console in light mode is a different game."* A player who wants light chooses the native family, which does light properly (`02-platform-native-themes.md`).

### 2.3 The constraint that shapes all three palettes: a monochrome theme cannot exist here

The obvious version of this theme is a *true single-phosphor* console — everything amber, everything green. It is beautiful, it is authentic, and it is unshippable, for a reason worth stating precisely rather than as taste:

`01-visual-language.md` §2.5 spends nine hues, and seven of them are load-bearing game semantics: compute, ethecoin, noise, heat, trace, The Eye, The Sickle. §2.4's redundant-encoding rule already requires each of those to carry a label or glyph *in addition to* colour — but the rule's own acceptance criterion is the **greyscale test**, and greyscale is what a monochrome theme reduces to. Passing the greyscale test means every distinction *survives* desaturation; it does not mean the player should be made to work at desaturated speed permanently. Under a trace timer the player is comparing a compute gauge in one window against a noise gauge in another (`../design/05-hacking-minigame.md` §5) — that comparison is a glance in colour and a read in monochrome, and **C5** is about the glance.

So the rule this family runs on, which also proposes an answer to **CL-1's sibling question CL-2** in `00-client-overview.md` §8:

> **A story variant owns Layer 1 (chrome) outright. It may not move a Layer 2 hue family. Only lightness and chroma may be retuned per variant, and only to hold the contrast floor against that variant's surfaces.**

Mechanised as a numeric rule that a unit test can assert, using sRGB chroma defined as `max(R,G,B) − min(R,G,B)`:

| Class of token | Chroma constraint | Measured across the three variants |
|---|---|---|
| Layer 1 chrome (surfaces, fg, borders) | **≤ 48** | `uos` 33 · `uos-amber` 44 · `uos-phosphor` 33 |
| Layer 2 hue-carrying semantics | **≥ 60** | minimum 61 (`-es-heat-band-1`, `#C9C05A`) |
| Layer 2 deliberately-neutral semantics (`-es-compute-recovering`, `-es-status-idle-fg`, `-es-outcome-aborted`, `-es-provenance-unverified`, `-es-authority-unknown`, `-es-faction-unaligned`) | ≤ 48, i.e. they read as chrome on purpose | 16–17 |

That gap — 48 against 60 — is what lets `uos-amber` be genuinely amber without ethecoin's amber disappearing into it. The chrome is a low-chroma tan; ethecoin is `#E3B341` at chroma 162, nearly four times as saturated. The variant reads as amber; the money still reads as money.

What the player therefore gets from choosing a variant is **the entire world of the interface** — every surface, every text tone, every border, rule, prompt, cursor and banner — while the instrument panel keeps saying the same thing it says everywhere. That is the correct split, and it is also the cheap one.

> A true single-phosphor *purist* mode is not ruled out forever, but it requires a full shape-and-glyph audit first (every meter fill differentiated by hatch pattern, every chip by glyph) and it must be labelled as what it is. Tracked as **SK-3**.

### 2.4 Layer 1 — chrome, per variant

All 17 Layer 1 tokens from `01-visual-language.md` §1.5. `uos` values are quoted from `01-visual-language.md` §2.3.1 and are **unchanged**; the two rows it did not publish (`-es-surface-emphasis`, `-es-fg-on-emphasis`) and the three accent variants are supplied here.

| Token | `uos` | `uos-amber` | `uos-phosphor` | Floor it must clear |
|---|---|---|---|---|
| `-es-surface-base` | `#0A0E0F` ¹ | `#0D0B08` | `#070C08` | — |
| `-es-surface-raised` | `#10171A` ¹ | `#171310` | `#0E150F` | — |
| `-es-surface-sunken` | `#060909` ¹ | `#080706` | `#040706` | — |
| `-es-surface-overlay` | `#141C1F` ¹ | `#1C1712` | `#121B14` | — |
| `-es-surface-emphasis` | `#2B3736` | `#3A2F22` | `#28382B` | 4.5:1 vs `-es-fg-on-emphasis` → **12.34 / 13.05 / 12.42** |
| `-es-fg-primary` | `#C6D4D3` ¹ (11.32) | `#DECFB4` (11.60) | `#C6DCC4` (12.10) | 4.5:1 on every surface |
| `-es-fg-secondary` | `#8A9B9A` ¹ (5.95) | `#A2917A` (5.82) | `#8CA48A` (6.53) | 4.5:1 |
| `-es-fg-tertiary` | `#5C6C6B` ¹ (3.14) | `#746655` (3.20) | `#5E7260` (3.40) | none — **decorative only**, never carries information |
| `-es-fg-on-emphasis` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | 4.5:1 on every `*-emphasis` fill |
| `-es-border-control` | `#5F7B80` ¹ (3.81) | `#8C7C60` (4.38) | `#5F8064` (3.99) | **3:1** (SC 1.4.11) |
| `-es-border-divider` | `#2B3A3F` ¹ | `#382E22` | `#27362A` | none — decorative |
| `-es-border-faint` | `#1B2529` ¹ | `#221C15` | `#18211A` | none — decorative |
| `-es-focus-ring` | `#1F6FEB` ¹ (3.73) | `#1F6FEB` (3.84) | `#1F6FEB` (3.80) | **3:1** vs every surface it can sit on |
| `-es-accent-fg` | `#5AB2FF` ¹ (7.61) | `#5AB2FF` (7.83) | `#5AB2FF` (7.75) | 4.5:1 |
| `-es-accent-emphasis` | `#1F6FEB` ¹ | `#1F6FEB` | `#1F6FEB` | 4.5:1 vs white → **4.63** |
| `-es-accent-muted` | `#223F57` | `#243D52` | `#203E52` | background wash — no floor of its own; `-es-fg-primary` on it → **7.18 / 7.34 / 7.71** |
| `-es-accent-subtle` | `#152531` | `#18222B` | `#13232B` | carries `-es-accent-fg` → **6.90 / 7.10 / 7.10** |

¹ Published in `01-visual-language.md` §2.3.1 — cited, not redefined. Parenthesised figures are the **worst** measured ratio across `-es-surface-base` / `-raised` / `-sunken` / `-overlay`. `01` §2.3.1 quotes its figures against `-es-surface-base` only, which is why some numbers here are lower for the same hex — this pass reproduces `01`'s base-surface numbers exactly (12.71 / 6.68 / 3.52 / 4.28 / 8.54) and then reports the stricter worst-surface value, which is what the floor is actually asserted against.

Three notes that are design, not bookkeeping:

- **Azure survives in all three variants, and that is the point.** In `uos-amber` the entire world is warm; the single cool hue on screen is the one you can click. *In a warm world, cool means interactive.* Keeping `-es-accent-fg` fixed also means the focus ring is the one element a player never has to relearn when they switch variants, which matters because the focus ring is the accessibility floor's visible half (`00-client-overview.md` §3.5 item 4).
- **`-es-fg-on-emphasis` is pure white in all three**, and it is the only pure white in the family. `01-visual-language.md` §2.3.1 measured `-es-accent-emphasis` (`#1F6FEB`) against white at 4.63:1; a tinted near-white drops it to 4.15–4.29 and breaks the floor. Emphasis fills are small and saturated, so white on them costs nothing atmospherically, and bending the accent hue to save a tint would cost the shared focus indicator.
- **`uos-amber` runs a slightly lower `-es-fg-tertiary`** (3.20 vs 3.52). That is inside spec because `01-visual-language.md` §1.5 declares the token below 4.5:1 *by design* and forbids it from carrying required information. It is listed so nobody later "fixes" it into an information carrier.

### 2.5 Layer 2 — game semantics, shared across all three variants

The 52 Layer 2 tokens. Per §2.3 these are **variant-invariant** except for the three gauge tracks, which take the variant's cast so a meter looks like it belongs to the surface it sits on. Values already published in `01-visual-language.md` §2.3.2 are marked ¹ and are unchanged; the rest are supplied here to complete the 69-token contract.

**Compute** (`01-visual-language.md` §2.2.1)

| Token | Hex | Worst measured | Note |
|---|---|---|---|
| `-es-compute-available` | `#4FD6C4` ¹ | 9.66 | the only token allowed at full saturation |
| `-es-compute-allocated` | `#37A79D` ¹ | 5.90 | always segmented by consumer |
| `-es-compute-recovering` | `#8B9C9B` ¹ | 6.03 | neutral by design; hatched fill |
| `-es-compute-overcommit` | `#E5534B` | 4.66 text / 3.70 vs track | cross-hatched over-range segment |
| `-es-compute-track` | `#22302F` ¹ · `#1F2B27` · `#1B2E27` | background | per variant: neon · amber · phosphor |

Meter fills against their track: `available` 7.67–8.20, `allocated` 4.69–5.01, `recovering` 4.78–5.11, `overcommit` 3.70–3.96. All clear the 3:1 non-text floor with margin; `01-visual-language.md` §2.3.2's "≥5.4:1" claim holds for the two fills that carry the player's decision.

**Ethecoin** (`01-visual-language.md` §2.2.2)

| Token | Hex | Worst | Note |
|---|---|---|---|
| `-es-ec-fg` | `#E3B341` ¹ | 8.88 | every EC figure |
| `-es-ec-credit` | `#F2C55C` | 10.63 | brighter than `-es-ec-fg`, never green |
| `-es-ec-debit` | `#C99A3F` | 6.73 | dimmer; **always** with U+2212 and the `DEBIT` label |
| `-es-ec-untraceable` | `#C9A96B` | 7.71 | low-chroma amber, rendered **hollow** (outline weight, dashed underline) + the `no ledger entry` chip |

The credit/debit split is a *lightness* split inside one hue, not two hues, for two reasons. First, the hue budget is spent. Second — and this is the design point — `../design/01-core-resources.md` §2.2 makes the public ledger an investigation surface, and an investigator scanning a hundred rows is looking for *amount and counterparty*, not for a colour-coded mood. Direction is carried by `+` / U+2212 and the word, per `01-visual-language.md` §2.4. `-es-ec-untraceable` is deliberately the *dullest* of the four: a Dead Drop leaves no trail, and the skin says so by having less to show.

**Noise** (`01-visual-language.md` §2.2.3)

| Token | Hex | Worst | Note |
|---|---|---|---|
| `-es-noise-fg` | `#BC8CFF` ¹ | 6.86 | the pooled value |
| `-es-noise-decay` | `#8A67B8` | 3.36–3.46 vs track | non-text; the falling tail drawn *behind* the current value |
| `-es-noise-threshold` | `#D6C2FF` | 9.27–9.55 vs track | tick + its numeric value |
| `-es-noise-track` | `#2A2436` · `#2C2130` · `#26232F` | background | per variant |

**Threshold ticks are drawn in the gutter below the track, never across the fill.** Drawn on the fill they would need to clear 3:1 against both the track *and* `-es-noise-fg`, which no single value does; drawn in the gutter they only ever sit on a surface. This is the cheap correct answer to a real contrast trap and it costs 4px of vertical space.

**Heat** (`01-visual-language.md` §2.2.4; bands from `../design/04-mining.md` §4)

| Token | Band | Hex | Worst |
|---|---|---|---|
| `-es-heat-band-0` | Zero (2%/hr) | `#7E8F8E` ¹ | 5.11 |
| `-es-heat-band-1` | Low (~8%) | `#C9C05A` ¹ | 9.19 |
| `-es-heat-band-2` | Moderate (~25%) | `#E09A4B` ¹ | 7.32 |
| `-es-heat-band-3` | High (~45%) | `#F0663F` ¹ | 5.49 |
| `-es-heat-band-4` | Named-hacker (~60%) | `#FF4136` ¹ | 4.99 |

Adjacent bands sit at only **1.10–1.80** contrast with each other. That is not a defect — it is a continuous ramp read as five steps — but it is exactly why `01-visual-language.md` §2.4's mandatory **5-pip band indicator plus band word** is non-removable, and why this theme may never substitute a glow or a tint for the pip. It also corroborates **V-8**: the ramp is a lightness ramp as well as a hue ramp, but the pip is what actually carries the value.

**Trace** (`01-visual-language.md` §2.2.5)

| Token | Hex | Worst | Note |
|---|---|---|---|
| `-es-trace-track` | `#331F22` · `#351C19` · `#301E1B` | background | per variant |
| `-es-trace-fill` | `#FF7B72` ¹ | 6.85 text / 6.12–6.27 vs track | **linear only** (`01-visual-language.md` §7.2) |
| `-es-trace-imminent` | `#FF4136` ¹ | 4.99 text / 4.46–4.57 vs track | discrete state + announced label |
| `-es-trace-contribution` | `#7A2B26` | 3.78 vs `-es-trace-fill` | 1px separator between contribution segments, and the leader line to each segment's label |

In `es-state-imminent` the meter switches to a stepped fill (`01-visual-language.md` §2.4), so segment boundaries are carried by the step geometry there — `-es-trace-contribution` sits at 2.75 against `-es-trace-imminent` and is *not* relied on in that state.

**Storage · outcome · authority · provenance · faction · gate · status**

| Token | Hex | Worst | Note |
|---|---|---|---|
| `-es-storage-vault` | `#57D97C` | 9.56 | + closed padlock |
| `-es-storage-standard` | `#E3B341` | 8.88 | + open padlock |
| `-es-storage-highhackable` | `#FF6B63` | 6.20 | + broken padlock |
| `-es-outcome-breached` | `#57D97C` | 9.56 | + check |
| `-es-outcome-failed` | `#FF6B63` | 6.20 | + cross |
| `-es-outcome-aborted` | `#9AAAA9` | 7.15 | **neutral, not bad** — + dash |
| `-es-authority-confirmed` | *= `-es-fg-primary`* | — | **never drawn** (`01-visual-language.md` §8.4) |
| `-es-authority-pending` | `#8FA6B5` | 6.82 | hollow ring + in-flight action name |
| `-es-authority-stale` | `#C0A87C` | 7.51 | dashed boundary + age |
| `-es-authority-unknown` | `#7A8A89` | 4.79 | renders `—`, never `0` |
| `-es-provenance-verified` | `#57D97C` | 9.56 | shield outline |
| `-es-provenance-unverified` | `#8A9B9A` | 5.95 | hollow shield — **neutral, not alarming** |
| `-es-provenance-broken` | `#FF6B63` | 6.20 | broken shield + which step failed |
| `-es-faction-eye` | `#A5B4C4` ¹ | 8.16 | aperture mark |
| `-es-faction-sickle` | `#E08A57` ¹ | 6.53 | crescent mark |
| `-es-faction-unaligned` | `#7E8F8E` | 5.11 | empty ring |
| `-es-gate-met` | `#57D97C` | 9.56 | gate glyph + requirement in words |
| `-es-gate-blocked` | `#9AAAA9` | 7.15 | **idle role, not danger** — a gate is a destination |
| `-es-status-ok-fg` | `#57D97C` ¹ | 9.56 | |
| `-es-status-warn-fg` | `#E3B341` ¹ | 8.88 | shares the amber hue with EC by design |
| `-es-status-bad-fg` | `#FF6B63` ¹ | 6.20 | |
| `-es-status-idle-fg` | `#9AAAA9` | 7.15 | the absence of a state |
| `-es-status-*-emphasis` | ok `#1F7A3D` · warn `#8A6414` · bad `#9E2B26` · idle `#3A4645` | 5.37 / 5.37 / 7.43 / 9.80 vs `#FFFFFF` | filled blocks |
| `-es-status-*-subtle` | derived, see below | ≥5.89 for its own `*-fg` | chip fills |

**`*-subtle` and `*-muted` are derived, not hand-picked**, so they inherit each variant's cast automatically and cannot drift:

```
-es-<role>-subtle  =  mix( -es-<role>-fg,  -es-surface-base,  0.14 )
-es-<role>-muted   =  mix( -es-<role>-fg,  -es-surface-base,  0.30 )
```

Computed, for reference: in `uos`, `ok-subtle #152A1E`, `warn-subtle #282516`, `bad-subtle #2C1B1B`, `idle-subtle #1E2425`; in `uos-amber`, `#172818 / #2B2310 / #2F1815 / #21211F`; in `uos-phosphor`, `#122918 / #262310 / #2A1915 / #1C221F`. Worst chip-text ratio across all roles and all three variants: **5.89:1** (`-es-status-bad-fg` on its own subtle fill in `uos`).

`-es-provenance-broken` deliberately shares the danger hue with `-es-status-bad-fg`, because a forged provenance chain *is* the danger case — the item is not recognized (`../architecture/04-item-provenance.md` §7). `-es-provenance-unverified` is a neutral grey and must never drift toward it: "we haven't checked" and "we checked and it's forged" are opposite facts (`01-visual-language.md` §2.2.9), and `../design/15-open-questions.md` W-1 makes *unverified* the common case today. A theme that made the common case look like an alarm would train players to ignore the alarm.

### 2.6 What every variant must hold constant

The acceptance test for a new story variant, in the same spirit as `00-client-overview.md` §3.4's screenshot test:

1. Every Layer 2 hue family is unchanged (§2.3's chroma rule, machine-asserted).
2. Every text token clears 4.5:1 against all four surfaces; every boundary, fill and focus indicator clears 3:1 (§9.1).
3. Chrome chroma ≤ 48; hue-carrying semantic chroma ≥ 60.
4. The greyscale test (`01-visual-language.md` §2.4) passes on `rig-monitor`, `ledger`, `map` and `storage`.
5. `-es-accent-fg` and `-es-focus-ring` are unchanged.
6. The floors still hold at maximum escalation tint (§7.3), not only at rest.

### 2.7 High contrast as a modifier

`uos-hc` exists in `00-client-overview.md` §3.3 so that choosing the story family never costs a player legibility. With three base variants, hand-authoring three high-contrast palettes is three more surfaces to verify. Proposed instead — a **deterministic transform**, applied at stylesheet-generation time to any story variant:

| Step | Transform | Effect |
|---|---|---|
| 1 | Raise every text token's lightness until it clears **7:1** on its worst surface (WCAG 2.2 SC 1.4.6 AAA) | `uos-hc` gets `-es-fg-primary #E4EFEE` (14.71), `-es-fg-secondary #B4C4C3` (9.56), `-es-trace-imminent #FF8A80` (7.57), `-es-status-bad-fg #FF9089` (7.90), `-es-heat-band-3/4 #FF8A5B / #FF8A80` (7.44 / 7.57), `-es-compute-allocated #4CC3B8` (8.07) |
| 2 | `-es-border-divider` and `-es-border-faint` are replaced by `-es-border-control` | every boundary becomes a real 3:1 boundary |
| 3 | All overlay translucency → opaque; all `-es-surface-*` steps widened by one | panels separate without shadow |
| 4 | `RADIUS_SM` → `RADIUS_0` on gauge tracks (`01-visual-language.md` §4.5 already licenses this) | sharper edges read at low vision |
| 5 | **Atmosphere forced to `off`** (§4) | no treatment of any kind |

Naming follows the id grammar with an appended modifier: `uos-hc`, `uos-amber-hc`, `uos-phosphor-hc`. This is the story family's answer to **PN-6** in `02-platform-native-themes.md` §8 — the same decision seen from the other end — and it should be settled jointly, not twice.

---

## 3. Typography

### 3.1 The bundled faces, and the two things JavaFX will not do

`01-visual-language.md` §3.2 already fixes the faces: the story family **bundles** them rather than resolving against the OS, so it renders identically on all three platforms. Mono is **JetBrains Mono** (SIL OFL 1.1, verified bundleable, licence file must ship); UI is **Inter** (⚠ licence unverified — `01-visual-language.md` **V-4**). Loaded via `Font.loadFont(InputStream, double)` (verified static method on `javafx.scene.text.Font`).

Two toolkit limits govern everything below. Both were checked in this pass.

- **No `font-feature-settings` equivalent** (verified against the JavaFX CSS Reference; also `01-visual-language.md` §1.3). JetBrains Mono ships stylistic sets `ss01`–`ss20` and character variants `cv01`–`cv99` from v2.304, and **none of them are reachable from JavaFX.** If a glyph alternate is wanted (a slashed zero, a different `l`), it must be **baked into the bundled font binary at build time** with `fonttools`, producing a project-local face, and that face must ship with the OFL licence and a renamed family so it cannot be confused with stock JetBrains Mono. Pin the upstream version: **JetBrains Mono ≥ 2.304**, which is the release that addressed the box-drawing gap problem (issue #165, closed; see §3.3).
- **No letter-spacing.** ⚠ **Important correction to `01-visual-language.md` §9.2**, which says the story theme "uses **letter-spacing** and weight, not case" for institutional headers. **Verified: JavaFX has no letter-spacing support** — `JDK-8090880` ("[CSS] Add css property `-fx-letter-spacing`") is an open enhancement request, and `JDK-8092100` ("Text needs to support letter spacing") documents the same gap in the scene graph. The remedy in §9.2 is therefore not implementable as written. See §3.4 for what replaces it and **SK-4** for the correction that `01` needs.

### 3.2 What each face is for in this family

`01-visual-language.md` §3.5 already makes mono mandatory for every comparable number, identifier, hash, log line, terminal surface and tabular value, and it is a *technical* requirement (no tabular figures without a monospaced face), not a stylistic one. The story theme extends mono's territory by exactly one class of content, and no further:

| Content | Native family | Story family | Why |
|---|---|---|---|
| Everything in `01-visual-language.md` §3.5 | mono | mono | unchanged — this is the contract |
| Window header strips, status lines, prompts, rules, banners (§5) | UI face | **mono** | these are *machine chrome* in the fiction; a proportional status line reads as an application, a mono one reads as a session |
| Headings, buttons, menus, settings, error messages, empty states | UI face | **UI face** | unchanged. Mono for running prose is a legibility cost with no compensating benefit (`01-visual-language.md` §3.5) |
| Teaching-layer definitions (`es-term` popovers) | UI face | **UI face** | non-negotiable: this is explanation, and `00-client-overview.md` §5.4 forbids the layer from becoming decoration |
| Recovered narrative content (`es-state-recovered`) | per artefact | per artefact | logs and database records are mono; recovered *emails* are UI face because a person wrote them (`../design/14-world-and-narrative.md` §3) |

That last row is the theme doing narrative work for free. A recovered system log looks machine-authored; a recovered email looks typed by a human being who had a bad morning. `../design/14-world-and-narrative.md` §6's "the scariest recovered document is a routine memo" only lands if a memo *looks like a memo*.

### 3.3 Box-drawing and block-element characters

JetBrains Mono covers Box Drawing (U+2500–U+257F) and Block Elements (U+2580–U+259F) — verified, with the caveat that the block's full-height rendering was a known defect (`JetBrains/JetBrainsMono` issues #37 and #165, the latter closed and addressed by v2.304). Because a rule made of glyphs is only as good as the face's vertical metrics, the usage rule is drawn tightly:

| Use | Verdict | Mechanism |
|---|---|---|
| Rules and boxes **inside a mono text stream** — the `terminal` buffer, `es-log-line` runs, `recon` readers, `audit` sub-tables, banners | **Allowed** | literal characters in the text content, since the surrounding content is already a character grid |
| Structural dividers between **panels, windows, sections, table headers** | **Forbidden** | a 1px `Region` / `Separator` in `-es-border-divider` — see below |
| Progress and meter fills | **Forbidden** | the `es-gauge` primitive (`01-visual-language.md` §8.1). Never `█▓▒░` — a block-character meter cannot be sub-character-accurate, and `../design/05-hacking-minigame.md` §4 requires the trace meter's true rate |

Four reasons structural rules are not glyphs, and they are all failures we would otherwise ship:

1. **They cannot align to the 4px grid.** Rule position becomes a function of the resolved face's advance width and line box, so `SPACE_4` stops meaning 16px (`01-visual-language.md` §4.1).
2. **They do not respond to density.** `es-density-compact` changes padding and row height (`01-visual-language.md` §4.4); a glyph rule changes neither and drifts out of alignment.
3. **They inherit a font bug.** The exact defect above — a vertical bar that does not reach the cell edge — turns a box into a dotted line at some sizes.
4. **Screen readers read them.** A `─────` run becomes a mouthful of nothing. Any decorative box-drawing node must be excluded from focus traversal and must not contribute to any control's `accessibleText` (`01-visual-language.md` §6.4's rule generalised). Where a glyph rule *is* allowed — inside a text stream — the stream's accessible representation must skip the decoration line entirely.

Where a rule *is* drawn as a `Region`, the story theme is still allowed to look like a terminal: **1px `-es-border-divider` for a light rule, 2px `-es-border-control` for a heavy one, with 8px (`SPACE_2`) end insets** so it reads as a drawn rule rather than an edge-to-edge separator.

### 3.4 Banners, and the letter-spacing problem

The figlet-style banner is the family's one piece of pure ornament, and it earns its place at exactly three moments: **first-run**, **sign-in**, and the **`terminal` window's idle state** before a session starts. Nowhere else — a banner over a live tool window is screen area spent on a logo.

```
              ▄▄▄  ▄▄▄
       █  █ █   █ ▀▄▄
       ▀▄▄▀ ▀▄▄▄▀ ▄▄█
       uOS  ·  operator console  ·  0.1.0
```

Rules:

- Banners are **pre-authored string constants**, not generated at runtime. No figlet library, no font-to-ASCII conversion — that is a dependency and a rendering surprise for one decorative string.
- A banner is a single `Text` node with `accessibleText` set to its **plain meaning** (`"uOS operator console, version 0.1.0"`) and never to its glyph content.
- Banners never carry a value. A banner showing the player's compute or heat would be a readout in a costume, and §1.3 forbids it.
- Under `uos-*-hc`, or with atmosphere `off` (§4), the banner is replaced by a `TYPE_TITLE_2` heading with the same accessible name. It is decoration; decoration is the first thing to go.

**Letter-spaced institutional headers**, which `01-visual-language.md` §9.2 called for and which JavaFX cannot do (§3.1), are replaced by three mechanisms that are all implementable and all cheaper:

| Wanted effect | Replacement | Note |
|---|---|---|
| Tracked-out header (`E Y E   D I R E C T O R A T E`) | weight 600 + `TYPE_CAPTION` + `-es-fg-secondary` + a 2px `-es-border-control` rule beneath at `SPACE_1` | reads institutional without touching glyph advance |
| Wide bracketed label | the `es-bracket` decoration class (§5.2) — literal `[ ` / ` ]` with `SPACE_1` inner padding | the brackets supply the width |
| Genuinely spaced display text, e.g. a banner subtitle | per-glyph `Text` nodes in an `HBox` with `SPACE_HAIR` gaps, **only** on nodes that carry no data and whose container sets `accessibleText` to the plain string | one node per character; acceptable for ≤ 40 characters, once, on a first-run surface. Never on a label, never on a value, never in a list |

Inserting U+2009 THIN SPACE or U+200A HAIR SPACE between characters is **banned**: it corrupts the accessible name, breaks copy-paste, and breaks find-within-window (`Shortcut+F`).

### 3.5 Prompts, cursors, and where proportional type survives

**Prompt strings** are mono, `TYPE_MONO_BODY`, and follow a fixed grammar (§5.4). The prompt sigil (`$`) is `-es-fg-tertiary`; the host/context segment is `-es-fg-secondary`; typed input is `-es-fg-primary`.

**The block cursor** is the family's signature and its trickiest detail. JavaFX's text caret is drawn by the `TextInputControl` skin and there is **no documented CSS property for caret shape or blink rate** — ⚠ unverified as an absolute; see **SK-5**. Until that is settled, the safe split:

| Surface | Cursor | Why |
|---|---|---|
| A real input the player is typing into (`terminal` command line, any `TextField`) | **the platform caret, unmodified** | replacing it risks IME composition, selection handles and the accessibility caret. A block cursor is not worth an input method regression |
| The idle prompt line when `terminal` is not focused | a decorative block `Region`, 1 character cell wide, `-es-fg-primary` at 60% opacity, **solid, not blinking** | it is a "ready" marker, not a caret |
| An output stream awaiting a server response | a decorative block, blinking at **0.83 Hz** (0.6 s on / 0.6 s off) | this is the diegetic form of `es-state-pending` and it composes with the AuthorityBadge, never replaces it |

Blink is bounded by three rules that §9.4 justifies: ≤ 1 Hz (far below WCAG's three-flashes-per-second), one character cell in area, and **solid under reduced motion or atmosphere `off`**.

**Proportional type survives in five places**, and this list is exhaustive: settings and every preferences pane, teaching-layer definitions, error and refusal messages, empty-state copy, and recovered human-authored narrative. Four of those five are on the plain-language list in §6.4 — which is not a coincidence. **Where the fiction stops, the terminal typeface stops with it.** That correspondence is itself a usable signal: if the text is proportional, it is the program talking to you honestly.

---

## 4. Surface treatment — the atmosphere layer

### 4.1 The conflict with `00-client-overview.md`, stated plainly

`00-client-overview.md` §3.3 says the story theme *"deliberately is not"* scanlines, CRT curvature, glitch or typewriter reveal, and §7 repeats it as a non-goal: *"No CRT curvature, scanlines, chromatic aberration, glitch effects, film grain, or animated typewriter reveals on readable text."* This document was commissioned to specify exactly that vocabulary. The conflict is real and it is not resolved by ignoring either side.

Both documents are `[PROPOSAL]`, so neither is binding — but the *reasoning* in §3.3 is correct and survives: **every one of those effects is a legibility tax paid for atmosphere, and C5 outranks atmosphere.** What §3.3 gets wrong is treating a spectrum as a boolean. A 6% scanline on a chrome surface that holds no text is not the same artefact as a CRT filter over a trace meter, and banning both with one sentence throws away the cheap half.

**The resolution proposed here**, which preserves §3.3's shipped result exactly:

> Effects exist, are fully specified, are **scoped away from every surface that holds data**, and are governed by one player setting, `atmosphere`, whose **default is `off`**. At `off` — the default, the first-run state, and the state of every high-contrast variant — the client looks precisely like the theme `00-client-overview.md` §3.3 describes. Nothing about the shipped default changes; what changes is that a player who wants the CRT can have a bounded, measured version of it.

`00-client-overview.md` §7's non-goal wording needs amending to match, and that amendment is **SK-6** — not something this document performs on another document.

### 4.2 The three surface classes

Every effect below is scoped by which class of surface it may touch. This is the mechanism that makes the whole layer safe, so it is defined before the effects are.

| Class | Members | May be treated? |
|---|---|---|
| **Chrome** | `-es-surface-base` and `-es-surface-raised` in regions holding **no** text, no meter and no control: window padding, header strips, rule gutters, empty panel area, the banner field | **Yes**, within the level's ceiling |
| **Data** | `-es-surface-sunken` (every log well, table body and terminal buffer), every `es-gauge` track and fill, every `es-stat`, `es-ledger-row`, `es-log-line`, `es-item-card`, `es-node`, `es-chip`, `es-gate-badge`, `es-authority` | **Never.** Not at any level, not in any variant, not during any escalation state |
| **Narrative** | recovered-content panes (`es-state-recovered`), the Eye institutional pane (§6.5) | **Authored per artefact**, never player-configurable, always within the same contrast floor |

The rule in one line: **if the player reads a number off it, nothing is drawn on top of it.**

### 4.3 The effect catalogue

Each row gives the mechanism, the parameter, the range, and the default per level. "Cost" is per §8's taxonomy. Every CSS syntax claim below was read out of the **JavaFX 26 CSS Reference** in this pass, and every effect-class claim out of the `javafx.scene.effect` package summary.

#### 4.3.1 Scanlines

**Mechanism:** an additional background paint on chrome regions — a repeating linear gradient, no asset, no `Effect`, no per-frame work. Verified syntax from the JavaFX CSS Reference: `linear-gradient` accepts `[from <point> to <point>]` and a `repeat` keyword, and `-fx-background-color` on a `Region` accepts a series of paints.

```css
.es-atmosphere-chrome {
    -fx-background-color:
        -es-surface-base,
        linear-gradient(from 0px 0px to 0px 3px, repeat,
                        rgba(0,0,0,0.06) 0%, rgba(0,0,0,0.06) 50%,
                        transparent 50%, transparent 100%);
}
```

**Parameter:** line alpha. **Range** 0.00–0.10. **Period** fixed at 3px, so it survives 1.5× and 2× output scaling without moiré at integer factors. ⚠ At fractional scale factors (1.25×, 1.75×) a 3px period can alias; measure before shipping, and see **SK-7**.

**Defaults:** `off` 0.00 · `low` 0.00 · `high` 0.06.

**Too much at:** ≥ 0.10, where the lines start reading as content, or on any surface adjacent to small text — the eye tries to resolve the line pattern and the 11px `TYPE_MICRO` floor becomes unreadable. Never on `-es-surface-sunken`: that is where log lines live.

#### 4.3.2 Bloom / glow

**Mechanism:** **not** `javafx.scene.effect.Bloom` or `Glow`. Those are confirmed to exist in `javafx.scene.effect`, but neither is reachable from CSS (verified: *"JavaFX CSS currently supports the DropShadow and InnerShadow effects"*), and both force an offscreen pass over their whole input. The correct mechanism is a zero-offset drop shadow in the glyph's own colour, which **is** CSS-expressible:

```css
.es-atmosphere-glow {
    -fx-effect: dropshadow(gaussian, -es-compute-available, 3, 0.25, 0, 0);
}
```

Verified signature: `dropshadow(<blur-type>, <color>, <radius>, <spread>, <offsetX>, <offsetY>)`, blur types `gaussian | one-pass-box | two-pass-box | three-pass-box`, radius 0–127, spread 0.0–1.0.

**Parameter:** radius. **Range** 0–4px. **Scope: `TYPE_MONO_READOUT_LG` and `TYPE_MONO_READOUT` only** — the rig monitor's headline compute figure and a tool's primary readouts. Roughly ten nodes per window, all of which change at most a few times a second.

**Defaults:** `off` 0 · `low` 2 · `high` 3.

**Too much at:** ≥ 5px, where the glyph's counters fill in and `8` becomes `0`. **Categorically forbidden on:** `es-log-line`, table cells, chips, labels, and anything at `TYPE_MONO_BODY` or smaller. Beyond the per-node cost, a node under an `Effect` is rasterised into an offscreen buffer, which we expect to cost subpixel text antialiasing — ⚠ unverified for JavaFX specifically (**SK-8**), and a second independent reason to keep glow off body text.

#### 4.3.3 Vignette

**Mechanism:** one `radial-gradient` background paint on the `Stage` root only. Static, one paint, zero per-frame cost — the cheapest atmosphere in the catalogue and the one that most reliably reads as "screen."

**Parameter:** corner alpha. **Range** 0.00–0.24. **Defaults:** `off` 0.00 · `low` 0.10 · `high` 0.18.

**Too much at:** any value where a control in a window corner loses contrast. The check is mechanical: measure `-es-border-control` against `mix(-es-surface-base, black, α)` and require ≥ 3:1. At α = 0.24 on `uos` that still holds; above it, it does not.

#### 4.3.4 Grain / noise

**Mechanism:** a single 128×128 pre-generated PNG of monochrome noise, tiled via `-fx-background-image` with `-fx-background-repeat: repeat` (verified syntax), composited at low opacity on chrome regions. One texture upload, no per-frame work, ~6 KB on disk. **Never** generated at runtime and never animated — animated grain is a full-surface repaint every frame and is the single most expensive thing in this catalogue.

**Parameter:** tile opacity. **Range** 0.00–0.05. **Defaults:** `off` 0.00 · `low` 0.00 · `high` 0.03.

**Too much at:** ≥ 0.06, where it competes with `-es-border-faint` and dense tables start to shimmer.

#### 4.3.5 Chromatic aberration

**Recommended against, and specified anyway so that the recommendation is informed.**

There is no cheap mechanism. The candidates are `DisplacementMap` or a `Blend` of channel-separated copies — both are `Effect`s over a whole subtree, both re-render offscreen every pulse, and both destroy small-glyph legibility (an aberrated 13px mono `8` is not an `8`). What people actually recognise as "chromatic aberration" in cyberpunk interfaces is applied to *headers and decorative rules*, not to body text — so that is the only place it is permitted:

**Mechanism:** two additional copies of a **decorative, non-informational** node (a banner, a heavy rule, a header ornament) at ±1px horizontal offset, tinted cyan and magenta, at 35% opacity, with `-fx-blend-mode: screen` (verified as an accepted `-fx-blend-mode` value). Three draws of one small static node, cached (§8.2).

**Parameter:** offset. **Range** 0–1px. **Defaults:** `off` 0 · `low` 0 · `high` 0 — **off at every level.** It is available only behind an explicit, separately-named `atmosphere.aberration` toggle that the appearance pane marks as experimental.

**Too much at:** any application to text the player reads, any application to a control, any offset above 1px. If this ships at all it ships as a curiosity; **SK-9** tracks whether it ships.

#### 4.3.6 Flicker

**Mechanism:** a luminance modulation of a chrome region's background paint. Not an `Effect`; a CSS transition on the background colour, or a short `Timeline` on a single `Region`'s fill.

**Parameter:** amplitude, as a fraction of relative luminance. **Range** 0.00–0.04. **Frequency ≤ 1 Hz. Duration ≤ 1.2 s per occurrence. Frequency of occurrence ≤ once per 60 s.**

**Defaults:** `off` 0.00 · `low` 0.00 · `high` 0.03 — and at `high` it is an **event**, not ambience: it fires only on the escalation triggers in §7.2, never on a timer.

**Too much at:** anything approaching WCAG's thresholds. Full justification in §9.4; the short version is that 0.04 amplitude at ≤ 1 Hz is an order of magnitude inside both the general flash threshold and the three-flashes rule, and the 1.2 s cap keeps it under SC 2.2.2's five-second trigger.

#### 4.3.7 Phosphor persistence, CRT curvature, glitch displacement, typewriter reveal

**Not implemented, at any level, and this is a decision rather than a deferral.**

- *Persistence* (a trailing smear behind moving content) requires per-frame accumulation into a `Canvas` or a snapshot chain. Its cost scales with window count, which §8's budget forbids outright, and the only things that move in this client are meter fills — which `01-visual-language.md` §7.2 requires to move at their true rate. A smear on a trace meter is a lie about rate.
- *Curvature* requires `PerspectiveTransform` over the scene root: an offscreen pass over every pixel of every window, every pulse, and it makes edge text sub-pixel-misaligned by construction.
- *Glitch displacement* moves content the player is reading. `01-visual-language.md` §7.3 already forbids anything that moves a number.
- *Typewriter reveal* is banned outright by `00-client-overview.md` §7 and §3.3 is right about it: text the player needs is text the player needs now. **Recovered narrative content is not an exception** — a player re-reading a memo for a Traversal-class human-read step (`../design/05-hacking-minigame.md` §3.2) is doing work under a timer.

### 4.4 The atmosphere setting

| | `off` (**default**) | `low` | `high` |
|---|---|---|---|
| Scanlines | 0.00 | 0.00 | 0.06 |
| Glow radius (readouts only) | 0 | 2px | 3px |
| Vignette | 0.00 | 0.10 | 0.18 |
| Grain | 0.00 | 0.00 | 0.03 |
| Aberration | 0 | 0 | 0 (separate experimental toggle) |
| Flicker | 0.00 | 0.00 | 0.03, event-driven only |
| Per-pulse cost | 0 ms | 0 ms | 0 ms (all static paints; see §8.2) |

- **The setting lives in Settings → Appearance, beside the theme picker**, and in the command palette as `atmosphere off | low | high`. It is a plain-language control on a plain-language surface (§6.4).
- It is **client-owned state** (`00-client-overview.md` §1.1) and persists per profile.
- It applies **instantly, without animation, without reflow** — the same two constraints `00-client-overview.md` §4.3 puts on the theme switch, for the same reason.
- It is forced to `off` by: any `-hc` variant, OS reduced-motion (for flicker only; static treatments are unaffected — see §9.3), the automatic degradation ladder (§8.3), and **any live breach** (§7.4).
- It has **no effect whatsoever in the native family.** `02-platform-native-themes.md` owns that family and native means native.

---

## 5. Layout signatures

These are what make a window read as a session rather than a dialog, and none of them carries data.

### 5.1 Decoration classes

These are **not** primitives in the sense of `01-visual-language.md` §8, which specifies *data display* primitives. Nothing here displays data — that is exactly why they are a separate, clearly-labelled namespace rather than an expansion of the primitive set. They follow `01`'s class grammar (§1.2) and are raised for adoption as **SK-1**.

| Class | What it is | Story rendering | Native rendering (**mandatory** — `00-client-overview.md` §3.4) |
|---|---|---|---|
| `es-rule` | A horizontal division inside a panel | 1px `Region`, `-es-border-divider`, `SPACE_2` end insets | 1px `Separator`, `-es-border-divider`, full width |
| `es-rule-heavy` | A section boundary | 2px `Region`, `-es-border-control`, `SPACE_2` end insets | 1px `Separator` + `SPACE_4` above |
| `es-banner` | ASCII wordmark (§3.4) | pre-authored `Text`, mono, `-es-fg-secondary` | `TYPE_TITLE_2` heading, same accessible name |
| `es-statusline` | The window's bottom strip (§5.3) | mono `TYPE_MONO_CAPTION` on `-es-surface-raised`, 24px tall | same content, UI face `TYPE_CAPTION` |
| `es-statusline-segment` | One field within it | separated by ` · ` in `-es-fg-tertiary` | separated by `SPACE_4` |
| `es-prompt` | A prompt string (§5.4) | mono, sigil in `-es-fg-tertiary` | plain label, UI face |
| `es-cursor` | Decorative block cursor (§3.5) | block `Region`, one cell | not rendered |
| `es-bracket` | Bracketed label wrapper (§5.2) | literal `[ ` / ` ]`, `-es-fg-tertiary` | no brackets; `-es-fg-secondary` label |

The native column is not a courtesy. `00-client-overview.md` §3.4 says a theme may change how things look but never what exists or where it is, and the §3.4 acceptance test is that **a screenshot of the same window in both themes contains the same words and numbers in the same places**. A status line that exists only in the story theme would fail that test, so the status line exists in both and only its dress changes.

### 5.2 Bracketed labels

Square brackets are the family's punctuation. They mark *machine-classified* fields — a category, a state, a source — and are never applied to free text or to a value.

```
[recon]   [T2]   [rootkit-wrapped]   [tier 3]   [no ledger entry]
```

Rules: the bracket glyphs are `-es-fg-tertiary`, the content keeps its own token; brackets never wrap a number; the accessible name of a bracketed chip is its **content only**, never the brackets. Under the native family the brackets are dropped and the label sits at `-es-fg-secondary` — identical information, different dress.

### 5.3 The status line

Modelled on `tmux` and `vim`: one strip at the bottom of every tool window, always the same shape, so a player's eye learns one location. It is the family's single strongest legibility asset, because it makes **C2** ("compute is never off-screen") true in *every* window rather than only in `rig-monitor`.

**Grammar** — five segments, left to right, fixed order, separated by ` · `:

```
[<window-id>]  <context>  │  <compute>  <noise>  <heat>  │  <authority>  <clock>
```

**Rendered**, using the number formats fixed by `01-visual-language.md` §9.3:

```
[terminal]  operator@cell-07  ·  72 / 100 cycles  ·  43 · moderate  ·  PERSONAL · moderate  ·  21:04:33
[ledger]    2,481 rows        ·  72 / 100 cycles  ·  43 · moderate  ·  PERSONAL · moderate  ·  14s ago  21:04:19
[map]       17 nodes · 4 mapped ·  72 / 100 cycles ·  43 · moderate  ·  PERSONAL · moderate  ·  21:04:33
```

| Segment | Content | Token | Rule |
|---|---|---|---|
| window id | the id from `00-client-overview.md` §6.1, bracketed | `-es-fg-secondary` | the only place the raw id is shown; it is what `Shortcut+K` accepts |
| context | window-specific: connected host, row count, node counts | `-es-fg-secondary` | one field, never two |
| compute | `available / total cycles` | `-es-compute-available` | **mandatory in every window.** Never abbreviated away |
| noise | `<scalar> · <band>` | `-es-noise-fg` | mandatory |
| heat | `PERSONAL · <band>` | the band's `-es-heat-band-n` | scope label mandatory (`01-visual-language.md` §2.2.4). `SERVER · <band>` appears here only in `identity` |
| authority + clock | `es-authority` state, then `HH:mm:ss` | `-es-authority-*`, `-es-fg-secondary` | when authority is `stale`, its age precedes the clock, per `01-visual-language.md` §8.4 |

Hard constraints:

- **The status line is `es-density`-aware but never truncates a segment.** If the window is too narrow, segments wrap to a second 24px row; they are never elided. Dropping the compute segment to save 60px would break C2 in the one place C2 is cheapest to serve.
- **It is a mirror, never a control.** Nothing in it is clickable. A control in a status strip is a 24px hit target and `01-visual-language.md` §4.3 puts the floor for a time-critical control at 32px.
- Values in it are ordinary server-owned values and carry the ordinary authority states. A status line showing `72 / 100` from four seconds ago while marked `confirmed` would be C4 violated in the most-glanced-at pixels in the client.

### 5.4 Prompts and window titles — the honest split

`Stage.setTitle(...)` feeds the OS window list, alt-tab, the taskbar/Dock, window-manager rules and the platform accessibility tree. That string belongs to the operating system and it must be plain:

```java
stage.setTitle("Terminal — The Eye and Sickle");   // every theme, every platform
```

The diegetic prompt lives **inside our own pixels**, in the window's header strip, where it costs nothing anyone else depends on:

```
operator@cell-07:~/terminal $
```

**Grammar:** `<handle>@<server-id>:<path> $`, where `<handle>` is the player's active handle or Burner Handle (`../design/glossary.md`), `<server-id>` is the home server, and `<path>` is `~/<window-id>` — so the prompt teaches the window id, which teaches the command palette, which is **C6** compounding at zero cost. When a Relay Chain is active the prompt gains a hop count (`operator@cell-07:~/terminal [3 hops] $`), because that is a real compute reservation the player is paying per hop (`../design/01-core-resources.md` §1.1) and the prompt is where a Unix user expects to see their context.

Under a Burner Handle the prompt shows the burner, never the real handle. That is not styling — it is the whole point of a burner, and a prompt that leaked the real handle would be a gameplay bug wearing a theme's clothes.

---

## 6. Diegetic framing

### 6.1 The premise

**In fiction, uOS is the hardened operator console a Sickle cell would actually run** (`00-client-overview.md` §3.3 — hence a distribution name rather than a colour-scheme name). `../design/14-world-and-narrative.md` §2 says The Sickle is decentralized *because* the game is federated, and that each home server is a cell. This theme is the same fact seen a third way: the client looks self-built because it is, in the fiction, self-built.

That premise buys three things no amount of art can:

1. **It explains the multi-window layout in-fiction.** Thirteen separate tools is what a person running their own kit ends up with (`../design/00-vision-and-pillars.md` §6).
2. **It explains the Unix vocabulary in-fiction**, which is what makes the educational layer feel discovered rather than taught (`00-client-overview.md` §5, C6).
3. **It gives The Eye something to intrude *on*.** A generic UI cannot be violated; a console with a house style can (§6.5, §7.2).

### 6.2 Three zones, and the rule that assigns them

| Zone | Definition | Treatment |
|---|---|---|
| **Diegetic** | Surfaces that, in fiction, *are* the operator's tooling | Full prompt/status-line/box-drawing/bracket vocabulary. Terse machine register (`01-visual-language.md` §9.1) |
| **Semi-diegetic** | Surfaces that present recovered artefacts | The artefact is rendered in **its own** register (§6.5); the surrounding reader chrome is diegetic |
| **Plain** | Surfaces where the program speaks to the player as a program | No prompt, no brackets, no ASCII rules, no atmosphere. UI face, sentence case, plain words |

The assignment rule, which decides every case that comes up later:

> **If the surface would still need to work when the fiction has failed the player, it is plain.**

A player who cannot read the screen, cannot connect, cannot sign in, or has just lost something they care about is a player for whom the fiction is not currently helping. Every one of those states must be met by a program that stops performing.

### 6.3 Per-window treatment

All thirteen window ids from `00-client-overview.md` §6.1, with the Unix analogue that anchors each one's register.

| Window | Unix analogue | Zone | Diegetic treatment |
|---|---|---|---|
| `rig-monitor` | `top` | Diegetic | A `top`-style header block: uptime, load as `allocated/total`, then the per-consumer allocation table with a header row. **The headline compute figure is `TYPE_MONO_READOUT_LG` and is the one node that may glow.** No banner, no ornament — this window is always on top and always being glanced at |
| `audit` | `ps` / `netstat` / `df` | Diegetic | Three column-aligned tables with `ps`-style headers (`PID  OWNER  CYCLES  STATE  COMMAND`). **The data must be real and consistent** — Established, `../design/04-mining.md` §3.1 — so this window gets the *least* decoration in the client: the discrepancy that reveals a rootkit-wrapped miner is found by reading columns against each other, and nothing may sit between the player and those columns |
| `map` | `traceroute` | Diegetic | Graph view primary; a `traceroute`-style hop list as an equal-weight alternate view (which is also the keyboard-navigable one, §9.5) |
| `terminal` | a shell session | Diegetic | The fullest expression: banner when idle, prompt, block cursor, `dmesg`-style output stream, box-drawn layer separators inside the buffer |
| `recon` | `less` over recovered files | Semi-diegetic | A `less`-style pager with a `:`-prompt footer; the artefact itself renders in its own register (§6.5) |
| `mining` | a miner dashboard | Diegetic | Block progress as a labelled `es-gauge-block`; deployed network as a table with buffer fullness per row |
| `storage` | `ls` across three mounts | Diegetic | Three mount points with `df`-style capacity headers, sorted **by exposure**, not by type (`00-client-overview.md` §6.1) |
| `ledger` | a transaction log | Diegetic | Fixed-column log, newest last, `grep`-style filter field. Every field selectable (`01-visual-language.md` §8.5) |
| `market` | a package manager | Diegetic | Package-manager register: name, version-ish tier, size-as-compute, and the **blocking gate stated in words** per item |
| `botnet` | `jobs` / `systemctl` | Diegetic | Unit-list register: one row per instance with state, backlog timer and socketed tools |
| `defense` | a firewall / IDS console | Diegetic | Rule-list register: armed defenses, their permanent compute draw, canary state |
| `identity` | `whoami` / `id` | Diegetic | A `whoami`-style block: handle, DID, `PERSONAL` and `SERVER` heat, faction reputation, burner handles |
| `switcher` | `jobs` | Diegetic | A `jobs` list: `[n]` index, window id, state, headline value. `[n]` matches `Shortcut+1…9` — the bracket notation *is* the shortcut hint |

### 6.4 Where diegesis stops — the hard list

**Every surface below is plain-language, UI face, sentence case, atmosphere-free, in every variant, always.** This list is closed: adding to it is fine, removing from it is a decision that needs the same scrutiny as changing an invariant.

1. **Settings, every pane.** Including Appearance, where the theme and atmosphere controls live. A player must never be unable to find the control that turns off the thing they cannot see past. If audio lands (**CL-7**), the volume control is on this list before it is written.
2. **Every accessibility control**: theme, variant, high contrast, density, teaching level, atmosphere, reduce-effects, reduced-motion override, docked-layout toggle.
3. **Sign-in and the AT Proto OAuth flow.** An authentication surface that looks like a prop is a phishing-training surface. It is plain, and it names the server it is authenticating against.
4. **Every error, refusal and connectivity message** (`01-visual-language.md` §9.4). *"The Eye is watching your connection"* for a socket timeout is not atmosphere, it is a lie about a fact the player needs, and it converts a diagnosable problem into a mystery. A refusal names its author; a connection failure names the connection.
5. **Every authority state.** `pending`, `stale`, `unknown` are the visible form of I14 and they render exactly as `01-visual-language.md` §8.4 specifies, undressed, in every theme.
6. **Crash, recovery and corrupt-profile fallback.** `00-client-overview.md` §4.5: a corrupt preferences file must never block sign-in — and the message that says so must be readable by someone who has never played.
7. **Destructive confirmations**: abort a breach (`Shortcut+.`), kill a miner, install Ghost Protocol, forfeit faction tools. The consequence is stated in plain words with the actual cost. `../design/01-core-resources.md` §4.3 makes Ghost Protocol deliberately painful; the confirmation's job is to make sure the player knows that, not to sell it.
8. **The teaching layer, entirely** — every `es-term` popover, every `man <term>` result, the term index. `00-client-overview.md` §5.3's honesty rule requires each entry to declare whether it is `real`, `real, simplified`, or `game`; a definition delivered in-character cannot make that declaration credibly.
9. **First-run and onboarding**, until the player has chosen a theme.
10. **Any surface stating a real-world fact**: licence text, version, credits, the federation server's address and operator.

### 6.5 The Eye's institutional register

`00-client-overview.md` §3.3 proposes the diegetic Eye skin and tracks it as **CL-3**; this section supplies what it would look like in the story family, without deciding whether it ships.

`../design/14-world-and-narrative.md` §6: *The Eye never monologues. Its menace is bureaucratic and total.* So the skin is not menacing — it is **correct**. Institutional, referenced, dated, filed.

| Property | Value |
|---|---|
| Pane surface | `#131A21` (cold blue-grey; 1.11 step from `-es-surface-base`, so a **1px `-es-faction-eye` border is mandatory** to separate it) |
| Body | `#C8D4E0` — 11.65:1 |
| Secondary / metadata | `#93A4B6` — 6.87:1 |
| Rules | `#2E3B4A`, always 1px, always full width — bureaucracy does not inset its rules |
| Header / stamp | `-es-faction-eye` `#A5B4C4` — 8.29:1, plus the aperture mark |
| Type | UI face for memo bodies (a person typed it); mono for record dumps and identifiers |
| Layout | Wide left margin, reference number top-right, classification line, date line, distribution list. Every field filled |

Scope, restated from `00-client-overview.md` §3.3 and not widened: **it skins recovered content panes only.** It never touches the player's controls, meters or chrome; it obeys the same contrast floor; it is authored, not a preference. And it never appears in the *native* family's chrome either — the pane is content, and content is content in both families.

The device works because of what it does to the player's own reading: an Eye memo listing the player's handle among fourteen others, with a reference number and a routing list, is `../design/14-world-and-narrative.md` §4's "escalation the player can attribute" delivered as **typesetting**.

---

## 7. Escalation in the skin

### 7.1 What Pillar 4 gives the skin to work with

`../design/00-vision-and-pillars.md` §3 Pillar 4: *escalation feels like a trap tightening; The Eye adapts visibly and the player can attribute it.* `../design/14-world-and-narrative.md` §4 splits it into **server heat** (institutional, affects everyone) and **personal heat** (targeted, at named-hacker it is personal pursuit).

The skin has exactly three legitimate registers. Anything not on this list is not an escalation effect.

### 7.2 The three registers

#### R1 — Chrome temperature, driven by personal heat

As `personalHeat` rises through the five bands (`../design/04-mining.md` §4), `-es-surface-base` and `-es-surface-raised` take a bounded tint toward that band's `-es-heat-band-n`.

```
α = 0.015 × band      →  band 0: 0.000   band 1: 0.015   band 2: 0.030
                         band 3: 0.045   band 4: 0.060   (hard cap)
```

**Never `-es-surface-sunken`** — the data wells stay exactly neutral, at every band, in every variant. The world gets warmer; the instrument does not.

Measured at the cap (band 4, `#FF4136` at α = 0.06 into `uos`): `-es-surface-base` becomes `#191111` and `-es-surface-overlay` becomes `#221E20`. Worst text ratio anywhere in the theme drops from 4.99:1 to **4.76:1** — still above the 4.5:1 floor, with the measurement taken at the worst state rather than at rest (§7.3). The cap of 0.06 is chosen *because* 0.08 lands at 4.65 and 0.10 at 4.54: the margin, not the look, sets the number.

Transition between bands: `DUR_BASE` (180ms) with `steps(1, jump-end)` — a heat band change is an event, not a slide (`01-visual-language.md` §7.2). And it is announced by the heat chip changing, which is the actual carrier; the tint is a mood that agrees with the chip, never a substitute for it.

#### R2 — Interference density, driven by server heat

At `atmosphere: high` only, rising `serverHeat` may raise scanline alpha and grain opacity by **one step within the level's ceiling** — never above it, never at `low` or `off`, never on data surfaces.

```
serverHeat band 0–2 :  scanline 0.06  grain 0.03   (the level's defaults)
serverHeat band 3–4 :  scanline 0.08  grain 0.04   (ceiling)
```

This is the one place where *other players' recklessness is visible in your own chrome*, which is exactly the social pressure `../design/01-core-resources.md` §4.2 describes as deliberate. It is worth having and it is worth being tiny.

#### R3 — Eye intrusions, authored and event-driven

The strongest register and the most constrained. At named-hacker status, or on a scripted escalation beat, The Eye appears **in the chrome's own formats**:

| Intrusion | Where | Bound |
|---|---|---|
| An Eye-formatted line **appended** to a log stream | `terminal`, `audit`, `defense` | Appended, never inserted, never replacing a player-owned line. Marked `es-state-recovered`. Selectable and copyable like any other line |
| A propaganda payload arriving as recovered content | `recon` | It is content in a reader. It never opens itself, never raises a window, never steals focus (`00-client-overview.md` §2, C5) |
| A one-line Eye banner in a window's header strip | any diegetic window | ≤ 1 line, ≤ 4 s, dismissible, never over a meter or a control, never on `rig-monitor` |
| A single flicker event (§4.3.6) | chrome only | ≤ 1.2 s, ≤ once per 60 s, `atmosphere: high` only |

Hard limits on all four: an intrusion **never occupies a control, never covers a meter or a value, never blocks input, never persists, and never appears during a live breach** (§7.4). And per `../design/14-world-and-narrative.md` §4, an intrusion should reference the player's own actions — *"census node, 04-11, unauthorised query, handle attached"* — because that is the difference between escalation the player can attribute and difficulty going up.

### 7.3 The limit: legibility is measured at the worst state

> **The contrast floors in §9.1 are asserted against the maximum escalation state, not the resting state. If a state breaks a floor, the state's parameters are reduced. The floor does not bend.**

This is the whole of §7's safety, and it is a build-time check, not a review item: the palette test harness enumerates *(variant × atmosphere level × personal heat band × server heat band)* — 3 × 3 × 5 × 5 = 225 combinations — resolves the tinted surfaces, and asserts every text token at 4.5:1 and every boundary, fill and focus indicator at 3:1. A palette or parameter change that breaks any of the 225 fails `mvn verify`.

*(The five server-heat bands are assumed to mirror the five personal-heat bands of `../design/04-mining.md` §4, per `01-visual-language.md` §2.2.4's shared ramp. `../design/14-world-and-narrative.md` **N-1** has not yet fixed the server-heat band table; if it lands with a different count, the harness's dimension changes and nothing else does.)*

Corollaries worth stating because each is a tempting mistake:

- Escalation may not raise glow radius. Glow lands on readouts, and readouts are data.
- Escalation may not tint `-es-surface-sunken`, ever.
- Escalation may not alter any Layer 2 token. Compute does not get redder as you get hotter.
- Escalation may not add motion to any surface a value sits on.

### 7.4 Never punish a losing player twice — and the inversion that follows

The obvious failure mode of an escalation skin is that the player who is doing badly gets the hardest UI to read. That would take `../design/00-vision-and-pillars.md` §5's *"losing should feel attributable"* and turn it into "losing feels bad to look at." Two rules prevent it.

**Rule 1 — escalation is keyed to standing, never to failure.** R1 and R2 read `personalHeat` and `serverHeat` only. A failed breach, a swept network (`../design/04-mining.md` §4), a lost bot instance, a cracked miner, a broken provenance chain — none of them touches the skin. They produce **outcome surfaces** with itemised causes (`01-visual-language.md` §8, C3), which is the thing that actually makes a loss attributable. Heat is a standing the player chose to accumulate; failure is a thing that just happened to them, and the interface does not editorialise about it.

**Rule 2 — the tensest moment is the cleanest screen.**

> **When a breach is live, atmosphere drops to `off` for its duration.** No scanlines, no glow, no vignette, no grain, no flicker. When the trace meter enters `es-state-imminent`, the heat tint (R1) also drops to zero.

This inverts what a mood system would naively do, and it is the most important paragraph in this document. `../design/05-hacking-minigame.md` §4 requires the trace meter's inputs to be legible so a loss reads as *"I was too loud"*; `01-visual-language.md` §7.3 already forbids animation during a live breach. Making the screen noisier at the moment the player most needs to read four surfaces and decide which to save (**C5**, `../design/00-vision-and-pillars.md` §7) would directly manufacture the "the game decided" feeling the design is built to avoid.

And the inversion is *better fiction*, not a compromise with it. The world closing in should feel like the instrument becoming more precise, not like the instrument failing. A tool that gets clearer when things get bad is what a competent operator's kit would do — and it means the moment atmosphere returns, the player knows they are out.

---

## 8. Performance

### 8.1 The budget

JavaFX renders every `Stage` from **one render thread**, so the budget is global, not per-window. With all thirteen windows open at 60 Hz the frame budget is 16.7 ms total.

| Metric | Budget | Rationale |
|---|---|---|
| Atmosphere cost, all windows, per pulse, at `high` | **≤ 1 ms** | Every treatment in §4.3 is a static paint or a cached node. If any of them costs per-pulse time, it is implemented wrong |
| Total client render time, all windows, at rest | ≤ 4 ms | Leaves 12 ms for a live breach's meters |
| Total client render time during a live breach | ≤ 10 ms | Breach atmosphere is `off` (§7.4), so the whole 10 ms goes to meters and log throughput |
| Cost at rest with nothing changing | **0 ms** | No `AnimationTimer` may run when nothing is animating. A theme that burns a GPU on a static screen is a laptop-battery bug |
| Additional per-window cost of atmosphere | **O(1) in window count** | Nothing may scale with the number of open windows |

### 8.2 Cheap and expensive, in JavaFX terms

| Cost | Technique | Notes |
|---|---|---|
| **Free** (paint-only, no offscreen pass) | `-fx-background-color` including `linear-gradient` and `radial-gradient`, `repeat` gradients, `-fx-background-image` with a tile, `-fx-border-color`, colour changes | This is where §4.3's scanlines, vignette and grain all live, on purpose |
| **Cheap, once** | `-fx-effect: dropshadow(...)` on a small, rarely-changing node, with `setCache(true)` + `CacheHint.SPEED` (verified: `Node.cacheProperty`, `Node.cacheHintProperty`) | One offscreen blur pass, then reused until the node changes |
| **Moderate** | `-fx-blend-mode` other than the default on a small node | Forces the parent group to composite offscreen; fine for the three-node aberration hack (§4.3.5), not fine on a panel |
| **Expensive** | Any `Effect` on a large or frequently-changing subtree — `GaussianBlur`, `Bloom`, `Glow`, `ColorAdjust`, `Lighting`, `PerspectiveTransform` (all verified present in `javafx.scene.effect`) applied above the leaf level | Re-renders to texture every pulse. **A glow on a node that updates every frame is the worst of both worlds: the cache is invalidated every pulse and you pay for the blur anyway** |
| **Forbidden** | Per-frame `Canvas` redraw of a full surface, `Node.snapshot()` in a render loop, any effect whose cost scales with open-window count | This is what rules out persistence and curvature (§4.3.7) |

The caching rule follows directly: **cache the static decoration (banner, rules, vignette layer); never cache a meter.** A cached node that changes every pulse is strictly slower than an uncached one.

### 8.3 Automatic degradation

Three inputs, one ladder. All of it runs at startup and continuously; none of it asks the player anything.

| Signal | Source | Response |
|---|---|---|
| Effects unavailable on this platform | `Platform.isSupported(ConditionalFeature.EFFECT)` — **verified** public API; its javadoc states that an effect used on a platform without support *"will be ignored"* | Glow is unavailable → clamp `atmosphere` to `low` and remove the glow row from the appearance pane rather than offering a control that does nothing |
| Sustained frame times above budget | An `AnimationTimer` sampling `handle(long now)` deltas over a rolling 5 s window | If p95 frame interval > 20 ms (< 50 fps) for 5 s, **step down one atmosphere level** and post one non-modal notice naming what changed and how to undo it. Never step down twice inside 60 s; never step *up* automatically |
| Software rendering pipeline | No public API. `-Dprism.order=sw` selects it and `-Dprism.verbose=true` reports it, but neither is a supported runtime query | Do not attempt detection. `ConditionalFeature.EFFECT` plus the frame-time signal catch the real cases; guessing at the pipeline through internal classes is a `com.sun.*` dependency this client does not take |

**The player's setting is never silently overwritten.** A degradation step is a *runtime clamp* — the stored preference survives, the notice says so, and re-selecting the level restores it (and, if the hardware really cannot, steps down again with the same notice). Silently rewriting a preference is how a settings pane starts lying.

### 8.4 What the harness measures

Beyond the 225-combination contrast assertion (§7.3), the story theme's build-time checks:

1. **Startup font resolution** — every bundled face loads, and its actual family name matches what the stylesheet asks for. `-fx-font-family` takes no fallback list (verified, `01-visual-language.md` §1.3), so a failed load silently falls back to the platform default and the theme quietly becomes something else.
2. **Box-drawing coverage** — every box-drawing and block-element codepoint used by any authored banner or rule string is present in the bundled face. A missing-glyph box in the wordmark is a shipping defect.
3. **Chroma bounds** — §2.3's ≤ 48 / ≥ 60 rule, asserted per variant.
4. **Decoration-class parity** — every class in §5.1 has a native-family rendering. A story-only surface fails `00-client-overview.md` §3.4's screenshot test.

---

## 9. Accessibility guardrails

This is the risky family and it gets the tightest rules. `00-client-overview.md` §3.5 sets a floor that is *identical for both families*; everything here is that floor made specific to the things this theme does that the native one does not.

### 9.1 Contrast floors, per variant

| Class | Floor | Source | Worst measured, `uos` / `-amber` / `-phosphor` |
|---|---|---|---|
| Text and meaningful glyphs, at rest | **4.5:1** | WCAG 2.2 SC 1.4.3 (AA) | 4.99 / 5.13 / 5.08 (`-es-trace-imminent` on `-es-surface-overlay`) |
| Text, at maximum escalation tint | **4.5:1** | same, measured per §7.3 | **4.76** at heat band 4 |
| Boundaries, meter fills, focus indicators, state marks | **3:1** | SC 1.4.11 | 3.36 (`-es-noise-decay` on its track) |
| Text under any `-hc` variant | **7:1** | SC 1.4.6 (AAA) | 7.44 (`-es-heat-band-3`) |
| `-es-fg-tertiary` | *exempt* | — | 3.14 / 3.20 / 3.40 — **decorative only**, never information (`01-visual-language.md` §1.5) |

The lowest number in this table is 4.76:1, and it is the *most escalated* state of the *default* variant. That is the number to quote when someone asks whether the story theme is safe.

### 9.2 Reduce effects, and plain mode

Two distinct controls, because they answer two distinct needs:

| Control | What it does | Where |
|---|---|---|
| `atmosphere: off` | Removes all §4.3 treatment. Palette, type, prompts, status line, box-drawing and diegetic framing all remain | Settings → Appearance; `atmosphere off`; **the default** |
| **Plain mode** | Additionally: no banner (replaced by a heading), no ASCII rules inside text streams (replaced by `es-rule`), no block cursor, no brackets, no prompts, no diegetic window framing. Status line remains, in UI face. Palette and type remain | Settings → Accessibility; `plain on` |

Plain mode is not a third theme. It is the story theme with the *performance* removed — for a player who wants the palette but finds decorative characters in a screen-reader stream, or ASCII art in a magnifier, actively obstructive. It is a strictly information-preserving subtraction: `00-client-overview.md` §3.4's screenshot test still passes, because everything it removes carried no data.

**Turning plain mode on may never change a single value, label, unit, or number's precision.** If it does, something informational was hiding in the decoration.

### 9.3 Reduced motion and reduced transparency

Both honoured in this family exactly as in the native one (`00-client-overview.md` §3.5 item 6), through both mechanisms `01-visual-language.md` §7.4 specifies: `@media (prefers-reduced-motion: reduce)` in CSS (JavaFX 25+) and `Platform.getPreferences().reducedMotionProperty()` in Java (JavaFX 24+), used together so code-driven motion is *skipped* rather than shortened.

The story-specific bindings:

| Preference | Effect in this family |
|---|---|
| `reducedMotion` | Flicker (§4.3.6) disabled entirely. Block cursor becomes solid. Heat-band tint transitions become instant. **Static treatments — scanlines, vignette, grain — are unaffected**, because they do not move and removing them would be answering a different request than the one the player made |
| `reducedTransparency` | Vignette alpha → 0. Overlay surfaces become fully opaque (`01-visual-language.md` §5.5). Grain → 0 |
| `persistentScrollBars` | Scrollbars always visible in every log well and buffer — which matters here, because a terminal buffer with a hidden scrollbar gives no indication that there is history above |

### 9.4 Photosensitivity

**WCAG 2.2 SC 2.3.1 Three Flashes or Below Threshold (Level A)**, verified: *"Web pages do not contain anything that flashes more than three times in any one second period, or the flash is below the general flash and red flash thresholds."* The general flash threshold is *"a pair of opposing changes in relative luminance of 10% or more of the maximum relative luminance where the relative luminance of the darker image is below 0.80"*, and content also passes if *"the combined area of flashes occurring concurrently occupies no more than a total of .006 steradians within any 10 degree visual field on the screen (25% of any 10 degree visual field)"* — estimated as a 341 × 256 px rectangle at 1024 × 768.

Every flashing or blinking element in this family, against those numbers:

| Element | Rate | Luminance swing | Area | Verdict |
|---|---|---|---|---|
| Flicker (§4.3.6) | ≤ 1 Hz | ≤ 0.04 | chrome region | **≤ 1/3 the rate limit and ≤ 2/5 the 10% threshold.** Compliant twice over |
| Block cursor (§3.5) | 0.83 Hz | full swing | one character cell (~8 × 20 px) | Rate-compliant; area is ~0.2% of the safe-area estimate |
| Switcher alert indicator (`01-visual-language.md` §7.3) | two-step pulse at `DUR_SLOW`, **capped at three cycles then steady** | partial | one indicator | Compliant, and it stops |

**Additional self-imposed rules, tighter than the standard:**

- **Nothing red flashes, at any rate, ever.** The red flash threshold exists because saturated red is the highest-risk case, and this client's red is the trace meter — the last thing that should ever pulse.
- **Nothing full-screen flashes at any rate.** The area exemption is not relied on as a licence; it is a second margin.
- **No effect is ever synchronised across windows.** Thirteen windows flickering together turns a small-area effect into a full-screen one, which is precisely what the steradian clause measures.

**WCAG 2.2 SC 2.2.2 Pause, Stop, Hide (Level A)**, verified: moving/blinking content that starts automatically, lasts more than five seconds and is presented in parallel with other content needs a mechanism to pause, stop or hide it. Flicker's 1.2 s cap keeps it under the five-second trigger; the block cursor and the switcher pulse are each stoppable via `atmosphere: off`, plain mode, or reduced motion — three independent routes, all reachable from a plain-language surface (§6.4 item 2).

### 9.5 Identical information, and the screen-reader trap

`00-client-overview.md` §3.5 item 3 is absolute: neither theme may hide, defer, abbreviate or de-emphasise a value the other shows. Two places where this family could quietly break it, and the rules that stop it:

- **Decorative characters must not reach the accessibility tree.** Box-drawing runs, brackets, banners, prompt sigils and block cursors are decoration; they set no accessible text and are excluded from focus traversal (`01-visual-language.md` §6.4 generalised). A row whose accessible name reads *"bracket recon bracket box-drawings-light-horizontal…"* is worse than useless. This is the single easiest regression to introduce in this theme and it should be a test, not a review item.
- **Every ASCII-rendered structure has a semantic equivalent.** The `map`'s `traceroute`-style hop list is the keyboard-navigable and screen-reader-navigable form of the graph, and it is an equal-weight view rather than a fallback (§6.3). An ASCII table in `audit` is a real `TableView` with real column headers and real accessible names, drawn to look like `ps` output — never a pre-formatted text blob.

One inherited item this document cannot resolve on its own: `01-visual-language.md` §9.2 bans literal uppercase strings (because JavaFX has no `text-transform`, so uppercase would land in the accessible name and screen readers may spell it out) while §9.3 and §2.4 mandate the literal strings `PERSONAL`, `SERVER`, `CREDIT` and `DEBIT` — which this family's status line (§5.3) renders. The strings are authored, not transformed, so the mechanism §9.2 objects to is not in play; whether an authored uppercase word still needs a separately-authored accessible name is a real question. Raised as **SK-10**.

### 9.6 The acceptance tests

A story variant ships when all of these pass, and not before:

1. All 225 escalation combinations clear §9.1's floors (§7.3).
2. The greyscale test (`01-visual-language.md` §2.4) passes on `rig-monitor`, `ledger`, `map`, `storage`, `audit`.
3. Plain mode changes no value, label, unit or precision anywhere.
4. Every decoration class has a native-family rendering and contributes nothing to any accessible name.
5. Every window's screenshot in `uos` and in `native` contains the same words and numbers in the same places (`00-client-overview.md` §3.4).
6. Atmosphere at `high`, all thirteen windows open, static screen: **0 ms per pulse**.
7. Colour-vision simulation on the five heat bands and the three storage tiers, with the mandatory pips and padlocks present (corroborating **V-8**).

---

## 10. Open questions

Deliberately undecided here. Log in `../design/15-open-questions.md` §2 if this doc set is adopted. Prefix `SK-` is chosen to avoid collision with `OQ/P/D/S/N/E/A/G/W/Q` (design), `CL-` (`00-client-overview.md`), `V-` (`01-visual-language.md`) and `PN-` (`02-platform-native-themes.md`).

- **SK-1: Adopt the decoration-class namespace (§5.1)?** `es-rule`, `es-rule-heavy`, `es-banner`, `es-statusline`, `es-statusline-segment`, `es-prompt`, `es-cursor`, `es-bracket` follow `01-visual-language.md`'s class grammar but are not in its primitive list, which is explicitly about *data display*. Either `01` adopts a "decoration classes" section or this document keeps them theme-scoped. Decide before the first tool window is styled, because eight class names are cheap to rename now and expensive later.
- **SK-2: Do `uos-amber` and `uos-phosphor` ship in v1?** They extend `00-client-overview.md` §3.3's two-variant list. The palette work is done and verified; the cost is two more variants in the 225-combination harness and two more in every screenshot review. A defensible v1 ships `uos` + `uos-hc` only and adds the phosphors once the window catalogue is real.
- **SK-3: Is a true single-phosphor "purist" mode worth building?** §2.3 rules it out for the default because it collapses seven load-bearing hues. It becomes possible if every meter fill gets a hatch pattern and every chip a glyph — which is work `01-visual-language.md` §2.4 half-mandates anyway. Revisit after the greyscale test has been run on real windows, since that test is the same audit.
- **SK-4: `01-visual-language.md` §9.2 needs correcting.** It prescribes letter-spacing for institutional headers; **verified** that JavaFX has no letter-spacing (`JDK-8090880`, `JDK-8092100`, both open enhancements). §3.4 here supplies three implementable replacements. `01` owns the fix; this document must not perform it.
- **SK-5: ⚠ Can the JavaFX text caret be restyled or replaced without breaking IME, selection and the accessibility caret?** §3.5 assumes not and keeps the platform caret in real inputs, which costs the block cursor exactly where it would be most satisfying. Worth thirty minutes with `TextInputControlSkin` before accepting the compromise permanently.
- **SK-6: Amend `00-client-overview.md` §3.3 and §7's non-goal wording.** Both currently ban scanlines, aberration and grain outright; §4.1 here proposes they are permitted as a default-off, data-surface-excluded, budget-bounded layer. The *shipped default is unchanged either way* — this is a wording decision about what the client is allowed to offer, and it should be made explicitly rather than inherited from whichever document is read second.
- **SK-7: ⚠ Scanline aliasing at fractional output scale factors.** The 3px period is clean at 1× and 2×; 1.25×, 1.5× and 1.75× need measuring on real displays, and the multi-monitor mixed-DPI case in `00-client-overview.md` **CL-8** compounds it. If it aliases, the remedy is an output-scale-aware period, which is one more thing to recompute on screen change.
- **SK-8: ⚠ Does a JavaFX `Effect` cost subpixel text antialiasing?** §4.3.2 assumes a node under an effect is rasterised offscreen with greyscale AA, which is a second reason glow never touches body text. The conclusion (glow on readouts only) does not depend on the answer, but the *reason* given to future contributors should be true.
- **SK-9: Does chromatic aberration ship at all?** §4.3.5 specifies it and recommends against it: it is off at every atmosphere level and reachable only behind an experimental toggle. If nobody turns it on in playtest, delete the code rather than carrying an experimental flag forever.
- **SK-10: Do the mandated uppercase literals (`PERSONAL`, `SERVER`, `CREDIT`, `DEBIT`) need separately-authored accessible names?** §9.5 explains the apparent conflict between `01-visual-language.md` §9.2 and §9.3. Needs testing with a real screen reader on all three platforms; the answer probably differs per platform, which is itself the finding.
- **SK-11: Should the status line (§5.3) exist in the native family at full strength?** §5.1 requires it to exist so the screenshot test passes, but a native-feeling desktop app with a `tmux` status bar in every window is a slightly odd object. The alternative — a thinner native status strip with the same fields in UI face — is what §5.1 specifies, and it should be reviewed against a real native window before it is built. `02-platform-native-themes.md` owns the native side of the answer.
- **SK-12: Does the escalation tint (§7.2 R1) actually read?** α = 0.06 is a 6% mix, chosen for contrast margin rather than for perceptibility, and it may simply be invisible — in which case the register is costing 225 test combinations for nothing. Measure with players before defending the number; if it does not read, the honest answers are to delete R1 and let the heat chip carry it alone, or to move the tint to a surface that is allowed more range (the window header strip only).
