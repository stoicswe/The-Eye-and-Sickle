# 06 — Data Model

**Status:** ⚠️ **[PROPOSAL]** — the source material fixes the *stack* (Postgres) and the *provenance schema* (`04`) but never sketches the game-state schema. This is a first-pass relational model to give Claude Code a starting point. Nothing depends on these specifics; the *constraints* it must satisfy (§1) are the real content.
**Depends on:** `01-tech-stack.md` (Postgres), `02-identity-and-auth.md` (DIDs), `04-item-provenance.md`
**Depended on by:** implementation

---

## 1. Constraints the schema must satisfy (these are firm)

1. **All game state lives here, in the server's Postgres — never in a player's PDS** (Invariant I14). Identity comes from AT Proto; everything else is server-owned.
2. **The public ledger is queryable** (`../design/01` §2.2) — EC transactions are first-class rows, not derived.
3. **Per-item provenance chains are storable and queryable by `itemId`, with `chainDepth` range access** (`04` §6).
4. **The compute ledger is reconstructable** — every compute allocation (`../design/01` §1) must be attributable so manual-audit gameplay (`../design/04` §3.1) works against real data.
5. **Faction reputation and validator reputation are distinct** (glossary) — different tables, no shared column.

## 2. Proposed core tables

> All below is [PROPOSAL]. Types are illustrative (Postgres). Names follow the glossary conventions.

### players
| column | type | notes |
|---|---|---|
| `player_id` | uuid PK | server-local ID |
| `did` | text unique | AT Proto DID (`02`); nullable for local-only solo players (open, `02` §4) |
| `handle` | text | current display handle; not stable — the DID is |
| `personal_heat` | numeric | `../design/01` §4.1 |
| `faction` | enum(eye, sickle, none) | `../design/01` §5 |
| `faction_reputation` | numeric | ⚠️ not validator reputation |
| `created_at` | timestamptz | |

### rigs
| column | type | notes |
|---|---|---|
| `rig_id` | uuid PK | |
| `player_id` | uuid FK | |
| `compute_cores` | int | total cycle ceiling (`../design/11`) |
| `thermal_budget_tier` | int | recovery-rate governor (`../design/01` §1.3) |
| `bandwidth` | int | simultaneity cap |
| `memory_buffer` | int | equipped-tool slots |
| `installed_modules` | jsonb | Isolated Partition, Firmware Implant, Worm, Cuckoo Patch, Payout Splitter (`../design/11`) |

### compute_allocations
| column | type | notes |
|---|---|---|
| `allocation_id` | uuid PK | |
| `rig_id` | uuid FK | |
| `consumer_type` | enum | tool / bot_frame / self_mining / control_channel / defense / relay_hop |
| `consumer_ref` | uuid | points at the specific miner/bot/tool |
| `cycles` | int | reserved amount |
| `state` | enum(active, recovering) | recovering rows carry a recovery curve (`../design/01` §1.3) |
| `recover_complete_at` | timestamptz null | when recovering cycles return |

Constraint 4 lives here: sum of active allocations must reconcile against `rigs.compute_cores`, and a discrepancy is exactly what a manual auditor (or a hidden hostile miner) creates.

### items
| column | type | notes |
|---|---|---|
| `item_id` | uuid PK | matches provenance `itemId` (`04`) |
| `item_type` | text | e.g. `hacking_tool_tier2` |
| `item_attrs` | jsonb | authoritative stats; mirrors provenance `itemAttrs` |
| `holder_did` | text | current owner (`04`) |
| `storage_tier` | enum(vault, standard, high_hackable) | `../design/01` §6; null if socketed into a bot |
| `socketed_in` | uuid null | bot instance if assigned (`../design/10`) — makes it mid-risk |

### provenance_records
| column | type | notes |
|---|---|---|
| `record_id` | uuid PK | |
| `item_id` | uuid FK, indexed | per-item chain (`04` §6) |
| `chain_depth` | int, indexed | genesis = 0; for range queries (`04` §6.1) |
| `prev_record_hash` | text null | null at genesis |
| `payload` | jsonb | the canonicalized-then-signed payload (`04` §2) |
| `envelope` | jsonb | detached-JWS envelope, single- or multi-sig (`04` §3) |
| unique | (`item_id`, `chain_depth`) | one record per position |

### ledger_transactions  (the public ledger)
| column | type | notes |
|---|---|---|
| `tx_id` | uuid PK | |
| `from_did` | text null | null for mints/mining rewards |
| `to_did` | text | |
| `amount_ec` | numeric | |
| `tx_type` | enum | mining_reward / trade / crack_seizure / raid_loot / payout_splitter / purchase |
| `traceable` | bool | false if via Dead Drop (`../design/08`) — still recorded, but obscured to investigators |
| `created_at` | timestamptz | |

### deployed_miners
| column | type | notes |
|---|---|---|
| `miner_id` | uuid PK | |
| `deployer_did` | text | pays the 3-cycle control channel |
| `host_type` | enum(npc, player) | |
| `host_ref` | uuid | the machine hosting it (steals *its* compute, `../design/04` §2) |
| `tier` | enum(t1, t2, t3) | |
| `buffer_ec` | numeric | on-host accrual while deployer offline (`../design/04` §2.3) |
| `buffer_cap_hours` | int | default 4 (OQ-4) |
| `rootkit_wrapped` | bool | `../design/09` |
| `state` | enum(live, hijacked, sabotaged, dead) | |

### breach_resolutions  (feeds proof-of-skill + salvage guards)
| column | type | notes |
|---|---|---|
| `resolution_id` | uuid PK | |
| `player_did` | text | |
| `puzzle_class` | text | `../design/05` §3.1 [PROPOSAL] |
| `difficulty_tier` | int | the proof-of-skill / salvage threshold (`../design/02`, `10`) |
| `live_or_dormant` | enum | proof-of-skill requires `live` |
| `outcome` | enum(breached, failed, aborted) | |
| `created_at` | timestamptz | |

### Federation tables (only on federating servers)

- **validators** — `validator_did`, `validator_reputation` (⚠️ distinct from faction), `uptime`, `is_new` (cold-start floor, `05` §2.5).
- **duels** — `duel_id`, `participants`, `sampled_validators` (jsonb), `outcome`, `signatures` (jsonb, `04` §3.1).
- **flagged_servers** — `server_did`, `reason` (e.g. equivocation proof), `flagged_at` (non-recognition, `03` §4).

## 3. Explicitly deferred

- **ORM vs. jOOQ vs. plain JDBC** — not decided (`01` §5). The `jsonb` columns (attrs, envelopes, modules) suit a hybrid: relational for the economy, document for the flexible/nested crypto payloads.
- **Indexing/perf** beyond the two provenance indexes and the ledger's DID indexes — measure first.
- **Sharding/scale** — home servers are small (allowlist-bounded); premature.
- **Migration tooling** — pick at scaffold time (Flyway/Liquibase are the JVM-standard options).

## 4. Why relational fits

The economy is intensely cross-referential — items reference holders, allocations reference consumers, ledger entries reference DIDs, provenance chains reference prior records. That's a relational model's home turf, with `jsonb` escape hatches for the genuinely document-shaped data (signed payloads, item attrs, installed modules). This matches the Postgres choice in `01` and the "keep game state in your Postgres instances" directive from Tech Chat 1.
