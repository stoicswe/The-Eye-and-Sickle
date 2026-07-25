# 09 — Player State Portability & Character Slots

**Status:** ⚠️ **[PROPOSAL]** — the source design did not cover cross-machine or cross-server save continuity. This doc specifies it. The *account/character model* (§1) and the *portable/non-portable split* (§3) are the load-bearing parts; the specific mechanisms (§4–§6) are a first implementation.
**Depends on:** `02-identity-and-auth.md` (DID), `03-server-and-federation.md`, `04-item-provenance.md`, `08-discovery-and-sync.md`
**Depended on by:** the server `identity`, `discovery`, and `items` slices; implementation

---

## 0. The question this answers

*"I play on my laptop; can I continue on my desktop, signed in with the same Bluesky account?"* — and its harder sibling, *"can my character move to a different home server?"*

The answer splits cleanly, and most of it is already true:

- **Same home server, different machine — already solved, nothing to sync.** The client holds no authoritative state (`01` §1); a character is rows in a home server's Postgres keyed by the player's DID (Invariant I14). Sign in from any machine with the same DID and the server serves the same character. There is no save file on the machine.
- **A different home server — the real feature this doc adds.** Here the invariants bite, and the design has to respect the portable/non-portable split (§3).

## 1. Accounts and characters

Two concepts, kept distinct:

- An **account** is a **DID** — one Bluesky/AT Proto identity. Online play requires it (`02` §4).
- A **character** is a **save slot**: an independent game identity with its own ethecoin, rig, faction, heat, and items. A character is exactly what a `players` row already models.

**An account may hold up to `EYEANDSICKLE_MAX_CHARACTERS` characters — default 3 — in online play.** The cap is a product knob (configurable), not a balance value. Each character is a separate operator "within the network": separate progression, separate standing, separate heat.

> **Single-player / offline is exempt.** A local, allowlisted, DID-less character (`02` §4 option 1, `players.did` nullable) is outside this system: no cap, no directory, no federation. The 3-slot rule is a property of *online, DID-bound* play only.

### 1.1 One home per character

Every character has exactly **one home server** at a time — "your home server = your cell" (`03` §6). Its authoritative state lives there and nowhere else (I14). "Continuity" means being able to reach that home from any machine (§4), survive the home going away (§5), or move the home deliberately (§6).

## 2. Enforcing the cap without a central authority

There is no global database of accounts — Invariant I15 forbids a single arbiter, and `03` §2 keeps servers from sharing private state. So the cap is enforced the same way everything else in this federation is: **honest servers cooperate, and dishonest ones are not recognized.**

- A signed, gossiped **character directory** (§4) maps a DID to its characters and each character's home.
- Before creating a new DID-bound character, an honest server consults the directory and **refuses if the account already has 3 recognized characters.**
- A defecting server *can* mint a 4th character on its own turf — but that character is **not recognized federation-wide** (`03` §4). The cap is therefore "at most 3 *recognized* characters," which is the strongest guarantee a no-central-authority federation can make, and it is exactly the guarantee the rest of the trust model already relies on.

> **Open (Q-cap-race):** two servers, each seeing only 2 registered characters, could simultaneously create a 3rd-and-4th. The directory converges to at most 3 recognized (the excess is flagged like any other over-assertion), but which one is dropped, and how the loser is told, needs specifying. Soft, eventually-consistent enforcement — acceptable given I15, but the race is real.

## 3. The portable / non-portable split — the crux

A character's state is **not one thing**, and the architecture already sorts it exactly along the line this feature needs:

| State | Portable to an untrusted server? | Why |
|---|---|---|
| **DID (identity)** | ✅ yes | AT Proto DID, portable by construction (`02`) |
| **Provenanced items** | ✅ yes, *verifiably* | Per-item signed chains (`04`); any server can verify them with `ProvenanceChainVerifier` |
| **Ethecoin balance, rig/compute config, personal heat, faction reputation, deployed miners, breach resolutions** | ❌ no | Freely-assertable, server-local ledger state with no cryptographic anchor |

This is the whole design. **What is cryptographically yours travels; what a server merely asserts does not.** Everything below follows from it.

## 4. Option E — find my character's home from my DID

A lightweight **character directory**, built on the discovery layer (`08`): DID → `[ {characterId, slot, homeServerDid, homeEndpoint} ]`, each entry a **signed record with a monotonic sequence number**, exactly like a server descriptor (`08` §2).

- The **home server signs** the record ("I host character C, slot N, for DID D, at sequence K"). Any federated server verifies the home server's signature and can then answer "where are DID D's characters?" — DNS for your character.
- Sequence is **monotonic**: a home change (§6) advances it; a lower sequence is refused. This is what stops a stale record from resurrecting a moved character.
- A client on a new machine resolves its own characters from its DID, then connects to the right home. No state moves; only a pointer is read.

> **Open (Q-home-auth):** the home server signs the binding, which stops a *stranger* forging a location but not a *rogue home server* claiming to host characters it does not. A refinement is to have the account's DID key co-sign the binding at creation. Deferred; the home-server signature is the v1.

## 5. Option B — home-server backup, restore, and cooperative migration

The gap Option E leaves: **a self-hosted home server can simply disappear.** That is the real continuity risk, and it is an operational problem with an operational answer that touches no invariant, because the state never leaves trusted hands.

- **Backup/restore:** the operator dumps and restores the home server's PostgreSQL (documented in `deploy/`). State stays server-owned.
- **Cooperative migration (same-trust):** operator A exports a character's **full state** as a signed bundle; operator B imports it; the directory's home binding advances (§4) to B. This carries the *whole* character — economy included — because **both operators cooperate and therefore trust each other**. It is the trusted counterpart to §6.

Because B is trusted end-to-end, it is the only path by which the non-portable economy (§3) legitimately moves.

## 6. Option C — verifiable migration to an untrusted server

Moving to a home server you do **not** control, and which does **not** trust your old one. You carry only what is cryptographically yours, and the rest resets:

- **You carry:** your DID, and your **provenanced items with their signed chains**.
- **The destination verifies** each chain with `ProvenanceChainVerifier` (`04` §7) before recognizing an item. An unverifiable item is simply not recognized — the same rule that makes a cheating server's fabricated items worthless.
- **The economy resets.** The new character starts at base ethecoin, a base rig, zero heat, and zero faction reputation, because those are freely-assertable and cannot be trusted from an untrusted source (§3). This is honest to the trust model, and thematically apt: *you fled your cell — you kept your gear, and you are rebuilding your standing.*

### 6.1 The security rules every path must obey

- **No double-play.** A character has exactly one authoritative home. Migration **retires** the character at the old home before it becomes live at the new one; a retired character cannot be played or migrated again.
- **No rollback / no fork.** The home binding's sequence (§4) only advances. Presenting an older bundle, or re-homing to a server with a stale sequence, is refused — the same monotonicity the discovery descriptors enforce.
- **Provenance is re-verified at the destination, never trusted from the bundle.** The bundle is untrusted transport; the chains inside it carry their own proof.

## 7. Invariant reconciliation

- **I14 (state never on player-controlled infra).** Preserved. Character state still lives in a home server's Postgres. The directory record is a non-adversarial location pointer, safe to gossip (`03` §2). The migration bundle's *trusted* part (items) is provenance-verified; its *untrusted* part (economy) is discarded, not imported. Nothing is ever read as authoritative from the player's PDS.
- **I15 (no single arbiter).** Preserved. The cap and the home bindings are signed directory records enforced by honest servers via non-recognition, not by any central authority.
- **The rejected designs.** A "cloud save" in the player's PDS (violates I14 — player-controlled adversarial state) and wholesale *trusted* state transfer from an *untrusted* server (violates §3 — importing freely-assertable economy) are both out. C is the invariant-safe substitute for the second.

## 8. Data-model impact (informative; the migration is authoritative)

- `players.did` loses its `UNIQUE` constraint; uniqueness moves to `(did, slot)` for DID-bound characters. A `slot` (1..`MAX_CHARACTERS`) and a `status` (`active` / `migrated` / `retired`) are added.
- A `character_directory` table holds the signed home records of §4 (DID, characterId, slot, home server DID + endpoint, transport-key material for the home, sequence, signature) — the player-character analogue of `federation_peers`.
- Migration writes are append-only history where they touch provenance (`04`); the economy reset is a fresh character row, never an edit of the old one.

## 9. Open questions

- **Q-cap-race** (§2): converging a simultaneous over-creation to ≤3 recognized characters, and telling the loser.
- **Q-home-auth** (§4): whether the account DID co-signs its home bindings, or the home-server signature alone suffices.
- **Q-slot-scope:** is the cap strictly network-wide, or per-federation-directory? Two disjoint federations that never gossip cannot see each other's counts; a DID could hold 3 in each. Probably acceptable (they are different worlds), but state it.
- **Q-economy-seed** (§6): what exactly a reset character starts with (0 EC and a base rig, or a small onboarding grant) — a balance value for `03`, not decided here.
- **Q-retire-window:** how long a migrated character's old home retains its retired shell (for dispute/audit) before it may be reaped.

## 10. Cross-references

- The identity this is anchored on: `02-identity-and-auth.md` (esp. §4, local-vs-DID identity)
- Why untrusted servers exist, and non-recognition: `03-server-and-federation.md` §1, §4
- The provenance verifier C relies on: `04-item-provenance.md` §7
- The signed-descriptor + monotonic-sequence pattern E reuses: `08-discovery-and-sync.md`
- Player-facing multiplayer opt-in (per-server): `../design/13-multiplayer-and-federation-play.md` §5
- Open-question log: `../design/15-open-questions.md`
