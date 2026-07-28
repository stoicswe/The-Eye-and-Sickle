# 03 — Economy Baseline

**Status:** Established (design sessions 1–5); calibration explicitly pending playtest (OQ-1)
**Depends on:** `01-core-resources.md`, `02-unlock-gates.md`
**Depended on by:** `04-mining.md`, all tool pricing (`06`–`11`)

All figures assume a **~1 hour session** and a **100-cycle starting rig**. These are the anchors every price and yield in the game is tuned against. Change an anchor here and every table downstream must be re-checked — that's why this doc exists separately from the mining doc.

---

## 1. Income comparison (the headline table)

| Income source | Gross/hr | Risk | Effective/hr |
|---|---|---|---|
| Active hacking | 70 EC | Loot loss, heat gain | ~70 |
| Self-mining (full rig) | 39.4–45.1 EC | None | 39.4–45.1 |
| Deployed network (5× T2) | 95 EC | Detection, hijack, sabotage | ~55–65 |

⚠ **Self-mining is a band, not a number, as of 2026-07-27.** Blocks now pay their **transaction fees** to whoever mines them, on top of the subsidy — worth **+10.55%** — and only the modes paid out of real blocks receive them. Measured at a full 100-cycle rig:

| Mode | Scheme | Fee | EC/hr | vs the 40.00 anchor | Paid block fees |
|---|---|---|---|---|---|
| **Solo** | block | 0.00% | **45.12** | +12.80% | yes |
| THE COMMONS *(default)* | PPS | 2.00% | 40.00 | +0.00% | no |
| MERIDIAN CLEARING | PPS | 3.50% | 39.39 | −1.52% | no |
| PALE LANTERN | PPS | 2.50% | 39.80 | −0.50% | no |
| GLASS TEETH | PPLNS | 1.00% | 44.67 | +11.67% | yes |
| SMALL HOURS | PPLNS | 0.50% | 44.90 | +12.25% | yes |

**The 40.00 anchor now describes a pay-per-share pool**, which is the default and therefore still the floor this document prices. `Balance.chainNetworkHashrate()` was deliberately **not** re-solved to absorb the fees — that was the alternative and it was rejected, because the point of paying fees out is that mining income actually reflects them.

⚠ **What that re-check found: the ordering of income sources is unchanged**, which is the structural property §5 depends on. Active hacking (70) still leads, deployed networks (55–65) still sit second, and self-mining is still last at 39.4–45.1 even in its best mode. Faucet rule 1 (nothing above 70 effective) holds with 25 EC of headroom. What narrowed is the *gap* — self-mining at its best is now 64% of active income rather than 57%.

### 1.1 Why these ratios are what they are

- **Self-mining at 57–64% of active income.** ⚠ The lower figure is pooled pay-per-share and the upper is solo; see the band above. It is a floor, not a strategy — worth doing when heat is too high to operate, never worth doing *instead of* playing. That property survives the fee change with room to spare: 45.12 against 70 is still a decision nobody makes on the numbers alone. (Its safety is structural — Invariant I4.)
- **Deployed mining reads as the best option on the sheet (95 gross) and lands below active income once losses are priced in (~55–65).** A variance play that feels smart and isn't dominant. Players who audit diligently (Provenance Tracer) and site miners well can push effective yield **above** active income — that's a deliberate skill reward, and it's self-limiting because auditing consumes the session time they'd otherwise spend earning.
- **Active hacking is the ceiling on EC/hr** and it's the activity that *is the game* (Pillar 1). Nothing passive may beat it in expectation. Any new income source must be slotted under 70 effective or given costs that pull it under.

### 1.2 Variance warning (established, restated)

⚠ **Self-mining now has variance too, and it is opt-in.** `04-mining.md` §1.3 makes solo mining a Poisson process paying whole blocks at ~4-hour intervals. Pooled pay-per-share — the default — has none of it and is what this table's 40 EC/hr means. A player who has chosen solo is not on the floor this document prices, and playtest instrumentation (§6) should bucket the two apart or the mining figures will read as noise.

⚠ **Since 2026-07-27 the split to bucket on is the *scheme*, not the mode.** Block fees are paid to whoever mined the block, so PPLNS pools pass them on and pay like solo (+12%) while pay-per-share pools cannot and sit on the anchor. A run that mixes GLASS TEETH with THE COMMONS mixes two income rates 11.7% apart, and the mining figures will read as noise for that reason rather than for variance.

The deployed-network effective figure hides **correlated loss**: sweeps roll against the deployer's single global heat value, so networks die in wipes, not smooth decay (`04-mining.md` §4). Budget play experience around occasional catastrophic loss, not a steady bleed. OQ-3 tracks whether wipes feel dramatic or unfair.

---

## 2. Cost anchors

| Class | Range |
|---|---|
| Consumables | 5–15 EC |
| Mid-tier tools | 40–60 EC |
| Top purchasable tools | ~200 EC |
| Black-market zero-day | 400+ EC |

Rules of thumb derived from the anchors (use when pricing new items):

- A consumable costs minutes of income; burning one on a bad read should sting, not ruin.
- A mid-tier tool ≈ one cautious session's net income (see §3) — replacing a lost one is an evening, not a week.
- A top-tier purchasable ≈ 3 hours of gross active income — and per Invariant I2 it is still *horizontal*: better at its niche, never a ceiling raise.
- A zero-day ≈ 6+ hours gross, plus the heat cost of being hot enough to reach the broker at all. It should never feel routine.

---

## 3. The intended net-income shape

- A **cautious** player nets **20–30 EC/hr** after upkeep and replacement.
- An **aggressive** player runs **near zero and occasionally negative** — they are converting surplus into reputation, schematics, and story access rather than accumulating EC.

**That is the intended shape.** EC accumulation is not the victory track; if a playtest shows aggressive players stacking large balances, sinks are undertuned (OQ-1). Conversely if cautious players can't afford consumable replacement, faucets are undertuned.

---

## 4. Sinks

Sinks must absorb income at roughly the rate players generate it. The established sink list:

| Sink | Type | Reference |
|---|---|---|
| Consumable expenditure | Recurring, per-op | `06`/`07`/`08` tool tables |
| Relay-hop upkeep (~8 EC/hop/session) | Recurring, per-session | `08-stealth-and-noise.md` |
| Tool replacement after loss | Event-driven (raids, bot wipes, failed hacks) | `10-botnets.md`, `09-defense-and-hardening.md` |
| Payout Splitter conversion (EC → Sickle rep at a poor rate) | Voluntary, ongoing | `11-rig-infrastructure.md` |
| Black-market premiums | Event-driven, luxury | `06-intrusion-tools.md` |
| Vault expansion costs | Milestone-driven | `01-core-resources.md` §6 |

Design note: the two *reliable* sinks are relay upkeep and consumables; everything else is event-driven or voluntary. If steady-state players outrun the sinks, tune the reliable pair first — event sinks punish the wrong players (the unlucky) when overtuned.

> **[PROPOSAL]** Vault expansion EC component: the schematic gates the capability (per I2/I12), but installation can carry an EC materials cost (e.g. 150/250/400 EC for successive expansions) — a milestone-driven sink that scales with wealth without selling capacity itself. Flagged because the source design lists "vault expansion costs" as a sink while keeping capacity schematic-gated; this is the reconciliation: **schematic unlocks, EC installs.**

---

## 5. Faucet discipline (rules for adding any income)

1. No new faucet above 70 EC/hr effective for a solo player.
2. Passive faucets must be capped (buffers) or online-gated; the only offline faucet is deployed miners (Invariant I5).
3. Player-to-player takings (crack seizures, raid loot) are **transfers, not faucets** — they move EC/items, they don't mint. The crack payout explicitly works this way (`04-mining.md` §5).
4. Faucet + sink changes get logged in this file's changelog (§7) with before/after rates.

---

## 6. Playtest instrumentation (what to measure, once there's a build)

Per-player-hour, segmented by heat band and playstyle cluster:

- Net EC/hr distribution (target: cautious cluster 20–30; aggressive cluster ≈ 0)
- Sink share: % of gross income absorbed by each sink class
- Self-mining share of total income (rising share = heat too punishing or hacking underpaying)
- Deployed-network effective yield including wipe losses (target band 55–65 for a 5×T2 reference network)
- EC balance at session end over time (should be roughly flat for aggressive players)

## 7. Changelog

- 2026-07-23 — Initial extraction from consolidated design doc (sessions 1–5). Added §2 rules of thumb, §5 faucet discipline, §6 instrumentation list, and the §4 vault-installation reconciliation proposal.
