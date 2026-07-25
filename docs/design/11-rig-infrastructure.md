# 11 — Rig Infrastructure

**Status:** Established (design sessions 1–5)
**Depends on:** `01-core-resources.md`, `02-unlock-gates.md`
**Depended on by:** `04-mining.md`, `05-hacking-minigame.md`, `09`, `10`

The rig is the player's ceiling. Every upgrade here is a *permanent capability increase*, which is why:

> **All rig infrastructure is schematic or story-milestone gated. None is purchasable.** This is the entire defense against the mining flywheel (Invariant I1/I2).

If any rig upgrade could be bought with EC, mining income would buy mining/attack capacity and the master scarcity would compound out of control (`00` Pillar 2). This doc is the single most important place to hold the line on gate discipline.

---

## 1. Upgrades (established)

| Upgrade | Function |
|---|---|
| **Compute Cores** | Raw cycle ceiling |
| **Thermal Budget** | Governs compute *recovery rate* (see §2) |
| **Bandwidth** | Caps simultaneous engagements |
| **Memory Buffer** | Equipped-tool slots, separate from storage capacity |
| **Isolated Partition** | Lets one bot run without contributing to the noise pool. Extremely expensive, hard cap of one or two |
| **Firmware Implant** | Deployed miners survive a host wipe. Recovered from deep inside Eye infrastructure — acquiring it is itself a late-game objective |
| **Worm Module** | Deployed miners attempt to propagate to adjacent nodes. Compounding returns and compounding exposure; noise scales with spread and the player does not control where it goes |
| **Cuckoo Patch** | Hijacks a discovered foreign miner rather than killing it |
| **Payout Splitter** | Routes a fraction of mining yield to the Sickle common fund, converting EC to reputation at a poor rate. Primary faction-side currency sink |

## 2. The four core stats

These four define the shape of a rig and gate everything else:

**Compute Cores — the raw ceiling.** How many total cycles exist. The headline progression stat; every other system is measured against it (`01` §1). Growth here is designer-paced and story-milestone heavy.

**Thermal Budget — the recovery-rate governor.** Spent cycles return to the pool over time, and recovery is **slower the closer the rig sits to capacity** (`01` §1.3). High thermal → a loaded rig still recovers fast; low thermal → an overextended player is effectively down their spent cycles for a long stretch. **This is the single stat explaining why a loaded rig feels sluggish, and it is what gives scanning (`04` §3.2) a real rather than nominal opportunity cost.** Underrated but load-bearing: without Thermal Budget, compute costs would be flat and overextension would be free.

**Bandwidth — the simultaneity cap.** How many engagements can run at once. Directly bounds the botnet/multitasking ceiling (`10`) independent of raw compute — you can have cycles free and still be bandwidth-blocked from another engagement.

**Memory Buffer — equipped-tool slots.** Separate axis from storage (`01` §6): storage is how much you *own*, memory buffer is how much you can have *readied at once*. A player can own a deep toolkit but only field a loadout-sized slice.

## 3. The advanced/mining modules

**Isolated Partition** — the single exception to noise pooling (`10` §3): one bot runs noise-free. Extremely expensive, hard cap of one or two. Deliberately scarce so the pooling rule stays the norm.

**Firmware Implant** — deployed miners survive a host wipe. **Recovered from deep inside Eye infrastructure — acquiring it is itself a late-game objective**, not a shop transaction. Directly changes the NPC-sweep math (`04` §4): implanted miners survive the sweep that destroys everything else, making a hot player's network resilient in a way that's *earned* through a story-scale operation.

**Worm Module** — deployed miners self-propagate to adjacent nodes. **Compounding returns and compounding exposure**: noise scales with spread and the player does not control where it goes. The high-risk/high-reward endgame of deployed mining — a worm can build a huge network or paint an enormous target, and you can't fully steer it. The loss-of-control is the design feature; do not add player steering.

**Cuckoo Patch** — enables the **hijack** response to a discovered foreign miner (`04` §5): take future yield instead of killing. A rig module rather than a consumable because "I can hijack" is a permanent capability. See `04` §5.2 for crack-vs-hijack.

**Payout Splitter** — routes a fraction of mining yield to the Sickle common fund, converting **EC → reputation at a poor rate**. The **primary faction-side currency sink** (`03` §4). Poor rate is deliberate: it's a *commitment* signal (spend income to prove allegiance), not an efficient conversion. Voluntary and ongoing.

## 4. Gate-discipline checklist (read before adding any rig upgrade)

1. Is it a permanent capability or ceiling increase? → It **must** be schematic/story-gated. No EC path. No exceptions. (This is Invariant I1/I2 at its most tempting to violate.)
2. Does it interact with mining income? → Double-check it can't create a compute-buys-compute loop (Firmware Implant and Worm Module both touch mining yield but neither adds *cycles* — confirm any new module has the same property).
3. Where is its schematic found? → Name the region/story beat. Rig upgrades are progression anchors; they should map to the world (`14`).
4. What's its permanent compute reservation, if any? → Add it to the compute-budget math (`09` §3, `10` §3).

## 5. Progression role

The rig upgrade tree *is* the designer-paced progression spine (Pillar 3). Money buys breadth across it (tools, consumables); the tree itself is walked by exploration and story. A session picking up "what does the player unlock next and where" should start here and in `14-world-and-narrative.md`.
