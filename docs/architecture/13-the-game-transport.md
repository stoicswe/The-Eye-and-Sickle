# 13. The game transport — closing CL-8

> **Status: ⚠️ [PROPOSAL] — written 2026-08-02. Nothing here is built.**
>
> Two decisions were taken on explicit direction and this document is written against them:
> **Route A** — the authoritative engine is built server-side rather than borrowing `solo`'s — and
> **pinned self-signed TLS plus the DID channel** for the link. See `../design/15` §3.

---

## 0. The shape of the problem

`GameSession` has **104 methods**. The server today has authoritative implementations for compute,
characters, the ledger and gates; **mining, the chain, breach, the filesystem, tasks and the network
map exist only in `solo`**. So CL-8 is two jobs, and the larger one is not the transport:

1. **Build the engine server-side** — the authoritative rules for everything the client can see.
2. **Carry it** — get state to the client and intent back.

⚠ **Job 2 must not wait for job 1 to finish, and must not be shaped by how far it has got.** The
transport should be able to carry a game with three systems implemented and the same game with
fifteen, without changing shape.

---

## 1. ⚠ 104 methods must NOT become 104 endpoints

The obvious reading of the port is one REST call per method. That would be a mistake, and the client
already says so:

`RemoteGameSession`'s **last-known-good rule** — *"every read here returns the last value the server
sent, even while disconnected, and reports `connected()` false"* — is only implementable if reads are
served from a **local copy of authoritative state**. A read that made a network call could not return
anything while disconnected, and the accessibility argument behind that rule (*"a HUD that empties
when the network hiccups removes information from a player mid-decision"*) would be lost.

So the client is already designed around a snapshot. The transport should admit it.

### The two-endpoint model

| | |
|---|---|
| **State** | the server sends a **snapshot** — one document covering every read |
| **Intent** | the client sends **one intent at a time**; the server answers with an `Outcome` |

Every one of the 104 methods is then either a field of the snapshot or a variant of the intent type.
Adding a system adds a field and a variant, not a controller.

⚠ **This is also the only shape that keeps I14 honest.** With one intent endpoint there is exactly one
place where a client-supplied value crosses into the rules, and it is trivially auditable. With 104
endpoints, "the client is never authoritative" becomes a property somebody has to re-check per
controller.

### Where the types live

`protocol`, which is precisely its charter: *records, enums and sealed types describing what crosses
the wire*. ⚠ **The intent type must be `sealed`** — an open hierarchy means a variant the server does
not know how to refuse, and the safe default for an unknown intent is refusal, which only a closed set
makes checkable.

⚠ **No balance values, thresholds or gate evaluation ride along** — those stay server-side, and
`ArchitectureRulesTest` already refuses them in `protocol`. A snapshot carries *results*, never the
rules that produced them; a client that received the yield curve could predict, and predicting is one
short step from asserting.

---

## 2. Delivery: snapshot, then deltas, then push

Build in that order, and stop at whichever is sufficient.

1. **Poll a full snapshot.** Simplest, correct, and enough for a LAN. ⚠ Its cost is the *size* of the
   snapshot, not the request rate — the ledger and the block list are the two fields that would make
   this untenable, and both are already paged (`ledger(int limit)`, `chainBlocks()`).
2. **Deltas** once the snapshot is measurably too big. Not before: a delta protocol is a second
   representation that can disagree with the first.
3. **Server push** for the things a poll makes feel wrong — a block landing, an intruder, a completed
   task. ⚠ Push is an *optimisation of latency*, never a second source of truth: anything pushed must
   also appear in the next snapshot, or the two can diverge and the divergence is invisible.

⚠ **`UI-7` is relevant and should not be made worse.** `../design/15` records that the client already
repaints on a clock where it should react to events. A polling transport that fans out into per-widget
timers would entrench that; one snapshot arriving and one `changed()` firing is the shape that lets
UI-7 be fixed later rather than harder.

---

## 3. The link

`07-transport-security.md` §3 is unchanged and is the spec: **TLS 1.3 is mandatory and the DID channel
runs inside it.** What LAN adds is that neither half is free, and the decisions taken are:

- **TLS with a self-signed certificate, pinned on first use.** A household has no CA, and §3 already
  argues no CA fits this trust model. ⚠ The pin must be **shown to the player on first connection and
  checked on every later one** — an unpinned self-signed certificate is not TLS, it is an encrypted
  channel to whoever answered.
- **The DID channel inside it** — `protocol.channel`'s `SecureHandshake` / `SecureChannel`.
  ⚠ **T-1 applies**: hand-rolled, reviewed patterns, unreviewed code. Its wording is *"do not let it
  guard a live federation"*; a LAN test is not a federation, so this is the right place to exercise it
  first — and the right place to find out it is wrong.
- ⚠ **The client generates a transport keypair at join**, which the server records against the LAN
  identity. This is worth doing rather than running the handshake server-authenticated only: it makes
  a LAN identity *meaningfully stronger than the bearer token* `12` §2 describes, because possession
  of the UUID alone would no longer be enough to impersonate.

---

## 4. Suggested order

1. **The snapshot and intent types** in `protocol`, covering only what the server can already answer:
   compute, balance, ledger, characters. Small, and it fixes the shape.
2. **The two endpoints**, plaintext over loopback, no channel yet. Proves the pipe.
3. **`RemoteGameSession` reads from the snapshot** and sends intents; everything not yet in the
   snapshot keeps returning last-known-good and `EX_UNAVAILABLE`, exactly as it does now. ⚠ **At this
   point a LAN game is playable to the extent the engine exists**, and every system added afterwards
   is additive.
4. **The link** (§3): pinned TLS, then the channel, then the join-time keypair.
5. **The engine**, system by system, in dependency order. Mining and the chain first — compute is
   already server-side, and mining is the income floor (**I4**, **I5**) that the economy hangs off.
6. **Push** (§2.3), if and when polling feels wrong.

⚠ **Steps 2 and 3 are worth doing before any of step 5.** A transport with one working system proves
the whole shape; an engine with no transport proves nothing a unit test had not already.

## 5. Cross-references

- The port being served: `client/session/GameSession`, and `RemoteGameSession`'s last-known-good rule
- The link: [`07-transport-security.md`](07-transport-security.md) §2–§4, and **T-1**
- LAN specifics, and why the engine's subset is contained there: [`12-lan-mode.md`](12-lan-mode.md)
- **CL-8**, **UI-7**: [`../design/15-open-questions.md`](../design/15-open-questions.md)
