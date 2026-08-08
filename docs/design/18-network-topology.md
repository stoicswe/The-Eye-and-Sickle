# 18 — Network Topology: shape, depth, and how a map grows

**Status:** **[PROPOSAL]** — the solo half is being built against this document; the multiplayer half
is design only and has no server to hang it on.
**Depends on:** `07-recon-tools.md` §5 (the sweep ladder), `17-bridges-and-surveillance.md` (bridges),
`02-unlock-gates.md` §1.1 (what each gate may sell), `00-vision-and-pillars.md` §4 (**I2**, **I3**)
**Depended on by:** `TopologyGenerator`, `NetRules`, `docs/client/09-network-map-graph.md`

This document answers one question the code has been answering by accident: **what shape is a
network, and how does it grow as the player works through it?** Before it, a server's internal shape
was whatever a random recursive tree produced — depth roughly the logarithm of the machine count, and
a branch factor nobody chose. The map in `docs/client/09` §8 records the measurement: *"layers are
1–5 machines wide, maps are 4–10 columns deep… fan-out does not occur at reachable depth"*. That is
the accident, described.

---

## 1. Vocabulary, because two different things are called depth

| Term | Means | Where it lives |
|---|---|---|
| **Server depth** | How many bridges from home a *server* is | `ServerState.depthFromHome`, 0–4+ |
| **Node depth** | How many hops from the server's gateway a *machine* is | emergent today; **chosen** under this proposal |

The sketch this document is written from labels the second one `Depth_0 … Depth_N`, running left to
right across one server, with `LOCALHOST` at `Depth_0`. Both matter and they do different jobs:
**node depth is how long a server takes to cross; server depth is how dangerous it is.** §4 is the
whole of that distinction.

---

## 2. Solo: a server is a tree of chosen depth

### 2.1 The three rules

1. **Each server picks a node depth of 4–13**, drawn at world generation and never re-drawn.
2. **A machine branches into 1–7 children**, drawn per machine.
3. **At least two machines on a server branch into more than one child.** A server that was one long
   chain would have no choice in it, which is the failure `TopologyGenerator`'s own class note gives
   for rejecting a chain at the *server* level — restated one level down.

### 2.2 ⚠ Depth is the target and the machine budget is the constraint

Depth and branching cannot both be free: a depth-13 tree branching 1–7 the whole way is thousands of
machines, and the brief's hard cap is fifty per server. So the construction is **spine first, then
branches**:

- the machine count comes from `Balance.netMachinesMin/Max(serverDepth)` as it already does (12–20 at
  home, up to 34–50 deep);
- a **spine** of `D` machines is laid from the gateway, which is what makes the depth exact rather
  than emergent;
- the remaining budget is spent attaching branches of 1–7 to machines already placed.

⚠ **The spine may take at most `NET_SPINE_BUDGET_SHARE` (0.6) of the non-gateway machines**, and that
number is a measurement rather than a taste. The first version of the rule was "leave two machines
over", on the reasoning that two is what rule 3 needs. It is arithmetically true and it makes a bad
server: a 13-machine home rolled depth 11, the spine took eleven of the twelve non-gateway machines,
and the whole thing rendered as **an eleven-hop chain with a single fork at the far end**. Dumped and
read, it was a corridor — precisely the shape this section exists to prevent, arrived at from the
other side.

At 0.6 there is a real budget to fan with at every size: a 12-machine home spends at most 7 on depth
and has 4 spare; a 50-machine deep server hits the depth ceiling long before the share binds and has
36 spare to spread over 13 layers.

⚠ **The share is of `count − 1`, not of `count` — the gateway is the root and is not on the spine.**
The off-by-one in the other direction is what produced the corridor above.

⚠ **The clamp is named and tested rather than silent.** A rolled 13 on a small server is not the depth
that server gets, and "the number in the save is not the number in the world" is how a tuning question
becomes a bug hunt.

### 2.4 ⚠ Chords must be same-layer, or the shape is undone after it is built

The intra-server chord pass adds ~22% more links *after* the tree is built, and it runs on every
server. Unconstrained, a single chord from the gateway to a deep machine collapses the spine the
server was built around — and **nothing in the save would show it**, because depth is not stored.

This is not a new argument. `TopologyGenerator`'s own class note has forbidden the same thing between
**servers** since it was written: *"a depth-skipping chord would shorten a BFS path and silently
re-depth a server after its machines had already been generated against the old depth"*. The rule
simply had nothing to apply to at the machine level until there was a spine to shorten.

⚠ **Same layer exactly, not "within one".** Depth preservation only needs `|Δ| ≤ 1` — but a chord to
the layer *below* is indistinguishable from a branch in the finished graph, so allowing one makes the
1–7 rule unobservable in the thing that ships. Measured: a machine with a 7-wide fan and one such
chord reads as fanning 8. **A rule nobody can check on the real object is a rule that will drift.**

### 2.3 What this changes on screen

`docs/client/09` §8 **NM-5** is the open complaint that the map's real unreadability is *horizontal* —
maps grow deep, not wide, and nothing addressed it. This proposal makes both dimensions deliberate:
depth is chosen in a published band, and fan-out is a rule rather than an accident. **The stack fold
(`NET_STACK_THRESHOLD`, built and dormant) stops being dormant** — a 1–7 branch factor produces layers
wide enough to fold, which is what it was built for.

---

## 2.5 A server has a name, and it is what a bridge advertises — **implemented**

`NpcNames.server` — **`adjective-character`**, the same scheme and the same adjective pool as a
machine's `adjective-pioneer`, hashed from the server id and de-collided by walking. It costs no RNG
draws, for the reason machine names cost none.

The characters come from Final Fantasy, Zelda, Cyberpunk 2077/Edgerunners, Cronos: The New Dawn,
Marathon, Portal, Half-Life, Death Stranding, Tomb Raider, Resident Evil, Watch Dogs, Wolfenstein,
Doom, Warhammer 40,000, Warhammer Fantasy/Age of Sigmar and Dune — **878 names** after filtering.

⚠ **Fictional where the machines' pool is real, and both halves of that matter.** `PIONEERS` is
scientists, so it carries a hard "no demeaning adjective" rule — pairing a real name with an insult
is a claim about a person. This pool is characters, so the binding rule is different:

- **No real person.** Three were dropped on review: `blavatsky` (a historical occultist), `zidane`
  (Final Fantasy IX's protagonist, and a famous living footballer — and it is the footballer a
  hostname reads as), `bohemond` (a real crusader).
- **No species.** `necron`, `pfhor` and `jjaro` were harvested and removed: `wicked-necron` names a
  race, not a person.
- **No ordinary given name and no common word.** `wicked-sam` and `wicked-paul` are not references to
  Death Stranding or Dune; they are an adjective and a name. Paul Atreides appears as `muaddib` and
  `atreides`, which are.
- **No collision with the other three pools.** ⚠ Resident Evil Village's Karl **Heisenberg** was
  dropped by that check rather than by review, and it is the sharpest case: `PIONEERS` has Werner
  Heisenberg, and a name in both pools reads as the physicist wherever it appears. Seven more went
  for colliding with the operator pool — a player who has just met an operator called `magnus` and
  then finds a server called `roguish-magnus` will reasonably think the two are connected.

⚠ **What this replaced was seven names shared by every world in existence.** `home-relay`,
`south-exchange`, `north-yard` … the same list, in the same order, on every seed — its own note
conceded it, on the grounds that "nobody replays a world for its place names". That trade stopped
being available the moment servers became **tabs** (§2.6) and a bridge advertised its far side by
name: seven shared names read as furniture. Hashing the id was free the whole time.

## 2.6 The map is one tab per server — **implemented**

`ServerTabs`. One tab per server in `NetMap.knownServers()`, **home first and the rest alphabetical**,
each laying out its own server from its own shallowest known machine.

⚠ **The tab list is what the player has heard of, never the world.** A server reaches `knownServers`
by being swept or by an identified bridge advertising it. Anything else would publish the shape of
the world for free, which is the rule `NetRules` states as *"undiscovered hosts do not exist in
`knownNodes`, and the map draws nothing where they are"*.

⚠ **A tab may legitimately be empty, and it is dimmed rather than hidden or disabled.** An identified
bridge names the server on its far side and that is all the player has until they cross it. "There is
a door there and you have not been through it" is real information — it is the entire product of the
bridge finding (`07` §5.1a) — and a disabled control still asks to be understood when the thing to
understand is "go and cross that bridge".

⚠ **Layers are rebased on the shallowest machine in the filtered map**, not on the rig. `hopsFromRig`
is measured across the whole world, so a foreign server's tab would otherwise open with four or five
empty columns and its content off the right-hand edge. The rebase is a **no-op** for the whole-world
map, which is why it lives in `NetLayout` rather than at the call site. It does **not** rewrite the
sightings: `hopsFromRig` means what it says and several other surfaces read it.

⚠ **A bridge's own edge is dropped by the filter**, because one of its ends is not on the grid. The
failure that prevents is not a crash — `NetLayout`'s adjacency pass would build a neighbour set
containing a machine with no sighting, and the barycentre arrangement would order the layer around
something invisible. Nothing is lost: the bridge is still drawn, still carries its glyph, and still
names the server on its far side.

## 3. Multiplayer: the map is built out of noise — [PROPOSAL], not implemented

Nothing in this section exists. It is recorded so that the solo shape above is built in a way the
online shape can reuse, rather than being rediscovered later and found incompatible.

### 3.1 Machines arrive by how loud they are

A server's map is not generated up front. **Noise decides what is on it**: the loudest machines within
reach are immediately available, still subject to the 1–7 rule per node. When a sweep finds more than
seven at one node, **the surplus is assigned to the next depth** rather than widening the layer — and
reaching it costs the full loop: breach, move the vantage, sweep again.

That keeps §2's shape rule true online, and it makes the shape of a live server a *record of how loud
its population has been*, which is a thing the game already measures and currently spends only on
heat.

### 3.2 Population shapes the server — depth grows, width churns

The first version of this section put `Depth_N` at the **largest prime factor of the connected-user
count**, and the arithmetic kills it: 13 users gives depth 13, the fourteenth player takes it to 7,
and a sixteenth takes it to **2**. A server's whole world would reshape, and usually *shallow*,
as it filled up.

⚠ **The fix is not to abandon the prime — it is to give it the job it is actually good at.** The
volatility is the interesting part; it just has to land somewhere that volatility is harmless. So the
population drives two different things, on two different functions:

| | Function of | Behaviour | Why it belongs there |
|---|---|---|---|
| **Depth** | π(high-water mark of connections) | monotone, irregular steps, saturating | a world that got deeper must never get shallower |
| **Layer width** | largest prime factor of the *live* connection count | volatile, 2–7 (24 under §3.4) | the shape may churn freely; nothing is lost when it does |

#### Depth: the prime-counting function, ratcheted

```
depth(server) = clamp(NODE_DEPTH_MIN + π(highWaterMark), NODE_DEPTH_MIN, ONLINE_DEPTH_MAX)
```

π(n) is the count of primes ≤ n — **monotone non-decreasing**, so depth can only ever rise, and its
steps land on the primes, so it grows in an irregular rhythm rather than as a straight line. It is
also naturally *saturating*: π(13) = 6, π(50) = 15, π(100) = 25, so a server deepens quickly while it
is young and then slows to a crawl, which is the shape a long-lived world should have.

Against the high-water mark rather than the live count, because **the depth of a world is a fact about
its history, not about who happens to be logged in tonight.**

#### Width: the largest prime factor, and this is where it earns its keep

```
layerWidth(server) = clamp(largestPrimeFactor(liveConnections), 2, BRANCH_MAX)
```

⚠ **The jumpiness is now a feature rather than a hazard.** A server with 13 people on it is a narrow,
deep, corridor-like place; a sixteenth arrives (lpf 2) and it reads as a broad shallow sprawl; a
seventeenth (lpf 17, clamped to 7) blows it wide open. **Nothing a player has already discovered is
disturbed** — width governs where *new* machines attach, and §3.1's noise ordering decides which ones.
That is the whole reason this is the safe end to put the volatile function on.

⚠ **And it makes §3.4's overflow land beautifully.** The clamp at `BRANCH_MAX` (7) is hiding every
prime above it — 11, 13, 17, 19, 23 all read as 7. When a server meets §3.4's condition the ceiling
rises to **24**, and those primes become visible for the first time: the widths a busy server can take
are exactly the primes its population can produce. Nothing extra had to be invented for that; it falls
out of the two rules meeting.

⚠ **A depth that falls must never delete a machine a player has already discovered** — and under this
construction it cannot, because depth does not fall. The rule is stated anyway, because a future
change to the envelope would reintroduce the hazard silently: `NetRules`' whole discovery discipline
is that a found machine stays found, and a map that forgets what it showed you is indistinguishable
from a bug.

### 3.3 Bridges terminate a depth, whether or not it was reached

- **Depth met** → a bridge is placed at the end, to jump to another server.
- **Depth not met** (not enough machines to fill it) → the depth is **cut short with a bridge**, so a
  server always ends in a door rather than in a dead end.
- **One or more bridges**, one per federated server connected to this one, each carrying an
  **online/offline indicator** so a player can see which doors are shut.

⚠ The indicator is new and is the first thing in this game that publishes another server's liveness.
It is safe because it says exactly one bit about a server the player already knows exists — the same
bar `17` §3.1 sets for the peer count.

### 3.4 The 24 overflow

When the depth is met **and** every node is full at its 1–7 **and** a sweep still finds new machines,
the per-node limit rises to **24**, filled **closest to the player's rig first, by noise at the moment
of sweeping**. This is the pressure valve that lets a long-lived server keep growing after its shape
is otherwise full.

⚠ It is deliberately a *late* rule: 24 is not a normal layer width, it is what a server looks like
when it has been busy for a long time. The map's stack fold is what keeps that readable.

### 3.5 ⚠ A quiet player stays hidden — the 34% ceiling

Deeper traversal makes a *less noisy* machine more likely to be found. But **a very quiet player
remains very hard to find**: against a deliberately quiet machine, even the deep sweep tops out
around a **34% chance of success**.

⚠ **This is a rule about player rigs, not about NPCs, and conflating the two would re-tune the whole
game.** Today a quiet NPC machine is 72% at deep tier, and `07` §5.1a's vantage rule takes it to about
89%. Applying a 34% ceiling to `SignalStrength.LOW` would cut every NPC's findability by more than
half. The shape that works is a **fourth band below `LOW`** — call it what a player *achieves* by
going quiet, not what a machine *is* — with the deep sweep the only instrument that reaches it at all.

⚠ It is also the point where hiding becomes a strategy with a cost, which is what makes it worth
having: staying at 34% means staying quiet, and `08`'s noise economy is what charges for that.

---

## 4. Difficulty: flat within a server, stepped across a bridge

### 4.1 The rule

**Every machine on one server is about as hard as every other.** Difficulty steps **when the player
crosses a bridge**, and only then.

This is close to what the code already does — `Balance.netTier(serverDepth, u)` keys on the *server* —
but not equal to it: the tables carry a real spread inside each depth (home rolls tier 1 or 2 at
70/30; depth 2 rolls 2, 3 or 4). Under this proposal that spread narrows to at most one step, so a
server reads as *a place with a character* rather than as a bag of machines.

⚠ **Infrastructure keeps its +1.** `TopologyGenerator` already lifts gateways and bridges a tier, on
the argument that the two machines a player must get through to make progress should not also be the
softest things on their server. That is a statement about *position*, not about depth, and it
survives.

### 4.2 The tier ladder rises across bridges — and stays on the ethecoin side

"Slowly increasing the base tier of upgrades required to sweep, breach and port scan" is the
progression this document exists to make real. Each bridge crossed should raise the floor of what a
player needs to work effectively.

⚠ **Every depth gate must sit on the ethecoin ladder, never on a schematic.** This is **I2**, and it
is one careless table away:

- **Sensitivity may be gated.** Requiring a wide sweep to work usefully two servers out is the same
  shape as `NET_SWEEP_BRIDGE_MIN_TIER`, and `02` §1.1 step 4 puts breadth on ethecoin.
- **Reach may not.** `NetRules.hopCeiling` takes no sweep tier at any depth and must never learn to.
- ⚠ **A schematic-gated tool must never become *required* at a depth.** A schematic is found, not
  bought, so a depth that demanded one would be a wall with no route through it — content invisible
  rather than content gated, which is exactly the argument `NET_SWEEP_BRIDGE_MIN_TIER` makes for
  being 2 rather than 3.

### 4.3 ⚠ The counterpart already exists and must not be double-counted

`netCounterHackChance`, `MonJobs` density and `netDefendedChance` all already scale on server depth.
"Deeper is more dangerous" is therefore *already* true in three places; this section is about the
**tier floor**, and a fourth independent depth scalar is how a fifth server becomes unplayable
without anybody deciding it should be. Re-check `03`'s income tables against any change here — the
economy numbers are calibrated as a set.

---

## 5. Open questions

- **NT-1** — the solo node-depth band is **4–13** and the machine budget is unchanged, so on small
  servers §2.2's share clamp binds and a rolled depth is not the depth you get: a 12-machine home can
  never exceed 7 however the roll lands, so the top of the published band is unreachable at home.
  The fix would be to raise `netMachinesMin` at home, which changes the tutorial surface — the first
  screen a new player ever sees — so it is left as the clamp. ⚠ **Measured consequence**: home servers
  now run 4–7 deep and deeper servers use the full band.
- **NT-6** *(new)* — a deeper server costs more **positions** to cross, and that is a real change to
  the discovery loop rather than a side effect. Measured: with a one-hop ceiling, walking twelve
  positions at base tier found 10.1 machines under the old bushy random tree and **7.7** under this
  shape, because a spine machine has one parent and one child where a random recursive tree's had
  several. `VantageDiscoveryTest` records both numbers. Whether that is the right price for a legible
  server is open; it is the number to watch if `NET_SPINE_BUDGET_SHARE` rises.
- ~~**NT-2** — §3.2's prime-factor volatility.~~ ✅ **Resolved: the prime keeps its job, and it is
  width rather than depth.** Depth is π(high-water mark) — monotone, irregular, saturating — and the
  largest prime factor of the live count drives layer width, where churn costs nothing and reads as
  the server breathing. What is still open is the two constants: `ONLINE_DEPTH_MAX`, and whether
  width should be the lpf of the live count or of a short rolling window (a single player
  connecting and disconnecting currently flips the width, which may be too twitchy).
- **NT-3** — §3.5's fourth signal band. What *action* puts a player rig into it, what it costs to stay
  there, and whether an NPC can ever be in it. Related to `08` §1's noise economy.
- **NT-4** — §3.4's 24 overflow interacts with `docs/client/09` **NM-5**: a 24-wide layer is well past
  what the map can draw without folding. The stack fold covers it; what is unknown is whether a
  24-machine fold is still legible or is just a number.
- **NT-5** — does the difficulty step at a bridge apply to a bridge crossed *backwards*? Returning to
  home should not be dangerous, and nothing currently makes it so; a naive "difficulty follows the
  deepest server visited" would.
