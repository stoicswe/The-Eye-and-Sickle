# 00 — Vision & Pillars

**Status:** Established (design sessions 1–5)
**Depends on:** nothing — this is the root document
**Depended on by:** every other doc in `docs/design/`

---

## 1. One-paragraph pitch

A puzzle-centric hacking game set in a surveillance dystopia. The player is an operator in **The Sickle**, a resistance coalition, working against **The Eye**, the surveillance state. Play happens through the interfaces a hacker would actually use — terminals, network maps, tool windows — and every meaningful decision is a question of where to spend scarce compute cycles. Single-player by default; optional multiplayer offers better rewards at the cost of real, uninsured loss.

## 2. The factions

- **The Eye** — the state. Systemic, patient, escalating. It does not play fair and it does not need to: its pursuit of Sickle players is automatic, institutional, and scales with how loud the resistance gets.
- **The Sickle** — a resistance coalition of *conflicting* interests: idealists, survivors, opportunists, and defectors. It is not a family and not uniformly trustworthy — the informant system (see `12-identity-and-social.md`) exists precisely because anyone might be compromised.

**Asymmetry is a rule, not flavor:** Eye pursuit of Sickle players is automatic and systemic. Sickle targeting of Eye-aligned players is objective-driven and chosen.

## 3. The five design pillars

Every system in this repo must be justifiable against these. If a proposed feature violates one, the feature is wrong, not the pillar.

1. **The puzzle is the game.** Every other system exists to give the core hacking minigame stakes, pacing, and consequence. Nothing may let a player skip it wholesale — automation is gated behind proving you can do it manually (proof-of-skill, `02-unlock-gates.md`), bots run it slower and worse (`10-botnets.md`), and zero-days that bypass it are rare, consumable, and never farmable (`06-intrusion-tools.md`).

2. **Compute is the master scarcity.** Not money. A rig has a fixed cycle budget and every activity — tools, bots, mining, defense, relays — competes for it. Compute is **never purchasable with ethecoin**, in any form, ever (see invariant list below).

3. **Progression is paced by the designer, not the grind.** Ceilings (rig capacity, permanent capabilities) are found or earned through schematics and story milestones. Money buys breadth only: consumables, replacements, horizontal options.

4. **Escalation feels like a trap tightening.** As the player wins, The Eye adapts visibly — smarter detection heuristics, targeted propaganda, false-flag bounties. Success makes the world more hostile in ways the player can see and attribute.

5. **Story is environmental.** Narrative arrives through logs, emails, and database records recovered by hacking. There are no companion characters. If a story beat can't be delivered through recovered data, it gets rewritten until it can.

## 4. Hard invariants

These are the load-bearing rules extracted from the full design. A change to any of these is a redesign, not a tweak. `CLAUDE.md` at the repo root mirrors this list.

| # | Invariant | Why it's load-bearing | Detail in |
|---|---|---|---|
| I1 † | Compute is never purchasable with ethecoin — **except the compute ladder's first rung** | Otherwise mining buys mining capacity and scarcity collapses into a compounding flywheel. One rung cannot compound; see † | `01-core-resources.md` |
| I2 | Ethecoin never buys a ceiling | Money buys breadth only; ceilings come from schematics/story | `02-unlock-gates.md` |
| I3 | Every item sits behind exactly one unlock gate | Gate assignment follows a rule, not per-item taste | `02-unlock-gates.md` |
| I4 | Self-mining is structurally immune to detection and seizure, and generates zero heat | It is the income floor; heat must have a real cost with a real bottom | `04-mining.md` |
| I5 | Self-mining and botnets stop a bounded time after the client closes; all offline income is capped, never proportional to absence | Keeps compute allocation an active bet; a longer absence must never be worth more | `04-mining.md` |
| I6 | A deployed miner consumes the host's compute, not the deployer's | If a hostile miner cost the host nothing, no one would ever spend compute on detection | `04-mining.md` |
| I7 | Proof-of-skill gates are tier-gated, never count-gated | Count gates reward farming the weakest target; tier gates reward competence | `02-unlock-gates.md` |
| I8 | Zero-days are never reliably purchasable | The moment they're farmable they answer every problem and the puzzle stops mattering | `06-intrusion-tools.md` |
| I9 | Defending your own rig never generates heat | Being wanted must come from aggression, not self-defense | `04-mining.md`, `05-hacking-minigame.md` |
| I10 | Bots assist, they never substitute — a bot never solves the puzzle for the player | Pillar 1, mechanically enforced | `10-botnets.md` |
| I11 | Bot loss destroys instances and socketed tools, never blueprints | Losing a late-game schematic to one bad session would make running bots irrational | `10-botnets.md` |
| I12 | Vault capacity scales sub-linearly and is never purchasable | Linear scaling produces unraidable late-game veterans, killing the risk economy | `01-core-resources.md` |
| I13 | Salvage/partial-progress drops are gated on engagement tier | Otherwise feeding junk bots to losses becomes a grind path to ceilings | `10-botnets.md` |
| I14 | Game state never lives in a player's PDS or on infrastructure the player controls unilaterally | Player-controlled state is the self-hosted-cheating problem relocated | `../architecture/02-identity-and-auth.md` |
| I15 | No single arbiter decides cross-server adversarial outcomes | Federation trust comes from quorum consensus + provenance, not authority | `../architecture/05-validator-quorum.md` |

## 5. Player fantasy & tone targets

- The player should feel like an *operator*, not an action hero: reading logs, watching noise budgets, deciding what to risk. Tension comes from overextension, not reflexes.
- Paranoia is a feature. Storage can be raided, miners can be planted on you, any Sickle contact might be an informant. The game should never fully confirm the player is safe.
- Winning should feel like getting away with something. Losing should feel attributable — "I got greedy," not "the dice hated me." (Correlated sweep losses are the deliberate exception; they should feel like *the crackdown finally came*, and open question OQ-3 tracks whether that lands.)

## 6. Product shape (established in tech sessions)

- **Desktop, cross-platform** (macOS / Linux / Windows), deliberately lightweight.
- **Multi-window client:** each tool is its own OS window (JavaFX `Stage`), so a player's screen ends up looking like an actual operator's desk — map here, terminal there, miner dashboard on the second monitor. See `../architecture/01-tech-stack.md`.
- **Self-hostable servers, Minecraft-style:** a "home server" anyone can run via Docker Compose, with allowlists; an opt-in federation directory for public servers; cross-server play secured by validator quorums and signed item provenance rather than a central authority. See `../architecture/03-server-and-federation.md`.
- **Identity via AT Protocol OAuth (authentication only):** the player's DID is their portable identity across servers. See `../architecture/02-identity-and-auth.md`.

## 7. What this game is not

- Not an idle/incremental game: offline income exists but is buffer-capped and never optimal (`04-mining.md`).
- Not a pay-to-win or grind-to-win economy: see I1/I2.
- Not a twitch action game: time pressure exists (trace timers, backlog timers) but the skill expression is planning, reading, and triage.
- Not a themepark MMO: multiplayer is opt-in, federated, and adversarial, with real loss.

