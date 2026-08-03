# 11. Finding a home server — the client's bootstrap problem

> **Status: ⚠️ [PROPOSAL] — exploration, written 2026-08-02. Nothing here is built.**
>
> `08-discovery-and-sync.md` covers how *servers* find each other and is implemented. This document
> covers the half that does not exist: how a **player's client** finds its first server. They are
> different problems with a different trust model, which is why this is a separate document rather
> than a section of `08`.
>
> Written after the client-side OAuth flow landed (`10` §7 stage 3), because signing in first is what
> makes most of the options below possible at all.

---

## 0. What already exists, and what is actually missing

The server↔server half is built and is not the problem:

- **Signed server descriptors** — `ServerDescriptor`, `ServerDescriptorCodec`, `ServerDescriptorVerifier`.
  Endpoint, transport key, capabilities, a **signed monotonic sequence** and a validity window.
- **Peer exchange** — `PeerDirectoryService` asks known peers for their peer lists, so the network
  heals and grows with no central registry (`08` §3).
- **Liveness and backoff** — `PeerLiveness`, `PeerBackoff`, feeding the validator quorum's uptime
  signal (`05` §2.2).

So a client that knows **one** server can enumerate the rest by asking it, and every descriptor it
receives is signed and verifiable. ⚠ **The entire missing piece is the first one.** Today
`MainMenuView` asks the player to type an address, which means online play is reachable only by
people who were already told where to go.

---

## 1. ⚠ The constraint that eliminates the obvious answer

`03` §1: home servers are **closed by default**. A player who discovers a server they are not
allow-listed on gets refused.

**Therefore a list of servers is not, by itself, useful.** A discovery feature that returns twenty
servers of which the player may join none is worse than no feature: it converts "I don't know where to
play" into "I tried twenty things and all of them rejected me", which reads as the game being broken
rather than as the servers being private.

⚠ **So the real requirement is not "find servers" — it is "find servers that will have me."** Every
option below is judged on whether it can answer that *before* the player attempts to join.

This has a clean consequence: **a descriptor needs a join-policy field** — something like
`open` / `allowlist` / `invite` — advertised the same signed way as the endpoint. It is self-asserted
and non-adversarial (`08` §1's first row), so last-writer-wins on the signed sequence is safe. A
server that lies about it only wastes the player's single join attempt; it cannot gain anything.

---

## 2. The options

### A. Manual address — what exists today

The player types `https://home.example`.

- ✅ Zero infrastructure, zero trust assumptions, and it must keep working regardless — this is how a
  private server among friends is joined, and that is a legitimate primary case.
- ❌ Answers nothing for a player with no contacts.

**Keep unconditionally.** Whatever else lands, this is the floor.

### B. Peer exchange from any known server

Once the client knows one server, ask it for its peer list, then ask those.

- ✅ **Already built server-side.** The client would need a read-only view of `PeerDirectoryService`'s
  data, which is the least new machinery of any option here.
- ✅ Descriptors are signed, so a hostile intermediary cannot forge an endpoint for somebody else's DID.
- ❌ Does not solve the bootstrap; it *expands* a set of one into a set of many.
- ⚠ **G-3 (Sybil resistance in peer exchange) applies directly and is worse for a client.** A hostile
  peer can advertise many fake peers. A *server* checks descriptors and backs off; a *player* sees a
  list and picks one. Bounds limit the blast radius but a client-facing list wants reputation
  weighting that `08` §6 records as undecided.

**Take it, as the second hop.** It is nearly free given what exists.

### C. A project-published seed list

The project hosts a signed list of public servers at a known HTTPS URL, shipped as a fallback in the
client.

- ✅ Solves the bootstrap outright, and it is what almost every federated network actually does.
- ✅ Can carry the join policy (§1) so the client only offers servers that accept newcomers.
- ⚠ **It is a central registry, which `03` §2 deliberately refused** — "a low-trust index, not an
  authority". The refusal is about *authority over game state*, not about a directory, so this does
  not contradict it — but the distinction has to stay real, which means: the list is a **hint only**,
  every entry is verified against its own signed descriptor on connection, and the client must work
  with the list unreachable.
- ❌ Someone has to run and curate it, and delisting becomes a social power.

**Recommended as the bootstrap**, with the constraints above stated in the code rather than assumed.

### D. LAN discovery (mDNS / DNS-SD)

Advertise `_eyeandsickle._tcp.local` on the local network.

- ✅ **Directly serves the pitch.** `CLAUDE.md` sells a spare box and `docker compose up`; the person
  who did that wants their household to find it without typing an IP.
- ✅ No central anything, no new trust — LAN presence is not a trust claim, and the descriptor is
  still verified.
- ❌ Needs an mDNS responder in the server and a listener in the client. The JDK has neither, so this
  is either a dependency (jmDNS) or a hand-written multicast implementation. ⚠ The client's dependency
  austerity is deliberate (`10` §6), so this is a real cost, not a small one.
- ⚠ Fails on most corporate and many café networks, where multicast is blocked.

**Worth doing second**, because it is the only option that makes a self-hosted server findable with no
external service at all.

### E. ⚠ Via the player's AT Proto identity — REJECTED

The tempting one, now that every player has a verified DID: publish or read a server list as a record
in the player's PDS, or discover servers from who they follow.

**Rejected, on two independent grounds:**

1. **Writing needs write scope.** `02` §3 requires this game to *never* request write scope, and
   `OauthClient.SCOPE` is `atproto` — which grants no PDS access at all. Publishing anything to the
   player's repository would mean requesting `transition:generic`, which is App-Password-equivalent
   breadth over their real social account.
2. **Reading a social graph makes server choice a popularity function**, and `12-identity-and-social.md`
   builds informants and dossiers on top of who knows whom. Wiring server discovery to the same graph
   couples two systems that should stay independent.

⚠ **I14 is not the reason** and should not be cited as one — a directory is not game state. The reason
is scope and coupling. Recorded because this idea will be proposed again.

---

## 3. Recommended shape

1. **Manual address** stays, unconditionally (A).
2. **A project seed list** as the bootstrap (C), treated as a hint: unsigned entries are a starting
   point, never an authority, and every server is verified against its own descriptor on connection.
3. **Peer exchange** to widen the set once any server is known (B).
4. **LAN discovery** (D) when the dependency is judged worth it.
5. **A join-policy field on the descriptor** (§1), without which none of 2–4 is worth showing.

⚠ **Sign-in comes first in the UI, and that ordering is deliberate.** The client cannot ask "will this
server have me?" on the player's behalf until it knows who the player is — so `MainMenuView` now
offers *Add an online account* above *Home server*.

---

## 4. Open questions

- **HS-1: who runs the seed list, and what is the delisting policy?** A curated index is a social
  power; it needs a stated rule before it has users, not after.
- **HS-2: does the descriptor's join policy need to be truthful?** It is self-asserted. A server
  advertising `open` and refusing everyone wastes one attempt per player. Probably acceptable; worth
  deciding rather than discovering.
- **HS-3: reputation weighting in client-facing peer exchange** — `08` §6 **G-3** left this open for
  servers, and it matters more for a client, which has no backoff state and one player's attention.
- **HS-4: is an mDNS dependency acceptable in the client?** Weigh against `10` §6's austerity
  argument, which chose to hand-write JOSE rather than add nimbus.

## 5. Cross-references

- Server↔server discovery, descriptors and peer exchange: [`08-discovery-and-sync.md`](08-discovery-and-sync.md)
- Closed-by-default join gate: [`03-server-and-federation.md`](03-server-and-federation.md) §1
- Why scope forbids option E: [`02-identity-and-auth.md`](02-identity-and-auth.md) §3
- The sign-in that makes this possible: [`10-oauth-and-did-resolution.md`](10-oauth-and-did-resolution.md)
