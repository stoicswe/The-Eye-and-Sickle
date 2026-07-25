# 08 — Peer Discovery & State Convergence

**Status:** ⚠️ **[PROPOSAL]** — neither tech chat covered how servers find each other or reconcile shared state. This doc fills that gap and is implemented in `server/.../discovery/`. The *trust split* in §1–§2 is the load-bearing part and should outlive the specific gossip mechanism.
**Depends on:** `03-server-and-federation.md`, `04-item-provenance.md`, `05-validator-quorum.md`, `07-transport-security.md`
**Depended on by:** implementation (`server` discovery slice), the validator quorum's uptime signal (`05` §2.2)

---

## 0. The request, and the one part of it that had to be refused

The feature asked for was: *"servers need discovery mechanisms to find other servers automatically, to sync the latest game state (if state is newer)."*

Half of that is a real, missing, safe feature — **discovery** — and it is built. The other half — **"sync the latest game state if newer"** — is last-writer-wins replication, and applied to game state it breaks the architecture at its foundation. This section exists so nobody re-proposes it:

- `03` §1: adversarial, untrusted servers exist **by design**.
- `03` §2: federation shares **non-adversarial data only** — "a server's private game state stays private."
- Invariant **I15**: no single arbiter decides cross-server adversarial outcomes.

"Newest wins" hands every cheater a win button: a hostile self-host claims a newer timestamp and overwrites honest servers' state — item ownership, balances, duel results. That is exactly the attacker the entire quorum + provenance apparatus (`04`, `05`) exists to defeat. **It is not implemented, and must not be.**

What replaces it delivers what was actually wanted — servers find each other and converge on shared state — by splitting on *trust*.

## 1. Three kinds of data, three convergence rules

| Data | Example | Convergence rule |
|---|---|---|
| **Self-asserted, non-adversarial** | a server's endpoint, transport key, capabilities | last-writer-wins is **safe** — only that server may change its own record, and a signature proves it. Ordered by a **signed monotonic sequence number**, never a wall clock. |
| **Adversarial / shared** | item ownership, duel outcomes | converge by **cryptographic validity, never recency**. A chain that verifies and properly extends a known chain is accepted; a conflicting chain is a **fork** — evidence of misbehaviour — routed to non-recognition, not merged. |
| **Self-verifying evidence** | equivocation proofs, flags | converge on the **evidence itself** (both conflicting signatures exist, so anyone can check), not on any server's assertion. |

The one thing common to all three: **no server is ever trusted because it spoke most recently.** Recency is not authority.

## 2. Why a signed monotonic sequence, not a timestamp

A server's own descriptor legitimately changes — it moves address, rotates its transport key (`07` §4.1). Newer *should* win there. But the ordering key must not be a wall clock:

- Clocks are attacker-controlled: a hostile server sets its clock to the year 3000 and pins its descriptor as "newest" forever.
- Clocks legitimately disagree across self-hosts, producing flapping.

So each descriptor carries a **sequence number the issuing server increments and signs**. A peer accepts a descriptor only if its sequence is greater than the one already held **and** the signature verifies against the issuer's DID key. Monotonicity is enforced at the database boundary (a trigger on `federation_peers` refuses a rollback), so even a bug cannot regress a peer's record.

## 3. Discovery mechanism

- **Seed peers** from configuration (`eyeandsickle.discovery`), then **peer exchange**: known peers are asked for their peer lists, so the network heals and grows with no central registry — the federation directory stays "a low-trust index, not an authority" (`03` §2).
- **Signed server descriptors** (`ServerDescriptor` / `ServerDescriptorCodec` / `ServerDescriptorVerifier`) carry endpoint, transport key, capabilities, sequence, and a validity window, persisted to `federation_peers`.
- **Liveness probing** measures each peer's reachability (`PeerLiveness`), and — this is the payoff — feeds the **uptime** signal the validator quorum weights sampling by (`05` §2.2, via `PeerUptimeSource`). Discovery is where that number finally comes from.
- **Abuse resistance**: every input arrives from an untrusted server, so everything is bounded — peer-list size, descriptor size, gossip fan-out — and an unreachable, flooding, or bad-descriptor peer is backed off exponentially (`PeerBackoff`). Descriptor verification refuses malformed, oversized, wrongly-signed, or stale-sequence input (`DescriptorFault` enumerates every rejection).

## 4. What crosses the wire, and what does not

Discovery moves **only** self-asserted directory data and requests for more of it. It never moves game state. When a server needs to act on another's item (a cross-server transfer), that goes through provenance verification (`04` §7) and, for a duel, the quorum (`05`) — not through discovery. A provenance payload's timestamp is a **replay-protection** input (`04` §2), never a claim to authority; discovery does not and must not use it to decide "newer."

## 5. Transport

Server-to-server discovery calls run over the bounded HTTP client (`RestClientPeerTransport`), inside TLS, and — where a mutually-authenticated session is warranted — inside the DID-authenticated secure channel (`07`). Payloads carry an RFC 9530 `Content-Digest` checksum (server `ContentDigestFilter`) so a corrupted descriptor is caught before verification, not after.

## 6. Open questions

- **G-1: flag propagation.** `03` §4 leaves the flagging mechanism itself `[PROPOSAL]`. Equivocation is automatic and self-verifying; softer fraud, and how flags gossip across the directory, is unspecified. Discovery carries the evidence; who acts on it and how it spreads needs the federation designer.
- **G-2: descriptor freshness vs. churn.** How often to re-probe and re-exchange, and how long a silent peer stays listed before it is dropped, are tuning knobs, not invariants.
- **G-3: Sybil resistance in peer exchange.** A hostile peer can advertise many fake peers. Bounds limit the blast radius; whether reputation should gate who is believed in peer exchange is open.
- **G-4: bootstrapping the first descriptor.** `LocalDescriptorSource` is wired empty until the descriptor builder is connected to the server's signing identity — the server discovers others but advertises nothing about itself until then (a wiring seam, `docs/design/15-open-questions.md`).

## 7. Cross-references

- Why untrusted servers exist at all: `03-server-and-federation.md` §1
- Item legitimacy, and why validity beats recency: `04-item-provenance.md` §7
- The uptime signal this feeds: `05-validator-quorum.md` §2.2, §4
- The channel and checksum this runs over: `07-transport-security.md`
- Open questions: `../design/15-open-questions.md` (G-1…G-4)
