# 14 — World & Narrative

**Status:** ⚠️ **[PROPOSAL]** — the source design establishes only the *delivery method* (Pillar 5: story is environmental) and the *escalation shape* (Pillar 4). Everything about the actual world, factions-as-story, and progression beats below is proposed to give the game a spine to hang systems on. Tagged throughout.
**Depends on:** `00-vision-and-pillars.md` (Pillars 4 & 5), `01-core-resources.md` §4 (heat), `11-rig-infrastructure.md` (schematics as beats)
**Depended on by:** the content pipeline; `05` (flavor logs feed human-read puzzle steps)

---

## 1. Established constraints

Two pillars fully constrain the narrative approach:

- **Pillar 5 — Story is environmental.** Narrative arrives through **logs, emails, and database records recovered by hacking.** There are **no companion characters.** If a beat can't be delivered as recovered data, it must be rewritten until it can. This is a hard content-pipeline rule: every story element is *a file on a node the player breaches.*
- **Pillar 4 — Escalation feels like a trap tightening.** As the player wins, The Eye adapts **visibly**: smarter detection heuristics, targeted propaganda, false-flag bounties. Success makes the world measurably more hostile, and the player can *see* it happening and attribute it to their own success.

Also established: the two factions (`00`), the heat tiers driving world-state thresholds (`01` §4), and schematics recovered from Eye infrastructure as objectives (Firmware Implant, `11`).

## 2. [PROPOSAL] The world

A first-pass setting consistent with the surveillance-dystopia premise and the environmental-story rule:

- **The Eye** is a total-surveillance state apparatus — not a person, an *infrastructure*. It expresses itself through systems the player hacks: monitoring nodes, propaganda servers, bounty registries, citizen-scoring databases. Its "characters" are the officials whose emails you recover, never NPCs who talk to you.
- **The Sickle** is a decentralized resistance — which is *why the game is federated* (`13` §4). Each home server is a cell. There is no Sickle headquarters, only nodes, drops, and the reputations of operators. The federation model and the fiction are the same fact viewed two ways.
- **The city/net** is the graph the player navigates: nodes owned by the state, by corporations, by citizens, by other operators. Environmental story is distributed across it — you learn the world by breaching it.

## 3. [PROPOSAL] Narrative delivered as data

Concrete content types, each a breachable artifact (this is the content team's format list):

| Artifact | Delivers | Found on |
|---|---|---|
| **Email threads** | Character, motive, conspiracy | Corporate/state nodes |
| **System logs** | What happened here, who did it | Any node post-breach |
| **Database records** | Scale, structure, the machinery of control | Eye infrastructure |
| **Recovered chat/dead-drop caches** | Sickle internal politics, informant hints (`12`) | Sickle-adjacent nodes |
| **Propaganda payloads** | The Eye's escalating response to the player (Pillar 4) | Eye propaganda servers, pushed as heat rises |

**Rule for writers:** no cutscenes, no talking companions, no quest-giver dialogue trees. If the player needs to know it, they *recover* it. Ambient environmental storytelling (a log that implies more than it states) over exposition.

## 4. [PROPOSAL] Escalation as a visible system

Pillar 4 wants escalation the player can *see and attribute.* Tie it mechanically to heat (`01` §4):

- **Server heat thresholds** unlock world-state narrative beats (established hook, `01` §4.2): as the whole population's activity rises, The Eye's institutional response escalates for everyone — new propaganda, harsher baseline sweeps, new defended infrastructure appearing on the graph.
- **Personal heat** drives targeted escalation: at named-hacker status, The Eye pursues *you* specifically — personalized false-flag bounties (other players paid to hunt you), targeted propaganda naming your handle, honeypots seeded where you operate.
- **The attribution requirement:** escalation must be legible as *caused by the player.* When a crackdown lands, the player should be able to think "this is because I hit the census node last week," not "difficulty went up." Recovered logs after an escalation should reference the player's own actions — the world reacting to *you*, in writing.

> **[PROPOSAL] N-1:** Define the server-heat threshold bands and what each unlocks, as a concrete table, once there's a content plan. Should align with the five heat bands already used elsewhere (`04` §4) for designer consistency.

## 5. [PROPOSAL] Progression as narrative spine

Marrying Pillar 3 (designer-paced progression) to Pillar 5 (environmental story): **the schematic tree is the story.** Because rig ceilings come from exploration and story milestones (`11`), each major unlock is a place in the world with a story attached:

- The **Firmware Implant** is already established as "recovered from deep inside Eye infrastructure — acquiring it is itself a late-game objective" (`11`). Generalize this: **every ceiling-tier schematic is a location + a story beat + a breach**, not a shop entry.
- This makes progression and narrative the *same content*, which suits a small team: you don't build a story mode and a progression system separately; the dungeon *is* the tech tree.

> **[PROPOSAL] N-2:** Sketch the critical-path schematic beats (starting rig → named-hacker endgame) as an ordered list of "location / story / capability unlocked" triples. This is the backbone the whole single-player campaign hangs on and is the highest-value next narrative-design task.

## 6. [PROPOSAL] Tone and theme

Consistent with `00` §5:

- **Surveillance, complicity, and the cost of resistance.** The informant system (`12`) is the thematic core made mechanical — resistance eats its own out of fear, and The Eye profits from that fear without lifting a finger.
- **No clean heroes.** The Sickle is idealists *and* opportunists *and* defectors (`00`). Environmental story should keep the resistance morally textured — recovered Sickle logs should sometimes make the player uncomfortable about their own side.
- **The Eye never monologues.** Its menace is bureaucratic and total. The scariest recovered document is a routine memo treating the player as a line item.

## 7. Cross-references

- Delivery pillars: `00-vision-and-pillars.md`
- Heat that drives escalation: `01-core-resources.md` §4
- Schematics as beats: `11-rig-infrastructure.md`
- Story data feeding puzzle human-read steps: `05-hacking-minigame.md` §3.2
- Federation as fiction: `13-multiplayer-and-federation-play.md` §4
