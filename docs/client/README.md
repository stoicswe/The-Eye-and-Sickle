# Client Documentation — The Eye and Sickle

This folder is the **design source of truth for the client**: what the player actually sees, touches
and learns from. `../design/` covers the game's systems and economy; `../architecture/` covers the
technical stack, servers and federation. This folder covers the surface where those two meet a human.

One boundary is worth knowing before reading `04`: this folder owns the teaching **mechanism** — tiers,
triggers, the `man` window, the file format, the CI checks. What those pages actually *say*, and
whether it is true, is the **curriculum**, and it lives in [`../education/`](../education/README.md).
`04` §2's mapping tables and `04` §3's command grammar are the specification that doc set is written
against; it cites them and never redefines them.

Read `../../CLAUDE.md` first for the invariants and conventions.

## Reading order

New to the client: read **`00` → `01`**, then whichever of `02`–`07` you are working on. `00` sets the
vision and the two theme families; `01` is the token and primitive contract that every other document
cites by name. Nothing below `01` re-defines a colour, a size or a component — they use its vocabulary.

## Document map

| # | Doc | Status | What it covers |
|---|---|---|---|
| 00 | [`00-client-overview.md`](00-client-overview.md) | ⚠️ **[PROPOSAL]** | The client's design vision, the six client pillars **C1–C6**, the two theme families, theme-selection UX, the educational layer in brief, non-goals |
| 01 | [`01-visual-language.md`](01-visual-language.md) | ⚠️ **[PROPOSAL]** | **The contract.** The three-layer token model (69 colour tokens, numeric tokens, 10 primitives, the state-class matrix), typography, spacing, elevation, motion, microcopy |
| 02 | [`02-platform-native-themes.md`](02-platform-native-themes.md) | ⚠️ **[PROPOSAL]** | The **native** family: macOS, Windows and Linux in depth; automatic adaptation to the host OS via `Platform.Preferences`; per-platform chrome, menus, shortcuts, fonts, HiDPI; a pre-ship checklist |
| 03 | [`03-story-theme.md`](03-story-theme.md) | ⚠️ **[PROPOSAL]** | **uOS** — the Unix-terminal cyberpunk skin — phosphor palettes, box-drawing, scanlines/bloom/grain with budgets, diegetic framing and where diegesis stops, escalation in the skin |
| 04 | [`04-terminology-and-education.md`](04-terminology-and-education.md) | ⚠️ **[PROPOSAL]** | The Unix vocabulary contract and the **`man`-page teaching layer**: the game↔real-world mapping tables, three-tier progressive disclosure, teaching levels, worked term entries |
| 05 | [`05-tool-windows-and-layout.md`](05-tool-windows-and-layout.md) | ⚠️ **[PROPOSAL]** | The operator's desk: the 13-window catalogue, window management and shortcuts, workspaces, the **single-window docked fallback**, and the attention ladder |
| 06 | [`06-resource-and-inventory-ui.md`](06-resource-and-inventory-ui.md) | ⚠️ **[PROPOSAL]** | Compute, ethecoin, noise/heat, storage tiers, the item card, inventory at scale, the provenance/item-history view, and cost-before-commit |
| 07 | [`07-accessibility.md`](07-accessibility.md) | ⚠️ **[PROPOSAL]** | The accessibility specification and its **testable checklist** — the document that answers a flag this project raised twice |

## Status tags — what they mean here

Nearly everything in this folder is **[PROPOSAL]**, because the source design sessions and the two
technology chats specified the client's *stack* and *shape* but never its *appearance*. What is
**Established**, and is cited inline wherever it constrains a decision:

- JavaFX + AtlantaFX, one `javafx.stage.Stage` per tool (`../architecture/01-tech-stack.md` §1).
- The client is a **view + input layer and is never authoritative** — Invariant I14. This is client
  pillar **C4**, and it has real visual consequences: the UI must distinguish a server-confirmed
  value from a pending one rather than optimistically faking authority.
- The compute readout is mandatory and always visible (`../design/01-core-resources.md` §1.4).
- A **single-window fallback layout must exist** (`../architecture/01-tech-stack.md` §1 and
  `../design/05-hacking-minigame.md` §5, both flagged for accessibility review). `05` §5 designs it
  and `07` treats it as a first-class equal, not a degraded mode.
- The resource, tool and gate vocabulary comes from `../design/glossary.md` and must not drift.

## The two theme families, in one paragraph

**Native** adapts to the host OS automatically — light/dark, accent colour and reduced-motion are read
live from the system, so flipping dark mode mid-session just works. **uOS** is the
story-atmospheric family — and uOS is not merely a skin name: it is the **operating system every
rig in the game runs** (`../design/glossary.md`), drawn as its own operator console. The rule that keeps
this from becoming two products: **only the skin changes.** Layout, information architecture,
interaction and terminology are identical across families, both satisfy the same accessibility floor,
and a theme is never permitted to hide, reorder or soften information. Atmosphere is spent on chrome,
never on data.

## The educational layer, in one paragraph

The game teaches real computing. The UI's vocabulary is genuine Unix, and an opt-out teaching layer
explains each term in `man`-page form — hover for a one-line synopsis, a keypress for a full page with
`NAME` / `SYNOPSIS` / `DESCRIPTION` / `SEE ALSO`, so players learn to read man pages by reading them.
Every entry states the game meaning, the real-world counterpart, and — where a mechanic is pure
invention with no real analogue — says so plainly. A wrong mapping teaches something false, which is
worse than teaching nothing. This is not veneer: `../design/04-mining.md` §3.1 already requires the
client to render process, connection and storage data faithfully enough that a careful player can
catch a hidden miner by noticing the numbers do not add up.

## Open questions

Each document ends with its own numbered open questions, prefixed by document:

| Prefix | Doc | Prefix | Doc |
|---|---|---|---|
| `CL-` | 00 Client overview | `WL-` | 05 Windows & layout |
| `V-` | 01 Visual language | `RI-` | 06 Resources & inventory |
| `PN-` | 02 Platform-native | `AX-` | 07 Accessibility |
| `SK-` | 03 Story theme | `T-` | 04 Terminology & education |

The ones that block implementation rather than merely refine it are summarised in
`../design/15-open-questions.md`. Two are worth knowing before reading anything else, because both
were found by *measuring* rather than assuming:

- **JavaFX has no letter-spacing** (`JDK-8090880`, open). `01` §9.2 originally prescribed it;
  `03` §3.4 supplies three implementable replacements and `01` now points at them.
- **The uOS heat ramp is luminance-flat end to end** — band 0 and band 4 differ by 1.02:1, so
  cold and named-hacker are indistinguishable in greyscale (`07` §5.4). The band name and pip count
  are therefore non-removable, and the palette fix is tracked as **AX-5**.

## A note on verification

These documents make a large number of claims about JavaFX 26, AtlantaFX 2.1, the three platforms'
design languages, and WCAG 2.2. Those were checked against primary sources — the JavaFX javadoc and
CSS Reference, AtlantaFX's published colour reference and its actual jar, Microsoft's and GNOME's
published typography guidance, and the W3C Recommendation — and every contrast ratio quoted was
computed with the WCAG relative-luminance formula rather than asserted. Where something could not be
verified it is marked ⚠ inline rather than stated flatly. The most consequential unverified item is
**AX-1**: JavaFX appears to have **no Linux screen-reader bridge** (its accessibility implementation
targets Windows UI Automation and macOS VoiceOver), which would be a genuine platform gap rather than
a bug we can fix — it needs confirming against JavaFX 26 before we make any promise to players.
