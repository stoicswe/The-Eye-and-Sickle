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

> **Firmware upgrades — the class (decided 2026-07-30).** The Firmware Implant is the first of a class, and the class has three rules. All three are the real-world behaviour of firmware rather than game-isms, which is why they are worth having.
>
> 1. **Two things are needed: the schematic and a software component (the *image*).** The schematic is the authorisation and the image is the payload; neither alone does anything. The image is acquired *either* by buying it from the market *or* by breaching a machine that has one — the raid route is not optional flavour, and an image that could only be bought would make the two-part requirement pointless.
> 2. **Firmware costs more** than any ordinary upgrade. It is a permanent capability's payload rather than a consumable, everything else the vendor sells is losable and replaceable (`02` §2.1) and this is not, and the price has to leave stealing one clearly worth the breach.
> 3. **The affected tool must be stopped to flash it.** You cannot rewrite the firmware of a device while the device is using it; real flashing tools refuse for the same reason, and a half-written firmware is how a device is bricked. For the Implant that means self-mining at zero *and* no deployed miners — a deployed miner spends the host's compute (**I6**) but it is still this rig's mining software driving it. It is a **refusal, never an offer to stop mining automatically**: doing that silently costs the player income they did not agree to lose.
>
> ⚠ **This is not a second unlock gate, and Invariant I3 is intact.** `02` §1.1 already sanctions the split — *"Rainbow Table is EC + schematic (buy the table, but the capability to use it is found)"* — under the standing condition that **the ceiling component is always on the non-EC side**. It is: the schematic is the ceiling, no amount of ethecoin produces one (`02` §2.2), and the purchasable half is an inert file. §4 rule 1's "no EC path, no exceptions" is therefore untouched.
>
> ⚠ **§4 rule 2 checked:** it touches mining income and adds **no cycles**. Surviving a host wipe changes how long a deployed miner lives, never how much compute exists — so no compute-buys-compute loop (**I1**) and no ceiling bought with money (**I2**).
>
> **Flashing (decided 2026-07-30).** A firmware image is a `.frm`, and installing one is not an install — it is a **flash**: a 90-second task with the affected tool frozen for the duration, behind a full-panel overlay carrying a warning mark, what is being written, a progress bar and a countdown.
>
> ⚠ **`.pkg → .frm` for firmware, `.pkg → .upg` for software — and the first arrow is deliberately unchanged.** The `.pkg` rename *is* the confirmation lock (`04` §1.3e): a bought package stays a vendor package until its payment is mined. Naming firmware `.frm` at both ends would leave it with no rename to make, and a purchased image would become flashable before its money moved.
>
> ⚠ **The freeze is the point, and it is the second half of "stop the tool first".** Requiring mining stopped at the door and then letting the player restart it two seconds later would make the rule ceremony. Raising the allocation is refused for the whole flash; **setting it to zero is always allowed**, because a rule that traps a player's cycles inside a tool they cannot use is a bug wearing a rule's clothes.
>
> ⚠ **It is a task, so it survives a quit** — `save.tasks`, settled by `tick()` and by `resume()`. A device writing its own memory does not stop when nobody is looking, and losing the image to a closed window would be the worst possible outcome of a 90-second wait. **The image is consumed on completion, never at the start**, so an interrupted flash costs nothing.
>
> ⚠ **Ninety seconds, and it is not derived from the image's size.** A download is bounded by the far end's uplink and should track bytes; a flash is bounded by the device writing itself, and a bigger image does not make a slower flash on any hardware a player has met. The figure is bounded at both ends: much shorter and freezing the tool costs nothing; much longer and a player is simply denied their rig.
>
> ⚠ **One flash at a time.** Two concurrent writes to the same device is how it is bricked.
>
> ⚠ **The schematic is still recovered, and nothing in the shipped code grants it.** `GameSave.schematics` holds it and the progression slice fills that; the catalogue entry named `firmware-implant` is the **image**, not the capability. A future edit adding a firmware entry with no schematic named is rejected at construction rather than shipping as an EC path to a ceiling.

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
