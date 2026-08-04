# 15 — Federation rollout plan

> **Status: ⚠️ [PROPOSAL] — written 2026-08-04. A sequencing plan, not a new design.**
>
> Every mechanism here is specified elsewhere: `03` (federation model), `05` (validator quorum),
> `07` (transport security), `08` (discovery), `12` (LAN), `13` (game transport). This document
> answers a different question — **in what order, and what has to be true before each step** — and
> records the two recommendations that change what gets built first.

---

## 0. The two recommendations, up front

**R1 — Ship LAN before federation, as a real milestone.** LAN is federation with the trust problem
deleted: same `GameSession` port, same server, same engine, same schema, same REST surface, and
**no DID resolution, no quorum, no server↔server transport, no cryptography at all**. It is the
only way to prove the port is honest — that single player and multiplayer really are one game —
before anything cryptographic is load-bearing. Almost every bug in the client/server split will be
found here, at a fraction of the cost.

**R2 — Do not ship the hand-rolled transport. Use mutual TLS for v1.** `07` §6 **T-1** already says
the Noise-IK-shaped construction is "reviewed patterns, unreviewed code" and must not guard a live
federation until a cryptographer has read it. That review is expensive and slow to schedule, and it
is on the critical path of everything else. **mTLS with certificates pinned to server DIDs** gives
authenticated, confidential, replay-resistant server↔server links using an implementation that has
had the review, and it costs one configuration class rather than a protocol. Keep `07` as the
long-term goal; do not let it block the rollout.

> ⚠ This is the single highest-risk item in the whole system. A federation that ships on unreviewed
> cryptography and is later found broken cannot be quietly fixed: every item that crossed it has a
> provenance chain nobody can now trust.

---

## 1. What is already built

Not a plan item — the starting position, so the plan is not re-doing it.

| Piece | Where | State |
|---|---|---|
| Provenance verifier + signed per-item history | `protocol/provenance` | Built, unit-tested |
| Validator quorum — A-Res sampling, AIMD reputation | `server/federation` | Built |
| Peer discovery | `server/discovery` | Built |
| `Content-Digest` ingress filter | `server/web` | Built |
| DID document / handle resolution, SSRF-hardened HTTP | `protocol/identity` | Built |
| AT Proto auth + allowlist, compute & ethecoin ledgers | `server/identity`, `server/compute`, `server/economy` | Built |
| Schema, migrations, `EngineSessions` | `server/migration`, `engine` | Built, `-Pit` green |

**The engine is already the shared one.** A home server drives the same `GameEngine` the client
drives in process, so multiplayer does not need a second implementation of any rule — that work is
done and is why this plan is about transport and trust rather than about game logic.

---

## 2. The dependency chain

Nothing here is optional ordering; each step is blocked by the one above it.

```
API-1  the server's auth model            ── nothing can call a server until this exists
  └── CL-8  client → home server transport
        └── LAN mode  (R1: ship here)     ── multiplayer, no cryptography
              ├── W-1  DID → key resolution over the network
              │     └── T-1/R2  server ↔ server transport (mTLS)
              │           └── federated identity + item provenance across servers
              │                 ├── W-7  server-side market stock (atomic take)
              │                 ├── W-8  buying from a foreign server
              │                 └── W-9  the federated Shadow Market
              └── G-1  flag propagation   ── needs peers to gossip between
```

### 2.1 API-1 — the auth model (blocks everything)

⚠ **The REST API currently answers 401 to every one of its 31 endpoints.**
`spring-boot-starter-security` is on the classpath with no `SecurityFilterChain` anywhere, so Boot's
`anyRequest().authenticated()` applies to all of it. `ApiDocsSecurityConfiguration` opens the doc
paths only, deliberately.

This is not a bug to patch — it is the auth model, unwritten. It has to answer:

- **Who is the caller?** An AT Proto session (a player) or a peer server (a DID-authenticated
  service). Two different principals with two different filter chains.
- **What does a player's token look like on the wire?** `10` specifies DPoP-bound service auth;
  the player-facing half is unspecified.
- **What is public?** Discovery and the federation directory need an unauthenticated surface, or
  peers cannot find each other before they trust each other.

⚠ **No server test has ever made an HTTP request to its own controllers.** Whatever this decides,
the first artefact is a slice test that does — otherwise the next silent 401 lands the same way.

### 2.2 CL-8 — client to home server

`RemoteGameSession` is written and refuses every intent. This is the smallest possible security
problem — a player talking to **their own** server over ordinary HTTPS — and it is where the port
gets proved.

⚠ **The port is the contract, and it must not grow a "remote-only" branch.** Every view already
binds to `GameSession` and never learns which side it is on; the moment a view asks, single player
and multiplayer become different games. Where a server genuinely cannot answer (the Shadow Market
today), the answer is an empty result and a documented seam, never a local simulation.

### 2.3 LAN mode — the milestone (R1)

`12` is already `[PROPOSAL], partially built` and `server/lan` exists. What LAN needs beyond CL-8:

- Server discovery on the local network, or a typed-in address — **not** the federation directory.
- `Quarantine.refuseIfLan` already guards the export path; LAN characters must stay non-federable.
- Shared world state that is genuinely shared: **W-7's atomic stock take is first**, because it is
  the smallest piece of real contention in the game and it is the one that proves the model.

**Deliverable:** two players on one LAN, one server, a shared shelf, a shared chain, visible to each
other on the map. No DIDs, no quorum, no signatures.

### 2.4 W-1 — DID → key resolution

`protocol/identity` already has `DidResolver`, `HandleResolver` and the SSRF-hardened client. What
remains is wiring it into the three server stubs (`DidPublicKeyResolver`, `PeerKeyResolver`,
`ValidatorKeyDirectory`), which currently resolve nothing — so a signature from another server is
unverifiable and the item is **not recognised**, which is the safe direction.

⚠ **secp256k1 is runtime-dependent and three API layers lie about it.** Most `did:plc` accounts sign
with it; on stock OpenJDK (SunEC) `Signature.initVerify` *succeeds* and only `verify()` fails, on the
request path. `MultibaseKey.secp256k1Available()` probes `verify()` for this reason. **A federated
deployment must state its JVM requirement**, or half the network cannot authenticate the other half
and the symptom appears only under load.

### 2.5 T-1 / R2 — server to server

See §0. Recommendation: mTLS pinned to server DIDs, `07`'s goals preserved, `07`'s construction
deferred. Whatever is chosen, it is the layer under provenance and quorum, so it lands before either
carries real weight.

### 2.6 The game features

W-7 → W-8 → W-9, in that order, each already specified in `design/15`. They are last because each
is cross-server **state mutation**, which is exactly what the quorum and signed provenance exist to
adjudicate (**I15**).

---

## 3. Invariants this plan must not break

- **I14 — game state never lives in a player's PDS or player-controlled infrastructure.** The
  mechanical guard is already in place and must survive every step: a solo character has no DID and
  no `players` row, and core's `V8` makes `character_game_state.character_id` a foreign key to
  `players`, so a hand-made save has nowhere to land. `SchemaIT.engineStateRequiresAPlayer` pins it.
- **I15 — no single arbiter decides cross-server adversarial outcomes.** Every step that adds a
  cross-server *decision* adds it behind the quorum, never behind "the server that asked".
- **I1/I2** — federation adds reach and stakes, never a compute path and never a purchasable
  ceiling. ⚠ The Shadow Market's arbitrage ceiling (`ShadowMarket.ceilingPercent`) is derived from
  the storefront's discount bands **on one server**; a federated order book must re-derive it against
  whichever storefront the player can actually reach, or cross-server arbitrage reopens the faucet.

---

## 4. Open questions for the federation designer

- **G-1** — what evidence flags a server beyond equivocation, who propagates a flag, and whether
  flags are reversible (`03` §4, `08` §G-1).
- **F-1 [new]** — **whose clock orders a cross-server trade?** The Shadow Market, the chain and the
  mempool all take the session's clock. Two servers disagree, and a trade is an ordering. This is the
  first place federation needs a happens-before relation rather than a timestamp.
- **F-2 [new]** — **what happens to a player whose home server disappears?** `09` covers portability
  of state; it does not cover the socially harder case where the server still exists and has been
  flagged. Items are provenance-signed by a server nobody now trusts.
- **F-3 [new]** — **is the ethecoin chain per-server or federation-wide?** `04`'s chain is currently
  a per-character simulation. Two servers with two chains have two money supplies; one shared chain
  is a consensus problem this project has not scoped.

⚠ **F-3 is load-bearing and should be answered before W-8.** Buying from a foreign server moves
money across whatever boundary the answer draws.

---

## 5. Cross-references

`03` §2, §4 · `05` · `07` §6 T-1 · `08` §G-1 · `09` · `10` §1 · `12` · `13` §4 ·
`design/15` CL-8, W-1…W-9 · `design/00` §4 I1, I2, I14, I15
